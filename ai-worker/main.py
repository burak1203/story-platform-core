from fastapi import FastAPI
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
import asyncio
import json

app = FastAPI(title="Story AI Worker")

# İleride OpenRouter/Gemini API çağrısını tam olarak buraya yazacağız.
# Şimdilik döngüyü test etmek için 3 saniye bekleyen bir simülasyon kuruyoruz.
async def generate_story_from_llm(prompt: str) -> str:
    await asyncio.sleep(3) 
    return f"Yapay zeka tarafından '{prompt}' temel alınarak üretilmiş destansı bir hikaye..."

async def consume_messages():
    consumer = AIOKafkaConsumer(
        'story-tasks-topic',
        bootstrap_servers='localhost:9092',
        value_deserializer=lambda m: json.loads(m.decode('utf-8'))
    )
    
    # Python'un Java'ya cevap verebilmesi için Producer ekliyoruz
    producer = AIOKafkaProducer(
        bootstrap_servers='localhost:9092',
        value_serializer=lambda v: json.dumps(v).encode('utf-8')
    )
    
    await consumer.start()
    await producer.start()
    print("AI Worker dinliyor ve yanıt vermeye hazır...")
    
    try:
        async for msg in consumer:
            task_data = msg.value
            event = task_data.get('event')
            
            if event == 'GENERATE_STORY':
                story_id = task_data.get('storyId')
                prompt = task_data.get('prompt')
                print(f"[{story_id}] ID'li hikaye üretiliyor. İstek: {prompt}")
                
                # 1. Yapay Zekadan metni al
                generated_text = await generate_story_from_llm(prompt)
                
                # 2. Sonucu JSON olarak Java'ya geri fırlat
                result_payload = {
                    "event": "STORY_COMPLETED",
                    "storyId": story_id,
                    "content": generated_text
                }
                
                await producer.send_and_wait('story-completed-topic', result_payload)
                print(f"[{story_id}] ID'li hikaye başarıyla Java'ya geri gönderildi.")
                
    finally:
        await consumer.stop()
        await producer.stop()

@app.on_event("startup")
async def startup_event():
    asyncio.create_task(consume_messages())