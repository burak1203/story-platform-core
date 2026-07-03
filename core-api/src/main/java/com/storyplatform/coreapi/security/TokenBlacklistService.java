package com.storyplatform.coreapi.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Logout edilen JWT'leri Redis'te süresi dolana kadar kara listede tutar.
 * Token'ın kendisi yerine SHA-256 özeti saklanır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String token, long remainingMillis) {
        if (remainingMillis <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + hash(token), "1", Duration.ofMillis(remainingMillis));
        } catch (Exception e) {
            log.error("Token kara listeye eklenemedi: {}", e.getMessage());
            throw new IllegalStateException("Oturum kapatma işlemi tamamlanamadı.", e);
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + hash(token)));
        } catch (Exception e) {
            // Redis erişilemezse istekleri bloke etmemek için fail-open davranıyoruz
            log.error("Kara liste kontrolü yapılamadı: {}", e.getMessage());
            return false;
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
