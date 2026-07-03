package com.storyplatform.coreapi.service;

import com.storyplatform.coreapi.entity.Location;
import com.storyplatform.coreapi.entity.Item;
import com.storyplatform.coreapi.entity.Story;
import com.storyplatform.coreapi.entity.User;
import com.storyplatform.coreapi.repository.CharacterRepository;
import com.storyplatform.coreapi.repository.ItemRepository;
import com.storyplatform.coreapi.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ElementService {

    private final CharacterRepository characterRepository;
    private final LocationRepository locationRepository;
    private final ItemRepository itemRepository;

    private void verifyOwnership(Story story, User user) {
        if (!story.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bu öğeyi değiştirme yetkiniz yok.");
        }
    }

    // --- KARAKTER İŞLEMLERİ ---
    @Transactional
    public void updateCharacter(Long id, String name, String description, User user) {
        com.storyplatform.coreapi.entity.Character character = characterRepository.findById(id).orElseThrow();
        verifyOwnership(character.getStory(), user);
        character.setName(name);
        character.setDescription(description);
        characterRepository.save(character);
    }

    @Transactional
    public void deleteCharacter(Long id, User user) {
        com.storyplatform.coreapi.entity.Character character = characterRepository.findById(id).orElseThrow();
        verifyOwnership(character.getStory(), user);
        characterRepository.delete(character);
    }

    // --- MEKAN İŞLEMLERİ ---
    @Transactional
    public void updateLocation(Long id, String name, String description, User user) {
        Location location = locationRepository.findById(id).orElseThrow();
        verifyOwnership(location.getStory(), user);
        location.setName(name);
        location.setDescription(description);
        locationRepository.save(location);
    }

    @Transactional
    public void deleteLocation(Long id, User user) {
        Location location = locationRepository.findById(id).orElseThrow();
        verifyOwnership(location.getStory(), user);
        locationRepository.delete(location);
    }

    // --- EŞYA İŞLEMLERİ ---
    @Transactional
    public void updateItem(Long id, String name, String description, User user) {
        Item item = itemRepository.findById(id).orElseThrow();
        verifyOwnership(item.getStory(), user);
        item.setName(name);
        item.setDescription(description);
        itemRepository.save(item);
    }

    @Transactional
    public void deleteItem(Long id, User user) {
        Item item = itemRepository.findById(id).orElseThrow();
        verifyOwnership(item.getStory(), user);
        itemRepository.delete(item);
    }
}