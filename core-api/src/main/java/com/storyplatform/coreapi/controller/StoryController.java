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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.storyplatform.coreapi.entity.User;

import java.util.List;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class StoryController {

    private final StoryService storyService;
    private final SseService sseService;

    @GetMapping("/my-stories")
    public ResponseEntity<List<StoryDetailResponse>> getMyStories(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(storyService.getMyStories(user));
    }

    @PostMapping
    public ResponseEntity<StoryDetailResponse> createStory(
            @RequestBody CreateStoryRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(storyService.createStory(user, request));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Story>> searchStories(
            @AuthenticationPrincipal User user,
            @RequestParam String query) {
        List<Story> results = storyService.searchSimilarStories(user.getId(), query);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/{storyId}/continue")
    public ResponseEntity<Void> continueStory(
            @PathVariable Long storyId,
            @RequestBody StoryContinueRequest request,
            @AuthenticationPrincipal User user) {

        // Doğrudan ID ve action gönderiyoruz, Service katmanı güvenliği kontrol ediyor
        storyService.continueStory(user.getId(), storyId, request.userAction());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{storyId}")
    public ResponseEntity<StoryDetailResponse> getStory(
            @PathVariable Long storyId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(storyService.getStorySafely(storyId, user));
    }

    @GetMapping(value = "/{storyId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStory(@PathVariable Long storyId) {
        // Not: JwtAuthenticationFilter içindeki '?token=' kontrolü sayesinde bu uç artık güvenli.
        return sseService.subscribe(storyId);
    }

    @DeleteMapping("/{storyId}")
    public ResponseEntity<Void> deleteStory(
            @PathVariable Long storyId,
            @AuthenticationPrincipal User user) {
        storyService.deleteStory(storyId, user);
        return ResponseEntity.ok().build();
    }

    // --- FRONTEND İÇİN GÜNCELLENMİŞ JSON SÖZLEŞMELERİ ---
    // userId parametreleri kaldırıldı, güvenlik Spring Security'e devredildi.

    public record CreateStoryRequest(String title, String startingPrompt) {}
    public record StoryContinueRequest(String userAction) {}
}