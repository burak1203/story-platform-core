from fastapi import FastAPI
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from sentence_transformers import SentenceTransformer
from dotenv import load_dotenv
from pydantic import BaseModel
import google.generativeai as genai
import asyncio
import re
import json
import os

# .env yükle
load_dotenv()

app = FastAPI(title="Story AI Worker")

# 1. Gemini İstemcisi Yapılandırması
genai.configure(api_key=os.getenv("GEMINI_API_KEY"))

# 2. ÜCRETSİZ VE YEREL HAFIZA MOTORU (Hugging Face)
print("Hugging Face Embedding modeli yerel hafızaya yükleniyor...")
local_embedding_model = SentenceTransformer('all-MiniLM-L6-v2')

# Temel model versiyonunu globalde tutuyoruz
GEMINI_MODEL_VERSION = 'gemini-3.5-flash'

def parse_llm_json(raw_text: str) -> dict:
    """
    Yapay zeka çıktısını temizler ve ilk geçerli JSON objesini döner.
    Canvas tetiklenmesini engellemek için tırnak ve regex kullanımı optimize edilmiştir.
    """
    # 1. Markdown bloğunu temizce yakala.
    match = re.search(r'```(?:json)?\s*(.*?)\s*```', raw_text, re.DOTALL)
    
    if match:
        clean_text = match.group(1).strip()
        try:
            return json.loads(clean_text)
        except json.JSONDecodeError:
            pass 
            
    # 2. Fallback: Metin içindeki ilk tam JSON bloğunu parantez sayarak ayıkla.
    # AI'nın JSON dışında metin yazması durumunda JSON'u izole eder.
    start_idx = raw_text.find('{')
    if start_idx == -1:
        raise ValueError("Yapay zekanın cevabında JSON iskeleti bulunamadı.")
        
    brace_count = 0
    end_idx = -1
    
    for i in range(start_idx, len(raw_text)):
        if raw_text[i] == '{':
            brace_count += 1
        elif raw_text[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                end_idx = i
                break
                
    if end_idx != -1:
        clean_text = raw_text[start_idx:end_idx+1]
        try:
            return json.loads(clean_text)
        except json.JSONDecodeError as e:
            print(f"JSON parse edilemedi: {e}\nHam Metin: {clean_text}")
            raise e
    else:
        raise ValueError("JSON süslü parantezleri kapatılmamış veya yarım kalmış.")

async def generate_story_and_embedding(prompt: str):
    print("Model hikayeyi kurguluyor ve elementleri ayıklıyor...")
    
    system_instruction = """[KİMLİK VE TON]
Sen usta, yaratıcı ve sürükleyici bir yazarsın. Betimlemelerin güçlü, diyalogların doğaldır. Asla klişe kalıplar kullanma.

[GÖREV]
Kullanıcının verdiği konuya göre akıcı bir hikaye yaz. 
Ardından bu hikayedeki karakterleri, mekanları ve eşyaları çıkar.

[ÇIKTI FORMATI - KESİN KURAL]
Cevabını SADECE VE SADECE aşağıdaki JSON formatında ver. JSON dışında tek bir kelime, selamlama veya açıklama yazma:
{
  "content": "Yazdığın sürükleyici hikayenin tamamı...",
  "characters": [{"name": "Karakter Adı", "description": "Fiziksel özelliği ve rolü"}],
  "locations": [{"name": "Mekan Adı", "description": "Atmosferi ve detayı"}],
  "items": [{"name": "Nesne Adı", "description": "Özelliği"}]
}"""

    model = genai.GenerativeModel(
        model_name=GEMINI_MODEL_VERSION,
        system_instruction=system_instruction
    )
    
    generation_config = genai.types.GenerationConfig(
        temperature=0.8,
        max_output_tokens=8192,
        response_mime_type="application/json"
    )

    response = await model.generate_content_async(
        f"Hikaye Konusu: {prompt}",
        generation_config=generation_config
    )
    
    raw_output = response.text
    
    # Regex ile JSON bloğunu güvenli şekilde ayıklama (Halüsinasyon koruması)
    json_match = re.search(r'\{.*\}', raw_output, re.DOTALL)
    if not json_match:
        raise ValueError("Model geçerli bir JSON döndürmedi.")
        
    parsed_json = parse_llm_json(raw_output)
    story_text = parsed_json.get("content", "")

    print("Vektör işlemi başlatılıyor...")
    loop = asyncio.get_event_loop()
    embedding_vector = await loop.run_in_executor(
        None, lambda: local_embedding_model.encode(story_text).tolist()
    )
    
    return parsed_json, embedding_vector


async def summarize_story_content(story_content: str):
    print("Model arka planda hikayeyi özetliyor...")
    
    system_instruction = (
        "Sen usta bir editörsün. Aşağıda verilen hikayeyi okuyup ana olay örgüsünü, "
        "karakterlerin son durumunu ve motivasyonlarını içeren kısa ama kapsamlı bir özet "
        "(maksimum 2 paragraf) yazacaksın. Cevabına SADECE Türkçe özet metnini yaz, başka hiçbir açıklama yapma."
    )
    
    model = genai.GenerativeModel(
        model_name=GEMINI_MODEL_VERSION,
        system_instruction=system_instruction
    )
    
    generation_config = genai.types.GenerationConfig(
        temperature=0.5,
        max_output_tokens=800
    )
    
    response = await model.generate_content_async(
        f"Şu hikayeyi özetle:\n\n{story_content}",
        generation_config=generation_config
    )
    
    return response.text.strip()


async def continue_story_and_embedding(user_action: str, context: dict):
    print("Model arka planda hikayeyi devam ettiriyor...")
    
    previous_content = context.get("previousContent", "")
    story_so_far = context.get("storySoFar", "")
    
    # Bilinen evreni sadece isimleriyle veriyoruz ki modelin kafası karışmasın
    known_characters = [c["name"] for c in context.get("characters", [])]
    
    summary_block = f"\n[ÖZET]:\n{story_so_far}\n" if story_so_far else ""

    system_instruction = f"""[KİMLİK]
Sen interaktif bir RPG oyun kurucususun.

{summary_block}
[SON BÖLÜM]:
{previous_content}

[BİLİNEN KARAKTERLER LİSTESİ]:
{known_characters}

[GÖREV]
Kullanıcının hamlesine ("{user_action}") göre hikayenin SADECE DEVAMINI yaz.

[JSON ÇIKTI FORMATI]
Sadece geçerli JSON dön:
{{
  "content": "Sadece yeni yazdığın kısım...",
  "new_characters": [{{"name": "Yeni Karakter", "description": "Kim olduğu"}}],
  "updated_characters": [{{"name": "Bilinen Karakterin Adı", "status_change": "Bu bölümde ne yaptı/durumu nasıl değişti (Örn: Orochimaru'nun kolu koptu)"}}],
  "new_locations": [],
  "new_items": []
}}
Not: Bilinen karakterler yeni bir eylem yaparsa 'updated_characters' içine ekle.
"""

    model = genai.GenerativeModel(
        model_name=GEMINI_MODEL_VERSION,
        system_instruction=system_instruction
    )
    
    generation_config = genai.types.GenerationConfig(
        temperature=0.7,
        max_output_tokens=8192,
        response_mime_type="application/json"
    )

    response = await model.generate_content_async(
        f"Hamlem: {user_action}",
        generation_config=generation_config
    )
    
    raw_output = response.text
    
    # Regex zırhı
    json_match = re.search(r'\{.*\}', raw_output, re.DOTALL)
    if json_match:
        parsed_json = parse_llm_json(raw_output)
    else:
        parsed_json = {"content": "Sistem hatası: Model JSON dönemedi."}

    new_story_segment = parsed_json.get("content", "")
    full_story_content = previous_content + "\n\n" + new_story_segment
    parsed_json["content"] = full_story_content

    print("Güncellenmiş hikaye vektöre çevriliyor...")
    loop = asyncio.get_event_loop()
    embedding_vector = await loop.run_in_executor(
        None, lambda: local_embedding_model.encode(full_story_content).tolist()
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
    print("🎧 AI Worker (Gemini Flash & Local Embedding) dinliyor...")
    
    try:
        async for msg in consumer:
            task_data = msg.value
            event = task_data.get('event')

            if event == "UPDATE_EMBEDDING":
                story_id = task_data.get("storyId")
                new_content = task_data.get("content")
                
                # 1. Yeni metnin vektörünü hesapla
                embedding_vector = local_embedding_model.encode(new_content).tolist()
                
                # 2. Java'ya güncel vektörü geri yolla
                response_payload = {
                    "event": "EMBEDDING_UPDATED",
                    "storyId": story_id,
                    "embedding": embedding_vector # Liste formatında [0.1, -0.05, ...]
                }
                await producer.send_and_wait("story-completed-topic", value=response_payload)
                print(f"[{story_id}] Embedding güncellendi ve gönderildi.")
                continue # Döngüdeki diğer if'lere girmemesi için
            
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
                    error_msg = str(e)
                    print(f"HATA: Yapay zeka işlemi başarısız oldu - {error_msg}")
                    error_payload = {
                        "event": "ERROR",
                        "storyId": story_id,
                        "message": f"Yapay zeka motoru yeni hikaye üretemedi (Limit veya Sunucu Hatası). Detay: {error_msg[:150]}..."
                    }
                    await producer.send_and_wait('story-completed-topic', error_payload)
                    
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
                    error_msg = str(e)
                    print(f"HATA: Devam işlemi başarısız oldu - {error_msg}")
                    error_payload = {
                        "event": "ERROR",
                        "storyId": story_id,
                        "message": f"Yapay zeka hikayeye devam edemedi (Limit veya Sunucu Hatası). Detay: {error_msg[:150]}..."
                    }
                    await producer.send_and_wait('story-completed-topic', error_payload)

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