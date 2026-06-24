from fastapi import FastAPI
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from openai import AsyncOpenAI
from sentence_transformers import SentenceTransformer
from dotenv import load_dotenv
from pydantic import BaseModel
import asyncio
import json
import os

# .env yükle
load_dotenv()

app = FastAPI(title="Story AI Worker")

# 1. Hikayeyi yazacak olan OpenRouter istemcisi (Ücretsiz Gemma 4 31B)
openrouter_client = AsyncOpenAI(
    base_url="https://openrouter.ai/api/v1",
    api_key=os.getenv("OPENROUTER_API_KEY"),
)

# 2. ÜCRETSİZ VE YEREL HAFIZA MOTORU (Hugging Face)
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

async def summarize_story_content(story_content: str):
    print("Gemma 4 arka planda hikayeyi özetliyor...")
    system_instruction = (
        "Sen usta bir editörsün. Aşağıda verilen hikayeyi okuyup ana olay örgüsünü, "
        "karakterlerin son durumunu ve motivasyonlarını içeren kısa ama kapsamlı bir özet "
        "(maksimum 2 paragraf) yazacaksın. Cevabına SADECE Türkçe özet metnini yaz, başka hiçbir açıklama yapma."
    )
    
    chat_response = await openrouter_client.chat.completions.create(
        model="google/gemma-4-31b-it:free",
        messages=[
            {"role": "system", "content": system_instruction},
            {"role": "user", "content": f"Şu hikayeyi özetle:\n\n{story_content}"}
        ],
        temperature=0.5,
        max_tokens=800
    )
    return chat_response.choices[0].message.content.strip()

async def continue_story_and_embedding(user_action: str, context: dict):
    print("Gemma 4 (Free) geçmiş bağlamı okuyor ve hikayeyi devam ettiriyor...")
    
    previous_content = context.get("previousContent", "")
    story_so_far = context.get("storySoFar", "")
    characters = context.get("characters", [])
    locations = context.get("locations", [])
    items = context.get("items", [])
    summary_block = f"\n[ŞU ANA KADARKİ HİKAYE ÖZETİ]:\n{story_so_far}\n" if story_so_far else ""

    # Modeli halüsinasyondan korumak için mevcut evreni string'e çeviriyoruz
    knowledge_base = json.dumps({
        "Mevcut Karakterler": characters,
        "Mevcut Mekanlar": locations,
        "Mevcut Eşyalar": items
    }, ensure_ascii=False, indent=2)

    system_instruction = f"""Sen usta bir RPG oyun kurucusu ve yazarsın.
Aşağıda hikayenin geçmişi ve şu an evrende var olan elementler verilmiştir.
{summary_block}
[HİKAYE GEÇMİŞİ]:
{previous_content}

[BİLİNEN EVREN (BUNLARI YENİDEN ÜRETMEYECEKSİN)]:
{knowledge_base}

Kullanıcının yaptığı son hamle/eylem şudur: "{user_action}"

GÖREVİN:
1. Kullanıcının hamlesine göre hikayenin SADECE DEVAMINI yaz. Hikayeyi baştan anlatma, kaldığı yerden akıcı bir şekilde devam et.
2. Eğer bu yeni bölümde *daha önce evrende olmayan yeni* bir karakter, mekan veya eşya ortaya çıktıysa, bunları ayıkla.
3. Cevabını KESİNLİKLE başka hiçbir açıklama metni eklemeden, doğrudan şu geçerli JSON formatında dön:
{{
  "content": "Sadece yeni yazdığın hikaye bölümü buraya...",
  "characters": [{{"name": "Yeni Karakter", "description": "Kim olduğu"}}],
  "locations": [{{"name": "Yeni Mekan", "description": "Nasıl bir yer"}}],
  "items": [{{"name": "Yeni Eşya", "description": "Ne işe yaradığı"}}]
}}
Eğer metinde yeni bir şey yoksa, o listeleri boş bırak ([]). Bilinen evrendeki eski elementleri tekrar JSON'a ekleme.
"""

    chat_response = await openrouter_client.chat.completions.create(
        model="google/gemma-4-31b-it:free",
        messages=[
            {"role": "system", "content": system_instruction},
            {"role": "user", "content": f"Hamlem: {user_action}\nHikayeyi devam ettir ve JSON dön."}
        ],
        temperature=0.7,
        max_tokens=2000
    )
    
    raw_output = chat_response.choices[0].message.content
    if raw_output.startswith("```json"):
        raw_output = raw_output.strip("```json").strip("```").strip()
        
    parsed_json = json.loads(raw_output)
    new_story_segment = parsed_json.get("content", "")

    # Eski hikaye ile yeni üretilen bölümü birleştir
    full_story_content = previous_content + "\n\n" + new_story_segment
    parsed_json["content"] = full_story_content # Java'ya birleştirilmiş halini yollayacağız

    print("Güncellenmiş hikaye yerel modelle vektöre çevriliyor...")
    loop = asyncio.get_event_loop()
    embedding_vector = await loop.run_in_executor(
        None, 
        lambda: local_embedding_model.encode(full_story_content).tolist()
    )
    
    return parsed_json, embedding_vector

async def consume_messages():
    consumer = AIOKafkaConsumer(
        'story-tasks-topic',
        bootstrap_servers='localhost:9092',
        group_id='ai-worker-group',
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
                    
            elif event == 'CONTINUE_STORY':
                story_id = task_data.get('storyId')
                user_action = task_data.get('userAction')
                context_data = task_data.get('context')
                print(f"\n[{story_id}] ID'li DEVAM görevi alındı. Kullanıcı Hamlesi: {user_action}")

                try:
                    parsed_json, embedding_vector = await continue_story_and_embedding(user_action, context_data)
                    
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
                    print(f"[{story_id}] Güncellenmiş hikaye (RAG Enjeksiyonlu) Java'ya teslim edildi.")
                except Exception as e:
                    print(f"HATA: Devam işlemi başarısız oldu - {e}")

            elif event == 'SUMMARIZE_STORY':
                story_id = task_data.get('storyId')
                full_content = task_data.get('content')
                print(f"\n[{story_id}] ID'li hikaye için ARKA PLAN ÖZETLEME görevi alındı.")
                
                try:
                    summary_text = await summarize_story_content(full_content)
                    result_payload = {
                        "event": "STORY_SUMMARIZED",
                        "storyId": story_id,
                        "summary": summary_text
                    }
                    await producer.send_and_wait('story-completed-topic', result_payload)
                    print(f"[{story_id}] Özet çıkarıldı ve Java'ya teslim edildi.")
                except Exception as e:
                    print(f"HATA: Özetleme başarısız oldu - {e}")

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
    loop = asyncio.get_event_loop()
    embedding_vector = await loop.run_in_executor(
        None, 
        lambda: local_embedding_model.encode(query.text).tolist()
    )
    return {"embedding": embedding_vector}