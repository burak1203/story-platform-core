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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.storyplatform.coreapi.dto.ElementDto;
import com.storyplatform.coreapi.dto.StoryDetailResponse;
import com.storyplatform.coreapi.controller.StoryController.CreateStoryRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final StoryTaskProducer storyTaskProducer;
    private final SseService sseService;
    private final RestTemplate restTemplate;

    @Value("${app.ai-worker.embed-url:http://localhost:8000/api/embed}")
    private String aiWorkerEmbedUrl;

    /**
     * Kafka gönderimi asenkron olduğu için DB yazımıyla atomik değildir;
     * gönderim başarısız olursa hikaye FAILED'e çekilir ve SSE ile bildirilir.
     */
    private void dispatchAiTask(Long storyId, Map<String, Object> aiTask) {
        storyTaskProducer.sendTaskToPython(aiTask).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka gönderimi başarısız, hikaye {} FAILED olarak işaretleniyor", storyId, ex);
                storyRepository.updateStatus(storyId, "FAILED");
                try {
                    sseService.sendStoryUpdate(storyId, Map.of(
                            "type", "AI_ERROR",
                            "message", "Görev kuyruğa gönderilemedi, lütfen tekrar deneyin."
                    ));
                } catch (Exception sseEx) {
                    log.error("SSE hata bildirimi gönderilemedi: {}", sseEx.getMessage());
                }
            }
        });
    }

    // --- YENİ EKLENEN GÜVENLİK VE LİSTELEME METOTLARI ---

    public List<StoryDetailResponse> getMyStories(User user) {
        // Not: Eğer Repository'de findByUserIdOrderByIdDesc yoksa hata verebilir.
        // Varsa sorunsuz çalışır. Yoksa sadece findByUserId kullanabilirsin.
        return storyRepository.findByUserIdOrderByIdDesc(user.getId())
                .stream()
                .map(this::mapToDetailResponse)
                .toList();
    }

    public StoryDetailResponse createStory(User user, CreateStoryRequest request) {
        Story story = Story.builder()
                .title(request.title())
                .content("Hikaye Başlangıcı: " + request.startingPrompt() + "\n\n")
                .status("PENDING")
                .actionCount(0)
                .user(user)
                .build();

        Story savedStory = storyRepository.save(story);

        Map<String, Object> aiTask = Map.of(
                "event", "GENERATE_STORY",
                "storyId", savedStory.getId(),
                "userId", user.getId(),
                "prompt", request.startingPrompt()
        );
        dispatchAiTask(savedStory.getId(), aiTask);

        return mapToDetailResponse(savedStory);
    }

    @Transactional(readOnly = true)
    public StoryDetailResponse getStorySafely(Long storyId, User user) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));

        if (!story.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bu hikayeye erişim yetkiniz yok!");
        }
        return mapToDetailResponse(story);
    }

    // --- MEVCUT METOTLARIN GÜNCELLENMİŞ HALLERİ ---

    @Transactional
    public void continueStory(Long userId, Long storyId, String userAction) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));

        if (!story.getUser().getId().equals(userId)) {
            throw new RuntimeException("Bu hikayeye müdahale etme yetkiniz yok.");
        }

        // Eş zamanlı "devam et" istekleri eski içeriği ezer; üretim sürerken yenisi kabul edilmez
        if ("PENDING".equals(story.getStatus())) {
            throw new IllegalStateException("Bu hikaye için devam eden bir üretim var, lütfen tamamlanmasını bekleyin.");
        }

        List<Map<String, String>> characterList = story.getCharacters().stream()
                .map(c -> Map.of("name", c.getName(), "description", c.getDescription()))
                .collect(Collectors.toList());

        List<Map<String, String>> locationList = story.getLocations().stream()
                .map(l -> Map.of("name", l.getName(), "description", l.getDescription()))
                .collect(Collectors.toList());

        List<Map<String, String>> itemList = story.getItems().stream()
                .map(i -> Map.of("name", i.getName(), "description", i.getDescription()))
                .collect(Collectors.toList());

        Map<String, Object> context = new HashMap<>();
        context.put("previousContent", story.getContent());

        if (story.getCurrentSummary() != null && !story.getCurrentSummary().trim().isEmpty()) {
            context.put("storySoFar", story.getCurrentSummary());
        }

        context.put("characters", characterList);
        context.put("locations", locationList);
        context.put("items", itemList);

        Map<String, Object> aiTask = Map.of(
                "event", "CONTINUE_STORY",
                "storyId", story.getId(),
                "userId", story.getUser().getId(),
                "userAction", userAction,
                "context", context
        );

        story.setStatus("PENDING");
        storyRepository.save(story);

        dispatchAiTask(story.getId(), aiTask);
    }

    // Vektörel Arama (Değişmedi)
    public List<Story> searchSimilarStories(Long userId, String searchText) {
        Map<String, String> requestBody = Map.of("text", searchText);
        ResponseEntity<Map> response = restTemplate.postForEntity(aiWorkerEmbedUrl, requestBody, Map.class);

        @SuppressWarnings("unchecked")
        List<Double> vectorList = (List<Double>) response.getBody().get("embedding");
        String vectorString = vectorList.toString();

        return storyRepository.findSimilarStories(userId, vectorString, 3);
    }

    // Eski getStoryDetails metodu doğrudan güvenli versiyona yönlendirildi
    @Transactional(readOnly = true)
    public StoryDetailResponse getStoryDetails(Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));
        return mapToDetailResponse(story);
    }

    // YARDIMCI METOT: Tüm kod tekrarlarını önleyen Null Korumalı DTO dönüştürücü
    private StoryDetailResponse mapToDetailResponse(Story story) {
        List<ElementDto> characters = (story.getCharacters() == null ? new java.util.ArrayList<com.storyplatform.coreapi.entity.Character>() : story.getCharacters()).stream()
                .map(c -> new ElementDto(c.getId(), c.getName(), c.getDescription()))
                .toList();

        List<ElementDto> locations = (story.getLocations() == null ? new java.util.ArrayList<com.storyplatform.coreapi.entity.Location>() : story.getLocations()).stream()
                .map(l -> new ElementDto(l.getId(), l.getName(), l.getDescription()))
                .toList();

        List<ElementDto> items = (story.getItems() == null ? new java.util.ArrayList<com.storyplatform.coreapi.entity.Item>() : story.getItems()).stream()
                .map(i -> new ElementDto(i.getId(), i.getName(), i.getDescription()))
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

    @Transactional
    public void deleteStory(Long storyId, User user) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));

        // Başkasının hikayesini silemesin diye güvenlik kontrolü
        if (!story.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bu hikayeyi silme yetkiniz yok!");
        }

        storyRepository.delete(story);
    }

    @Transactional
    public StoryDetailResponse editStoryContent(Long storyId, User user, String newContent) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Hikaye bulunamadı"));

        if (!story.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bu hikayeyi düzenleme yetkiniz yok.");
        }

        // Metni güncelle ve kaydet
        story.setContent(newContent);
        Story savedStory = storyRepository.save(story);

        // Python'a vektörü (hafızayı) baştan hesaplaması için emir veriyoruz
        Map<String, Object> aiTask = Map.of(
                "event", "UPDATE_EMBEDDING",
                "storyId", savedStory.getId(),
                "content", newContent
        );
        storyTaskProducer.sendTaskToPython(aiTask);
        // Not: Embedding güncellemesi kritik durum değiştirmediği için FAILED işaretlemesi yapılmıyor

        return mapToDetailResponse(savedStory);
    }
}