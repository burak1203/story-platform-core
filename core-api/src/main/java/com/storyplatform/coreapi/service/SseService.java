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

    // KRİTİK: Long yerine String kullanıyoruz ki Kafka'dan gelen Integer/Long tip çakışmaları yaşanmasın
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long storyId) {
        String key = String.valueOf(storyId);
        SseEmitter emitter = new SseEmitter(600_000L); // Zaman aşımı 10 Dakika

        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(key, emitter));
        emitter.onTimeout(() -> removeEmitter(key, emitter));
        emitter.onError((e) -> removeEmitter(key, emitter));

        System.out.println("Tünel açıldı, dinleniyor: HİKAYE ID -> " + key);
        return emitter;
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(key);
            }
        }
    }

    public void sendStoryUpdate(Long storyId, Object data) {
        String key = String.valueOf(storyId);
        List<SseEmitter> list = emitters.get(key);

        if (list != null && !list.isEmpty()) {
            for (SseEmitter emitter : list) {
                try {
                    // Veriyi Frontend'e fırlat
                    emitter.send(SseEmitter.event().name("STORY_UPDATE").data(data));
                    System.out.println("BAŞARILI: SSE verisi Frontend'e fırlatıldı! HİKAYE ID -> " + key);
                } catch (IOException e) {
                    emitter.complete();
                    removeEmitter(key, emitter);
                }
            }
        } else {
            System.err.println("DİKKAT: " + key + " ID'li hikaye için açık bir tünel bulunamadı! Frontend tüneli koparmış olabilir.");
        }
    }
}