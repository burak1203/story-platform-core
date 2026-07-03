package com.storyplatform.coreapi.controller;

import com.storyplatform.coreapi.entity.User;
import com.storyplatform.coreapi.service.ElementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/elements")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ElementController {

    private final ElementService elementService;

    // Frontend'den beklediğimiz güncelleme paketi
    public record ElementRequest(String name, String description) {}

    // KARAKTER
    @PutMapping("/characters/{id}")
    public ResponseEntity<Void> updateCharacter(@PathVariable Long id, @RequestBody ElementRequest request, @AuthenticationPrincipal User user) {
        elementService.updateCharacter(id, request.name(), request.description(), user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/characters/{id}")
    public ResponseEntity<Void> deleteCharacter(@PathVariable Long id, @AuthenticationPrincipal User user) {
        elementService.deleteCharacter(id, user);
        return ResponseEntity.ok().build();
    }

    // MEKAN
    @PutMapping("/locations/{id}")
    public ResponseEntity<Void> updateLocation(@PathVariable Long id, @RequestBody ElementRequest request, @AuthenticationPrincipal User user) {
        elementService.updateLocation(id, request.name(), request.description(), user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/locations/{id}")
    public ResponseEntity<Void> deleteLocation(@PathVariable Long id, @AuthenticationPrincipal User user) {
        elementService.deleteLocation(id, user);
        return ResponseEntity.ok().build();
    }

    // EŞYA
    @PutMapping("/items/{id}")
    public ResponseEntity<Void> updateItem(@PathVariable Long id, @RequestBody ElementRequest request, @AuthenticationPrincipal User user) {
        elementService.updateItem(id, request.name(), request.description(), user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id, @AuthenticationPrincipal User user) {
        elementService.deleteItem(id, user);
        return ResponseEntity.ok().build();
    }
}