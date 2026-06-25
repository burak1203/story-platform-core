package com.storyplatform.coreapi.controller;

import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.storyplatform.coreapi.dto.StoryDetailResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.storyplatform.coreapi.service.SseService;

import java.util.List;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class StoryController {

    private final StoryService storyService;
    private final SseService sseService;

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

    @PostMapping("/{storyId}/continue")
    public ResponseEntity<Story> continueStory(
            @PathVariable Long storyId,
            @RequestBody StoryContinueRequest request) {
        Story story = storyService.continueStory(request.userId(), storyId, request.userAction());
        return ResponseEntity.ok(story);
    }

    @GetMapping("/{storyId}")
    public ResponseEntity<StoryDetailResponse> getStoryDetails(@PathVariable Long storyId) {
        return ResponseEntity.ok(storyService.getStoryDetails(storyId));
    }

    @GetMapping(value = "/{storyId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStory(@PathVariable Long storyId) {
        return sseService.subscribe(storyId);
    }
}

// Frontend'den beklediğimiz JSON veri sözleşmesi
record StoryRequest(Long userId, String title, String prompt) {}
record StoryContinueRequest(Long userId, String userAction) {}