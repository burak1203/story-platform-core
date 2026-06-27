package com.storyplatform.coreapi.service;

import com.storyplatform.coreapi.dto.AuthenticationRequest;
import com.storyplatform.coreapi.dto.AuthenticationResponse;
import com.storyplatform.coreapi.dto.RegisterRequest;
import com.storyplatform.coreapi.entity.User;
import com.storyplatform.coreapi.repository.UserRepository;
import com.storyplatform.coreapi.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthenticationResponse register(RegisterRequest request) {
        // Kullanıcıyı oluştur ve şifresini hash'le
        var user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.USER) // Enum kullandık
                .build();

        repository.save(user);

        // Kayıt olan kullanıcıya hemen token üret
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Spring Security şifreyi kendi arka planda DB ile karşılaştırıp doğrulayacak
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // Şifre doğruysa kullanıcıyı DB'den çek
        var user = repository.findByUsername(request.getUsername())
                .orElseThrow();

        // Yeni bir token üretip geri dön
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }
}