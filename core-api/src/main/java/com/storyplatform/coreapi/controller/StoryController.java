package com.storyplatform.coreapi.controller;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List; // Hatanın çözümü için eklenen import

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @PostMapping("/generate")
    public ResponseEntity<Story> generateStory(@RequestBody StoryRequest request) {
        Story story = storyService.createStoryRequest(request.userId(), request.title(), request.prompt());
        return ResponseEntity.ok(story);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Story>> searchStories(
            @RequestParam Long userId,
            @RequestParam String query) {
        List<Story> results = storyService.searchSimilarStories(userId, query);
        return ResponseEntity.ok(results);
    }
}

// Frontend'den beklediğimiz JSON veri sözleşmesi
record StoryRequest(Long userId, String title, String prompt) {}