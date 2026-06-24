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

@Component
@RequiredArgsConstructor
public class StoryConsumer {

    private final StoryRepository storyRepository;
    private final CharacterRepository characterRepository;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;

    @KafkaListener(topics = "story-completed-topic", groupId = "core-api-group")
    @SuppressWarnings("unchecked")
    public void consumeStoryResult(Map<String, Object> payload) {
        Long storyId = Long.valueOf(payload.get("storyId").toString());
        String content = (String) payload.get("content");
        String embedding = payload.get("embedding").toString();

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));

        story.setContent(content);
        story.setEmbedding(embedding);
        story.setStatus("COMPLETED");

        // Önce hikayeyi temel verileriyle güncelle
        Story savedStory = storyRepository.save(story);

        // 1. Karakterleri Kaydet
        List<Map<String, String>> charactersData = (List<Map<String, String>>) payload.get("characters");
        if (charactersData != null) {
            charactersData.forEach(c -> {
                Character character = Character.builder()
                        .name(c.get("name"))
                        .description(c.get("description"))
                        .story(savedStory)
                        .build();
                characterRepository.save(character);
            });
        }

        // 2. Mekanları Kaydet
        List<Map<String, String>> locationsData = (List<Map<String, String>>) payload.get("locations");
        if (locationsData != null) {
            locationsData.forEach(l -> {
                Location location = Location.builder()
                        .name(l.get("name"))
                        .description(l.get("description"))
                        .story(savedStory)
                        .build();
                locationRepository.save(location);
            });
        }

        // 3. Nesneleri Kaydet
        List<Map<String, String>> itemsData = (List<Map<String, String>>) payload.get("items");
        if (itemsData != null) {
            itemsData.forEach(i -> {
                Item item = Item.builder()
                        .name(i.get("name"))
                        .description(i.get("description"))
                        .story(savedStory)
                        .build();
                itemRepository.save(item);
            });
        }

        System.out.println("Hikaye " + storyId + " ve tüm ilişkili alt elementleri (Karakter/Mekan/Nesne) başarıyla kaydedildi.");
    }
}