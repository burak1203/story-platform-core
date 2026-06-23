package com.storyplatform.coreapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Hikayenin başlığı
    @Column(nullable = false)
    private String title;

    // Hikayenin içeriği (Uzun metin olacağı için columnDefinition = "TEXT" ekliyoruz)
    @Column(columnDefinition = "TEXT")
    private String content;

    // Yapay zeka henüz yazıyor mu, bitti mi, yoksa hata mı verdi? (PENDING, GENERATING, COMPLETED, FAILED)
    @Column(nullable = false)
    private String status;

    // Kritik Nokta: Hikayeyi Kullanıcıya Bağlayan Halat
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}