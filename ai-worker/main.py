from fastapi import FastAPI
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from openai import AsyncOpenAI
from sentence_transformers import SentenceTransformer
from dotenv import load_dotenv
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
    # 1. Gemma 4 ile hikayeyi ücretsiz üret
    print("Gemma 4 (Free) hikayeyi kurguluyor...")
    chat_response = await openrouter_client.chat.completions.create(
        model="google/gemma-4-31b-it:free",
        messages=[
            {"role": "system", "content": "Sen yaratıcı, karanlık ve sürükleyici kurgular yazan usta bir yazarsın. Hikayelerini Türkçe olarak, etkileyici bir dille yaz."},
            {"role": "user", "content": prompt}
        ],
        temperature=0.8,
        max_tokens=1500
    )
    story_text = chat_response.choices[0].message.content

    # 2. TAMAMEN BEDAVA VE YEREL EMBEDDING ÜRETİMİ
    print("Hikaye yerel Hugging Face modeliyle vektöre çevriliyor...")
    # encode() fonksiyonu senkron çalıştığı için asyncio thread'inde koşturuyoruz ki sistem kasılmasın
    loop = asyncio.get_event_loop()
    embedding_vector = await loop.run_in_executor(
        None, 
        lambda: local_embedding_model.encode(story_text).tolist()
    )
    
    return story_text, embedding_vector

async def consume_messages():
    consumer = AIOKafkaConsumer(
        'story-tasks-topic',
        bootstrap_servers='localhost:9092',
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
                    generated_text, embedding_vector = await generate_story_and_embedding(prompt)
                    
                    result_payload = {
                        "event": "STORY_COMPLETED",
                        "storyId": story_id,
                        "content": generated_text,
                        "embedding": embedding_vector
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