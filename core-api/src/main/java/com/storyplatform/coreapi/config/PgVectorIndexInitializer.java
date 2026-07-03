package com.storyplatform.coreapi.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate ddl-auto pgvector index'i oluşturamadığı için
 * benzerlik aramalarının lineer taramaya dönmemesi adına
 * HNSW index'i uygulama açılışında idempotent olarak kurulur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PgVectorIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_stories_embedding_hnsw " +
                    "ON stories USING hnsw (embedding vector_cosine_ops)");
            log.info("pgvector HNSW index hazır.");
        } catch (Exception e) {
            log.error("pgvector index oluşturulamadı: {}", e.getMessage());
        }
    }
}
