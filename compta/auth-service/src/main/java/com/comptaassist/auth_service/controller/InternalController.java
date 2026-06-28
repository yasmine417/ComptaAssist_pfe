package com.comptaassist.auth_service.controller;

import com.comptaassist.auth_service.entity.Role;
import com.comptaassist.auth_service.entity.StatutCompte;
import com.comptaassist.auth_service.entity.User;
import com.comptaassist.auth_service.exception.AuthException;
import com.comptaassist.auth_service.repository.RefreshTokenRepository;
import com.comptaassist.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalController {

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;

    // ── Créer compte depuis cabinet-service ───────────────────
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> creerUser(
            @RequestBody Map<String, String> req) {

        String email = req.get("email");

        // Si l'utilisateur existe déjà → retourner son ID
        var existant = userRepository.findByEmail(email);
        if (existant.isPresent()) {
            User u = existant.get();
            log.info("Utilisateur déjà existant : {}", email);
            return ResponseEntity.ok(Map.of(
                    "id",     u.getId().toString(),
                    "email",  u.getEmail(),
                    "statut", u.getStatut().name()
            ));
        }

        String cabinetIdStr = req.get("cabinetId");
        UUID cabinetId = cabinetIdStr != null
                ? UUID.fromString(cabinetIdStr) : null;

        User user = User.builder()
                .nom(req.get("nom"))
                .prenom(req.get("prenom"))
                .email(email)
                .password(passwordEncoder.encode(
                        UUID.randomUUID().toString()))
                .role(Role.COMPTABLE)
                .cabinetId(cabinetId)
                .statut(StatutCompte.EN_ATTENTE)
                .actif(false)
                .build();

        user = userRepository.save(user);
        log.info("Compte interne créé : {}", email);

        return ResponseEntity.ok(Map.of(
                "id",     user.getId().toString(),
                "email",  user.getEmail(),
                "statut", user.getStatut().name()
        ));
    }
    // ── Supprimer compte depuis cabinet-service ───────────────
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> supprimerUser(
            @PathVariable UUID id) {
        log.info("Suppression compte demandée : {}", id);

        userRepository.findById(id).ifPresentOrElse(
                user -> {
                    log.info("Suppression user : {}", user.getEmail());
                    // Supprimer refresh tokens d'abord
                    refreshTokenRepository.deleteAllByUserId(id);
                    userRepository.delete(user);
                    log.info("Compte supprimé : {}", user.getEmail());
                },
                () -> log.warn("User introuvable : {}", id)
        );
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/users/{id}/desactiver")
    public ResponseEntity<Void> desactiverUser(
            @PathVariable UUID id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setActif(false);
            user.setStatut(StatutCompte.DESACTIVE);
            userRepository.save(user);
            log.info("Compte désactivé : {}", user.getEmail());
        });
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/reactiver")
    public ResponseEntity<Void> reactiverUser(
            @PathVariable UUID id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setActif(true);
            user.setStatut(StatutCompte.ACTIF);
            userRepository.save(user);
            log.info("Compte réactivé : {}", user.getEmail());
        });
        return ResponseEntity.noContent().build();
    }
}