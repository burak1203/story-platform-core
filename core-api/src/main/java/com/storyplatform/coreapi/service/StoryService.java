package com.storyplatform.coreapi.service;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.entity.User;
import com.storyplatform.coreapi.repository.StoryRepository;
import com.storyplatform.coreapi.repository.UserRepository;
import com.storyplatform.coreapi.kafka.StoryTaskProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final StoryTaskProducer storyTaskProducer;

    public Story createStoryRequest(Long userId, String title, String prompt) {
        // Kullanıcıyı doğrula
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        // Hikaye iskeletini oluştur (Şimdilik içi boş ve durumu PENDING)
        Story story = Story.builder()
                .title(title)
                .content("")
                .status("PENDING")
                .user(user)
                .build();

        Story savedStory = storyRepository.save(story);

        // Python'a iletilecek görevi hazırla
        Map<String, Object> aiTask = Map.of(
                "event", "GENERATE_STORY",
                "storyId", savedStory.getId(),
                "userId", user.getId(),
                "prompt", prompt
        );

        storyTaskProducer.sendTaskToPython(aiTask);

        return savedStory;
    }
}