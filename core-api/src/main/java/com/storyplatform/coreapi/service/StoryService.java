package com.storyplatform.coreapi.service;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;
import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.entity.User;
import com.storyplatform.coreapi.repository.StoryRepository;
import com.storyplatform.coreapi.repository.UserRepository;
import com.storyplatform.coreapi.kafka.StoryTaskProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final StoryTaskProducer storyTaskProducer;

    // ESKİ METOT: Hikaye oluşturma ve Kafka'ya yollama (Bunu silmiştik, geri getirdik)
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

    // YENİ METOT: Vektörel Arama
    public List<Story> searchSimilarStories(Long userId, String searchText) {
        // 1. Python FastAPI endpoint'ine HTTP isteği at
        RestTemplate restTemplate = new RestTemplate();
        String pythonApiUrl = "http://localhost:8000/api/embed";

        Map<String, String> requestBody = Map.of("text", searchText);
        ResponseEntity<Map> response = restTemplate.postForEntity(pythonApiUrl, requestBody, Map.class);

        // 2. Gelen vektörü (List) al ve string formatına çevir
        @SuppressWarnings("unchecked")
        List<Double> vectorList = (List<Double>) response.getBody().get("embedding");
        String vectorString = vectorList.toString();

        // 3. Veritabanında kosinüs benzerliği ile en yakın 3 hikayeyi getir
        return storyRepository.findSimilarStories(userId, vectorString, 3);
    }
}