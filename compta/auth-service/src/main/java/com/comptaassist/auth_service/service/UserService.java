package com.comptaassist.auth_service.service;


import com.comptaassist.auth_service.dto.UpdateProfileRequest;
import com.comptaassist.auth_service.dto.UserResponse;
import com.comptaassist.auth_service.entity.User;
import com.comptaassist.auth_service.exception.AuthException;
import com.comptaassist.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    public UserResponse getById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AuthException("Utilisateur introuvable"));
        return toResponse(user);
    }

    public UserResponse getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Utilisateur introuvable"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AuthException("Utilisateur introuvable"));

        if (request.getNom() != null) {
            user.setNom(request.getNom());
        }
        if (request.getPrenom() != null) {
            user.setPrenom(request.getPrenom());
        }
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public void desactiverCompte(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AuthException("Utilisateur introuvable"));
        user.setActif(false);
        userRepository.save(user);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .email(user.getEmail())
                .role(user.getRole().name())
                .cabinetId(user.getCabinetId())
                .build();
    }
}