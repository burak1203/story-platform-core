package com.storyplatform.coreapi.service;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.entity.User;
import com.storyplatform.coreapi.repository.StoryRepository;
import com.storyplatform.coreapi.repository.UserRepository;
import com.storyplatform.coreapi.kafka.StoryTaskProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.storyplatform.coreapi.dto.ElementDto;
import com.storyplatform.coreapi.dto.StoryDetailResponse;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final StoryTaskProducer storyTaskProducer;

    // 1. Sıfırdan Hikaye Oluşturma Metodu
    public Story createStoryRequest(Long userId, String title, String prompt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        Story story = Story.builder()
                .title(title)
                .content("")
                .status("PENDING")
                .user(user)
                .build();

        Story savedStory = storyRepository.save(story);

        Map<String, Object> aiTask = Map.of(
                "event", "GENERATE_STORY",
                "storyId", savedStory.getId(),
                "userId", user.getId(),
                "prompt", prompt
        );

        storyTaskProducer.sendTaskToPython(aiTask);
        return savedStory;
    }

    // 2. Vektörel Arama Metodu
    public List<Story> searchSimilarStories(Long userId, String searchText) {
        RestTemplate restTemplate = new RestTemplate();
        String pythonApiUrl = "http://localhost:8000/api/embed";

        Map<String, String> requestBody = Map.of("text", searchText);
        ResponseEntity<Map> response = restTemplate.postForEntity(pythonApiUrl, requestBody, Map.class);

        @SuppressWarnings("unchecked")
        List<Double> vectorList = (List<Double>) response.getBody().get("embedding");
        String vectorString = vectorList.toString();

        return storyRepository.findSimilarStories(userId, vectorString, 3);
    }

    // 3. YENİ METOT: Hafıza Enjeksiyonlu (RAG) Hikayeye Devam Etme
    @Transactional(readOnly = true)
    public Story continueStory(Long userId, Long storyId, String userAction) {
        // 1. Hikaye ve Kullanıcı Doğrulaması
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));

        if (!story.getUser().getId().equals(userId)) {
            throw new RuntimeException("Bu hikayeye müdahale etme yetkiniz yok.");
        }

        // 2. Alt Elementleri Çek ve Formatla (Sadece isim ve açıklama kısımlarını alıyoruz)
        List<Map<String, String>> characterList = story.getCharacters().stream()
                .map(c -> Map.of("name", c.getName(), "description", c.getDescription()))
                .collect(Collectors.toList());

        List<Map<String, String>> locationList = story.getLocations().stream()
                .map(l -> Map.of("name", l.getName(), "description", l.getDescription()))
                .collect(Collectors.toList());

        List<Map<String, String>> itemList = story.getItems().stream()
                .map(i -> Map.of("name", i.getName(), "description", i.getDescription()))
                .collect(Collectors.toList());

        // 3. RAG Bağlamını (Context) Oluştur
        Map<String, Object> context = new HashMap<>();
        context.put("previousContent", story.getContent());

        if (story.getCurrentSummary() != null && !story.getCurrentSummary().trim().isEmpty()) {
            context.put("storySoFar", story.getCurrentSummary());
        }

        context.put("characters", characterList);
        context.put("locations", locationList);
        context.put("items", itemList);

        // 4. Kafka'ya Gönderilecek "Zenginleştirilmiş" Ana Yükü Hazırla
        Map<String, Object> aiTask = Map.of(
                "event", "CONTINUE_STORY",
                "storyId", story.getId(),
                "userId", story.getUser().getId(),
                "userAction", userAction,
                "context", context
        );

        // 5. Python'a Fırlat
        storyTaskProducer.sendTaskToPython(aiTask);

        // İsteğin alındığını dönüyoruz (Asıl güncelleme asenkron çalışacak)
        return story;
    }

    @Transactional(readOnly = true)
    public StoryDetailResponse getStoryDetails(Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));

        List<ElementDto> characters = story.getCharacters().stream()
                .map(c -> new ElementDto(c.getName(), c.getDescription()))
                .toList();

        List<ElementDto> locations = story.getLocations().stream()
                .map(l -> new ElementDto(l.getName(), l.getDescription()))
                .toList();

        List<ElementDto> items = story.getItems().stream()
                .map(i -> new ElementDto(i.getName(), i.getDescription()))
                .toList();

        return new StoryDetailResponse(
                story.getId(),
                story.getTitle(),
                story.getContent(),
                story.getStatus(),
                story.getCurrentSummary(),
                story.getActionCount(),
                characters,
                locations,
                items
        );
    }
}