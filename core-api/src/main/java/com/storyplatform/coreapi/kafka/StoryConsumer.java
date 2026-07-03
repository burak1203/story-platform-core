package com.storyplatform.coreapi.kafka;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.entity.Character;
import com.storyplatform.coreapi.entity.Location;
import com.storyplatform.coreapi.entity.Item;
import com.storyplatform.coreapi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import com.storyplatform.coreapi.service.SseService;
import com.storyplatform.coreapi.service.StoryService;
import com.storyplatform.coreapi.dto.StoryDetailResponse;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoryConsumer {

    private final StoryRepository storyRepository;
    private final CharacterRepository characterRepository;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;
    private final StoryTaskProducer storyTaskProducer; // Python'a özetleme işi atmak için eklendi
    private final SseService sseService;
    private final StoryService storyService;

    @KafkaListener(topics = "story-completed-topic", groupId = "core-api-group")
    @SuppressWarnings("unchecked")
    public void consumeStoryResult(Map<String, Object> payload) {
        String event = (String) payload.get("event");
        Long storyId = Long.valueOf(payload.get("storyId").toString());
        // Eğer pythondan hata geldiyse doğrudan frontende fırlat ve çık
        if ("ERROR".equals(event)) {
            String errorMessage = (String) payload.get("message");
            log.error("Python'dan Hata Raporu Geldi: {}", errorMessage);

            // Hikaye PENDING'de takılı kalmasın diye FAILED'e çekiyoruz
            storyRepository.updateStatus(storyId, "FAILED");

            try {
                // Frontend'in anlayacağı formatta özel bir hata nesnesi yolluyoruz
                Map<String, String> errorData = Map.of(
                        "type", "AI_ERROR",
                        "message", errorMessage
                );
                sseService.sendStoryUpdate(storyId, errorData);
            } catch (Exception e) {
                log.error("Hata mesajı SSE ile gönderilemedi: {}", e.getMessage());
            }
            return; // Metodu burada kes, veritabanı işlemlerine girmesin
        }

        // 1. VEKTÖR GÜNCELLEMESİ (Story objesini DB'den çekmeden doğrudan güncelliyoruz)
        if ("EMBEDDING_UPDATED".equals(event)) {
            List<Double> vectorList = (List<Double>) payload.get("embedding");
            String vectorString = vectorList.toString();
            storyRepository.updateEmbedding(storyId, vectorString);
            log.info("Hikaye {} için vektör hafızası başarıyla güncellendi.", storyId);
            return;
        }

        // 2. DİĞER İŞLEMLER İÇİN STORY OBJESİNİ ÇEK (Sadece burada 1 kez tanımlanır)
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));

        // Eğer gelen mesaj bir özetleme sonucuysa sadece özeti güncelleyip çıkıyoruz
        if ("STORY_SUMMARIZED".equals(event)) {
            String summary = (String) payload.get("summary");
            story.setCurrentSummary(summary);
            storyRepository.save(story);
            log.info("Hikaye {} için Dinamik Özet arka planda güncellendi.", storyId);
            try {
                StoryDetailResponse updatedDetails = storyService.getStoryDetails(storyId);
                sseService.sendStoryUpdate(storyId, updatedDetails);
                log.info("Özet güncellemesi SSE ile Frontend'e fırlatıldı.");
            } catch (Exception e) {
                log.error("Özet SSE ile gönderilemedi: {}", e.getMessage());
            }
            return;
        }


        // --- NORMAL HİKAYE ÜRETİM VEYA DEVAM İŞLEMİ ---
        String content = (String) payload.get("content");
        String embedding = payload.get("embedding").toString();

        story.setContent(content);
        story.setEmbedding(embedding);
        story.setStatus("COMPLETED");

        // Hamle sayısını artır (Null kontrolü ile)
        int currentCount = story.getActionCount() == null ? 0 : story.getActionCount();
        story.setActionCount(currentCount + 1);

        Story savedStory = storyRepository.save(story);

        // Karakterleri, Mekanları ve Nesneleri Kaydet
        List<Map<String, String>> charactersData = (List<Map<String, String>>) payload.get("characters");
        if (charactersData != null) {
            charactersData.forEach(c -> characterRepository.save(Character.builder()
                    .name(c.get("name")).description(c.get("description")).story(savedStory).build()));
        }

        List<Map<String, String>> locationsData = (List<Map<String, String>>) payload.get("locations");
        if (locationsData != null) {
            locationsData.forEach(l -> locationRepository.save(Location.builder()
                    .name(l.get("name")).description(l.get("description")).story(savedStory).build()));
        }

        List<Map<String, String>> itemsData = (List<Map<String, String>>) payload.get("items");
        if (itemsData != null) {
            itemsData.forEach(i -> itemRepository.save(Item.builder()
                    .name(i.get("name")).description(i.get("description")).story(savedStory).build()));
        }

        log.info("Hikaye {} güncellendi. (Hamle Sayısı: {})", storyId, savedStory.getActionCount());

        try {
            StoryDetailResponse updatedDetails = storyService.getStoryDetails(storyId);
            sseService.sendStoryUpdate(storyId, updatedDetails);
            log.info("SSE ile Frontend'e canlı güncelleme gönderildi.");
        } catch (Exception e) {
            log.error("SSE gönderimi başarısız: {}", e.getMessage());
        }

        // ÖZETLEME TETİKLEYİCİSİ: Her 3 hamlede bir asenkron özet görevi yolla
        if (savedStory.getActionCount() % 3 == 0) {
            log.info("Hikaye {} uzadı. Arka planda özetleme motoru tetikleniyor...", storyId);
            Map<String, Object> summarizeTask = Map.of(
                    "event", "SUMMARIZE_STORY",
                    "storyId", savedStory.getId(),
                    "content", savedStory.getContent()
            );
            storyTaskProducer.sendTaskToPython(summarizeTask);
        }
    }
}