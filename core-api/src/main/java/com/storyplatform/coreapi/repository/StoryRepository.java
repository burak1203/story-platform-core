package com.storyplatform.coreapi.repository;

import com.storyplatform.coreapi.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {

    List<Story> findByUserId(Long userId);

    @Query(value = "SELECT * FROM stories WHERE user_id = :userId ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<Story> findSimilarStories(@Param("userId") Long userId, @Param("embedding") String embedding, @Param("limit") int limit);

    List<Story> findByUserIdOrderByIdDesc(Long userId);

    // YENİ EKLENEN METOT
    @Modifying
    @Transactional
    @Query(value = "UPDATE stories SET embedding = cast(:embedding as vector) WHERE id = :storyId", nativeQuery = true)
    void updateEmbedding(@Param("storyId") Long storyId, @Param("embedding") String embedding);
}