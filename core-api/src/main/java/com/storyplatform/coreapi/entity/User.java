package com.storyplatform.coreapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role; // Şimdilik "ROLE_USER" veya "ROLE_ADMIN" tutacağız

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Veritabanına ilk kez kaydedilmeden hemen önce çalışma zamanını otomatik atar
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}