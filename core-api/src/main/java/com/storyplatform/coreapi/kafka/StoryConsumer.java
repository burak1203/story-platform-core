package com.storyplatform.coreapi.kafka;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import com.pgvector.PGvector;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryConsumer {

    private final StoryRepository storyRepository;

    @KafkaListener(topics = "story-completed-topic", groupId = "story-platform-group")
    @Transactional
    public void consumeCompletedStory(Map<String, Object> payload) {
        log.info("Python'dan tamamlanmış hikaye geldi, ID: {}", payload.get("storyId"));

        String event = (String) payload.get("event");
        if ("STORY_COMPLETED".equals(event)) {
            Long storyId = ((Number) payload.get("storyId")).longValue();
            String content = (String) payload.get("content");

            // 1. Python'dan gelen sayı listesini al
            List<Double> embeddingList = (List<Double>) payload.get("embedding");

            Story story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı: " + storyId));

            story.setContent(content);
            story.setStatus("COMPLETED");

            // 2. Listeyi doğrudan "[0.12, 0.45...]" formatında standart bir metne çevirip kaydet
            if (embeddingList != null) {
                story.setEmbedding(embeddingList.toString());
            }

            storyRepository.save(story);

            log.info("Hikaye {} veritabanına metni ve vektör hafızasıyla (Embedding) başarıyla kaydedildi.", storyId);
        }
    }
}