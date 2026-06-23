from fastapi import FastAPI
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
import asyncio
import json
import random

app = FastAPI(title="Story AI Worker")

async def generate_mock_story_and_embedding(prompt: str):
    # 1. Metin Üretimi Simülasyonu
    await asyncio.sleep(2)
    story_text = f"Yapay zeka tarafından '{prompt}' temel alınarak üretilmiş destansı bir hikaye..."
    
    # 2. Vektör (Embedding) Simülasyonu (OpenAI text-embedding-3-small standardı: 1536 boyut)
    # Gerçek yapay zeka hafızası tam olarak böyle sayılardan oluşur.
    mock_embedding = [random.uniform(-1.0, 1.0) for _ in range(1536)]
    
    return story_text, mock_embedding

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
    print("🎧 AI Worker dinliyor ve vektör üretmeye hazır...")
    
    try:
        async for msg in consumer:
            task_data = msg.value
            event = task_data.get('event')
            
            if event == 'GENERATE_STORY':
                story_id = task_data.get('storyId')
                prompt = task_data.get('prompt')
                print(f"[{story_id}] ID'li hikaye için metin ve hafıza (vektör) üretiliyor...")
                
                # LLM'den metni ve vektörü al
                generated_text, embedding_vector = await generate_mock_story_and_embedding(prompt)
                
                # Sonucu Java'ya fırlat (embedding dizisi ile birlikte)
                result_payload = {
                    "event": "STORY_COMPLETED",
                    "storyId": story_id,
                    "content": generated_text,
                    "embedding": embedding_vector
                }
                
                await producer.send_and_wait('story-completed-topic', result_payload)
                print(f"[{story_id}] ID'li hikaye ve 1536 boyutlu vektör Java'ya başarıyla gönderildi.")
                
    finally:
        await consumer.stop()
        await producer.stop()

@app.on_event("startup")
async def startup_event():
    asyncio.create_task(consume_messages())