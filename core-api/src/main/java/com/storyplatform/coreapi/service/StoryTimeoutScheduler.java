package com.storyplatform.coreapi.service;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PENDING durumunda takılı kalan hikayeleri belirli bir süre sonra FAILED'e çeker.
 * Kafka mesajı kaybolursa veya AI worker çökerse hikayeler sonsuza kadar PENDING kalmaz.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoryTimeoutScheduler {

    private final StoryRepository storyRepository;
    private final SseService sseService;

    @Value("${app.story.pending-timeout-minutes:5}")
    private long pendingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${app.story.pending-check-interval-ms:60000}")
    public void failStuckPendingStories() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<Story> stuckStories = storyRepository.findByStatusAndUpdatedAtBefore("PENDING", threshold);

        for (Story story : stuckStories) {
            log.warn("Hikaye {} {} dakikadır PENDING durumunda, FAILED'e çekiliyor.", story.getId(), pendingTimeoutMinutes);
            storyRepository.updateStatus(story.getId(), "FAILED");
            try {
                sseService.sendStoryUpdate(story.getId(), Map.of(
                        "type", "AI_ERROR",
                        "message", "Üretim zaman aşımına uğradı, lütfen tekrar deneyin."
                ));
            } catch (Exception e) {
                log.error("Zaman aşımı SSE bildirimi gönderilemedi: {}", e.getMessage());
            }
        }
    }
}
