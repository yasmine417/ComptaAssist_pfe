package com.comptaassist.auth_service.service;


import com.comptaassist.auth_service.dto.*;
import com.comptaassist.auth_service.entity.*;
import com.comptaassist.auth_service.exception.AuthException;
import com.comptaassist.auth_service.repository.*;
import com.comptaassist.auth_service.dto.AuthResponse;
import com.comptaassist.auth_service.dto.UserResponse;
import com.comptaassist.auth_service.entity.RefreshToken;
import com.comptaassist.auth_service.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository     userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService         jwtService;
    private final PasswordEncoder    passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new AuthException("Email déjà utilisé");
        }

        Role role = Role.COMPTABLE;
        if (req.getRole() != null) {
            try { role = Role.valueOf(req.getRole().toUpperCase()); }
            catch (IllegalArgumentException e) {
                throw new AuthException("Rôle invalide : " + req.getRole());
            }
        }
        StatutCompte statut = "EN_ATTENTE".equals(req.getStatut())
                ? StatutCompte.EN_ATTENTE
                : StatutCompte.ACTIF;

        User user = User.builder()
                .nom(req.getNom())
                .prenom(req.getPrenom())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(role)
                .cabinetId(req.getCabinetId())
                .statut(statut)
                .actif(statut == StatutCompte.ACTIF)
                .build();

        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new AuthException("Email ou mot de passe incorrect"));


        System.out.println("LOGIN EMAIL = " + req.getEmail());
        System.out.println("ROLE = " + user.getRole());
        System.out.println("ACTIF = " + user.isActif());
        System.out.println("MATCH = " + passwordEncoder.matches(req.getPassword(), user.getPassword()));
        System.out.println(passwordEncoder.encode("Admin@2025"));

        if (!user.isActif()) {
            throw new AuthException(
                    "Votre compte a été désactivé. " +
                            "Contactez votre administrateur.");
        }

        // ← Vérifier que le compte n'est pas EN_ATTENTE
        if (user.getStatut() == StatutCompte.EN_ATTENTE) {
            throw new AuthException(
                    "Votre compte est en attente d'activation. " +
                            "Contactez votre administrateur.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new AuthException("Email ou mot de passe incorrect");
        }

        // Révoquer tous les anciens refresh tokens
        refreshTokenRepository.revokeAllByUserId(user.getId());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenStr) {
        if (!jwtService.isTokenValid(refreshTokenStr)) {
            throw new AuthException("Refresh token invalide");
        }

        RefreshToken stored = refreshTokenRepository
                .findByToken(refreshTokenStr)
                .orElseThrow(() -> new AuthException("Refresh token introuvable"));

        if (stored.isRevoked()) {
            throw new AuthException("Refresh token révoqué");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException("Refresh token expiré");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return buildAuthResponse(stored.getUser());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        RefreshToken rt = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(rt);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserResponse(user))
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .email(user.getEmail())
                .role(user.getRole().name())
                .cabinetId(user.getCabinetId())
                .actif(user.isActif())
                .statut(user.getStatut().name())
                .build();
    }
}