package com.storyplatform.coreapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    // Hangi hikayeyi (storyId) hangi istemcilerin (SseEmitter) dinlediğini tutan Thread-Safe harita
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // Frontend'in tünele abone olması için çağrılacak metot
    public SseEmitter subscribe(Long storyId) {
        // Timeout süresi 10 dakika (600_000 ms)
        SseEmitter emitter = new SseEmitter(600_000L);

        emitters.computeIfAbsent(storyId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Bağlantı koparsa veya zaman aşımına uğrarsa hafızadan temizle
        emitter.onCompletion(() -> removeEmitter(storyId, emitter));
        emitter.onTimeout(() -> removeEmitter(storyId, emitter));
        emitter.onError((e) -> removeEmitter(storyId, emitter));

        return emitter;
    }

    private void removeEmitter(Long storyId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(storyId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(storyId);
            }
        }
    }

    // Kafka'dan yanıt geldiğinde JSON'u bu tünelden frontend'e fırlatacak metot
    public void sendStoryUpdate(Long storyId, Object data) {
        List<SseEmitter> list = emitters.get(storyId);
        if (list != null) {
            for (SseEmitter emitter : list) {
                try {
                    // STORY_UPDATE isminde bir event gönderiyoruz
                    emitter.send(SseEmitter.event().name("STORY_UPDATE").data(data));
                } catch (IOException e) {
                    emitter.complete();
                    removeEmitter(storyId, emitter);
                }
            }
        }
    }
}