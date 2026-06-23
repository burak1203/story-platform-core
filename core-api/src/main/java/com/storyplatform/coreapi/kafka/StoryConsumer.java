package com.storyplatform.coreapi.kafka;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryConsumer {

    private final StoryRepository storyRepository;

    // Python'un mesaj fırlattığı odayı (topic) dinliyoruz
    @KafkaListener(topics = "story-completed-topic", groupId = "story-platform-group")
    @Transactional
    public void consumeCompletedStory(Map<String, Object> payload) {
        log.info("Python'dan tamamlanmış hikaye geldi: {}", payload);

        String event = (String) payload.get("event");
        if ("STORY_COMPLETED".equals(event)) {
            // Python'dan gelen ID'yi güvenli şekilde alıyoruz
            Long storyId = ((Number) payload.get("storyId")).longValue();
            String content = (String) payload.get("content");

            // Veritabanından o anki PENDING durumundaki hikayeyi buluyoruz
            Story story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı: " + storyId));

            // İçeriği doldurup statüyü COMPLETED yapıyoruz
            story.setContent(content);
            story.setStatus("COMPLETED");
            storyRepository.save(story);

            log.info("Hikaye {} veritabanına COMPLETED olarak başarıyla kaydedildi.", storyId);
        }
    }
}