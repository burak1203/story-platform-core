package com.storyplatform.coreapi.repository;

import com.storyplatform.coreapi.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {
    // İleride kullanıcının geçmiş hikayelerini listelemek için kullanılacak
    List<Story> findByUserId(Long userId);
}