package com.storyplatform.coreapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicInsert;
import com.pgvector.PGvector;
import java.util.List;

@Entity
@Table(name = "stories")
@DynamicInsert
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

    // JDBC kuryesini kandırmak için veriyi Java'da String olarak tutuyoruz.
    // @ColumnTransformer kalkanı, veritabanına yazılırken "?::vector" komutuyla metni gerçek vektöre çevirecek.
    @Column(columnDefinition = "vector(384)")
    @ColumnTransformer(write = "?::vector")
    private String embedding;

    // Kritik Nokta: Hikayeyi Kullanıcıya Bağlayan Halat
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Eş zamanlı güncellemelerde son yazanın sessizce ezmesini engelleyen optimistic lock
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Character> characters;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Location> locations;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Item> items;

    @Column(columnDefinition = "TEXT")
    private String currentSummary;
    // Mevcut veritabanı kayıtlarında hata vermemesi için int yerine Integer (nullable) kullanıyoruz
    private Integer actionCount;
}