package com.storyplatform.coreapi.controller;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}

// Frontend'den beklediğimiz JSON veri sözleşmesi
record StoryRequest(Long userId, String title, String prompt) {}