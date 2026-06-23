package com.storyplatform.coreapi.service;

import com.storyplatform.coreapi.entity.User;
import com.storyplatform.coreapi.repository.UserRepository;
import com.storyplatform.coreapi.kafka.StoryTaskProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // Fabrika müdürünün eline Kafka telsizini veriyoruz
    private final StoryTaskProducer storyTaskProducer;

    public User createUser(User user) {
        // 1. Kural Kontrolü
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Bu e-posta adresi zaten kullanımda!");
        }

        user.setRole("ROLE_USER");

        // 2. Veritabanına Kaydet
        User savedUser = userRepository.save(user);

        // 3. Asenkron Olarak Python'a Görev Fırlat
        // Şimdilik test amaçlı basit bir JSON sözleşmesi (Map) yolluyoruz
        Map<String, Object> aiTask = Map.of(
                "event", "USER_REGISTERED",
                "userId", savedUser.getId(),
                "email", savedUser.getEmail(),
                "message", "Sisteme yeni biri katıldı, ilk kurgu profili için hazırlan."
        );

        storyTaskProducer.sendTaskToPython(aiTask);

        return savedUser;
    }
}