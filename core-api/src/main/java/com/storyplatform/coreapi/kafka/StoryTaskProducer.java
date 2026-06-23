package com.storyplatform.coreapi.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j // Konsola log yazdırmak için kullanıyoruz
public class StoryTaskProducer {

    // Spring Boot'un Kafka'ya mesaj göndermek için bize sunduğu ana araç
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Mesajların toplanacağı kanalın (Topic) adı
    private static final String TOPIC = "story-tasks-topic";

    // İleride Controller'dan veya Service'den bu metodu çağırıp Python'a JSON fırlatacağız
    public void sendTaskToPython(Object taskPayload) {
        log.info("Kafka kuyruğuna AI görevi fırlatılıyor: {}", taskPayload);
        kafkaTemplate.send(TOPIC, taskPayload);
    }
}