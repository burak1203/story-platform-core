from fastapi import FastAPI
from aiokafka import AIOKafkaConsumer
import asyncio
import json

app = FastAPI(title="Story AI Worker")

# Kafka'yı arka planda sürekli, yorulmadan dinleyecek asenkron görev
async def consume_messages():
    consumer = AIOKafkaConsumer(
        'story-tasks-topic', # Java'nın mesaj attığı kuyruk
        bootstrap_servers='localhost:9092',
        value_deserializer=lambda m: json.loads(m.decode('utf-8')) # Gelen byte'ları JSON'a çevir
    )
    
    await consumer.start()
    print("AI Worker, Kafka kuyruğunu dinlemeye başladı...")
    
    try:
        # Kuyruğa mesaj düştükçe bu döngü çalışacak
        async for msg in consumer:
            task_data = msg.value
            print(f"KAFKA'DAN YENİ GÖREV GELDİ: {task_data}")
            
            # İleride burada OpenRouter/Gemini API'ye bağlanıp hikaye üreteceğiz
            # şimdilik sadece mesajı aldığımızı kanıtlıyoruz.
            
    finally:
        await consumer.stop()

# FastAPI ayağa kalktığında dinleme görevini başlat
@app.on_event("startup")
async def startup_event():
    asyncio.create_task(consume_messages())

@app.get("/")
def read_root():
    return {"status": "AI Worker is running and listening to Kafka..."}