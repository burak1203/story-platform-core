package com.storyplatform.coreapi.kafka;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.entity.Character;
import com.storyplatform.coreapi.entity.Location;
import com.storyplatform.coreapi.entity.Item;
import com.storyplatform.coreapi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import com.storyplatform.coreapi.service.SseService;
import com.storyplatform.coreapi.service.StoryService;
import com.storyplatform.coreapi.dto.StoryDetailResponse;

@Component
@RequiredArgsConstructor
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

        // 1. VEKTÖR GÜNCELLEMESİ (Story objesini DB'den çekmeden doğrudan güncelliyoruz)
        if ("EMBEDDING_UPDATED".equals(event)) {
            List<Double> vectorList = (List<Double>) payload.get("embedding");
            String vectorString = vectorList.toString();
            storyRepository.updateEmbedding(storyId, vectorString);
            System.out.println("Hikaye " + storyId + " için vektör hafızası başarıyla güncellendi.");
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
            System.out.println("Hikaye " + storyId + " için Dinamik Özet arka planda güncellendi.");
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

        System.out.println("Hikaye " + storyId + " güncellendi. (Hamle Sayısı: " + savedStory.getActionCount() + ")");

        try {
            StoryDetailResponse updatedDetails = storyService.getStoryDetails(storyId);
            sseService.sendStoryUpdate(storyId, updatedDetails);
            System.out.println("SSE ile Frontend'e canlı güncelleme gönderildi.");
        } catch (Exception e) {
            System.err.println("SSE gönderimi başarısız: " + e.getMessage());
        }

        // ÖZETLEME TETİKLEYİCİSİ: Her 3 hamlede bir asenkron özet görevi yolla
        if (savedStory.getActionCount() % 3 == 0) {
            System.out.println("Hikaye " + storyId + " uzadı. Arka planda özetleme motoru tetikleniyor...");
            Map<String, Object> summarizeTask = Map.of(
                    "event", "SUMMARIZE_STORY",
                    "storyId", savedStory.getId(),
                    "content", savedStory.getContent()
            );
            storyTaskProducer.sendTaskToPython(summarizeTask);
        }
    }
}