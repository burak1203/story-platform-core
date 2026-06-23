package com.storyplatform.coreapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.pgvector.PGvector;

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

    // Yapay zekanın metni anlamsal sayılara (koordinatlara) çevirdiği halini burada tutacağız.
    // 1536 rakamı, sektör standardı olan OpenAI (text-embedding-3-small) veya benzer boyutlu dil modellerinin vektör formatıdır.
    @Column(columnDefinition = "vector(1536)")
    private PGvector embedding;

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