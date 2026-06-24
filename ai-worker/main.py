from fastapi import FastAPI
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from openai import AsyncOpenAI
from sentence_transformers import SentenceTransformer
from dotenv import load_dotenv
from pydantic import BaseModel
import asyncio
import json
import os

# .env yükle (Artık OpenAI API Key'e ihtiyacımız yok, sadece OpenRouter kalabilir)
load_dotenv()

app = FastAPI(title="Story AI Worker")

# 1. Hikayeyi yazacak olan OpenRouter istemcisi (Ücretsiz Gemma 4 31B)
openrouter_client = AsyncOpenAI(
    base_url="https://openrouter.ai/api/v1",
    api_key=os.getenv("OPENROUTER_API_KEY"),
)

# 2. ÜCRETSİZ VE YEREL HAFIZA MOTORU (Hugging Face)
# Kod ilk çalıştığında bu modeli internetten bir kez indirecek, sonra hep lokal çalışacak.
print("Hugging Face Embedding modeli yerel hafızaya yükleniyor...")
local_embedding_model = SentenceTransformer('all-MiniLM-L6-v2')

async def generate_story_and_embedding(prompt: str):
    print("Gemma 4 (Free) hikayeyi kurguluyor ve elementleri ayıklıyor...")
    
    system_instruction = (
        "Sen usta bir yazarsın. Gelen talebe göre Türkçe bir hikaye yazmalı ve hikayedeki "
        "en önemli karakterleri, mekanları ve nesneleri ayıklamalısın. "
        "Cevabını KESİNLİKLE başka hiçbir açıklama metni eklemeden, doğrudan şu geçerli JSON formatında dönmelisin:\n"
        "{\n"
        '  "content": "Yazdığın hikaye metni buraya...",\n'
        '  "characters": [{"name": "Karakter Adı", "description": "Hikayedeki rolü ve kısa açıklaması"}],\n'
        '  "locations": [{"name": "Mekan Adı", "description": "Kısa açıklama"}],\n'
        '  "items": [{"name": "Nesne Adı", "description": "Önemli nesne açıklaması"}]\n'
        "}"
    )

    chat_response = await openrouter_client.chat.completions.create(
        model="google/gemma-4-31b-it:free",
        messages=[
            {"role": "system", "content": system_instruction},
            {"role": "user", "content": prompt}
        ],
        temperature=0.7,
        max_tokens=2000
    )
    
    raw_output = chat_response.choices[0].message.content
    if raw_output.startswith("```json"):
        raw_output = raw_output.strip("```json").strip("```").strip()
        
    parsed_json = json.loads(raw_output)
    story_text = parsed_json.get("content", "")

    print("Hikaye yerel Hugging Face modeliyle vektöre çevriliyor...")
    loop = asyncio.get_event_loop()
    embedding_vector = await loop.run_in_executor(
        None, 
        lambda: local_embedding_model.encode(story_text).tolist()
    )
    
    return parsed_json, embedding_vector

async def consume_messages():
    consumer = AIOKafkaConsumer(
        'story-tasks-topic',
        bootstrap_servers='localhost:9092',
        group_id='ai-worker-group', # Consumer Group ID ekledik, yoksa kafka --workers 4 ile çalışırken her worker tek tek aynı mesajı alıyor ve işliyor
        value_deserializer=lambda m: json.loads(m.decode('utf-8'))
    )
    
    producer = AIOKafkaProducer(
        bootstrap_servers='localhost:9092',
        value_serializer=lambda v: json.dumps(v).encode('utf-8')
    )
    
    await consumer.start()
    await producer.start()
    print("🎧 AI Worker (Gemma 4 & Local Embedding) dinliyor...")
    
    try:
        async for msg in consumer:
            task_data = msg.value
            event = task_data.get('event')
            
            if event == 'GENERATE_STORY':
                story_id = task_data.get('storyId')
                prompt = task_data.get('prompt')
                print(f"\n[{story_id}] ID'li görev alındı. Prompt: {prompt}")
                
                try:
                    parsed_json, embedding_vector = await generate_story_and_embedding(prompt)
                    
                    result_payload = {
                        "event": "STORY_COMPLETED",
                        "storyId": story_id,
                        "content": parsed_json.get("content"),
                        "embedding": embedding_vector,
                        "characters": parsed_json.get("characters", []),
                        "locations": parsed_json.get("locations", []),
                        "items": parsed_json.get("items", [])
                    }
                    
                    await producer.send_and_wait('story-completed-topic', result_payload)
                    print(f"[{story_id}] Hikaye ve yerel vektör Java'ya başarıyla teslim edildi.")
                except Exception as e:
                    print(f"HATA: Yapay zeka işlemi başarısız oldu - {e}")
                
    finally:
        await consumer.stop()
        await producer.stop()

@app.on_event("startup")
async def startup_event():
    asyncio.create_task(consume_messages())

class SearchQuery(BaseModel):
    text: str

@app.post("/api/embed")
async def get_embedding_for_search(query: SearchQuery):
    # Gelen arama metnini anında yerel modelle vektöre çevirir (Senkron/HTTP)
    loop = asyncio.get_event_loop()
    embedding_vector = await loop.run_in_executor(
        None, 
        lambda: local_embedding_model.encode(query.text).tolist()
    )
    return {"embedding": embedding_vector}