package com.storyplatform.coreapi.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // Spring Boot başlarken bu metodu çalıştırıp Kafka'da odayı (topic) açacak
    @Bean
    public NewTopic storyTasksTopic() {
        return TopicBuilder.name("story-tasks-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Spring Boot başlarken bu metodu da çalıştırıp geri dönüş odasını açacak
    @Bean
    public NewTopic storyCompletedTopic() {
        return TopicBuilder.name("story-completed-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }
}