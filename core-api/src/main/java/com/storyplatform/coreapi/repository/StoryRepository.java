package com.storyplatform.coreapi.repository;

import com.storyplatform.coreapi.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {

    // İleride kullanıcının geçmiş hikayelerini listelemek için kullanılacak
    List<Story> findByUserId(Long userId);

    // pgvector ile Kosinüs Benzerliği (Cosine Similarity) Araması
    @Query(value = "SELECT * FROM stories WHERE user_id = :userId ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<Story> findSimilarStories(@Param("userId") Long userId, @Param("embedding") String embedding, @Param("limit") int limit);
}