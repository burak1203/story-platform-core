package com.storyplatform.coreapi.repository;

import com.storyplatform.coreapi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Google ile giriş yaparken veya normal kayıtta email kontrolü için kullanacağız
    Optional<User> findByEmail(String email);
}