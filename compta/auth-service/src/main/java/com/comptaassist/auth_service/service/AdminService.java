package com.comptaassist.auth_service.service;

import com.comptaassist.auth_service.dto.*;
import com.comptaassist.auth_service.entity.*;
import com.comptaassist.auth_service.exception.AuthException;
import com.comptaassist.auth_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository     userRepo;
    private final EmailService       emailService;
    private final PasswordEncoder    passwordEncoder;
    private final AuditService       auditService;

    // ── Créer un directeur ────────────────────────────────────
    @Transactional
    public UserResponse creerDirecteur(
            CreateDirecteurRequest req,
            String adminEmail) {

        if (userRepo.existsByEmail(req.getEmail())) {
            throw new AuthException("Email déjà utilisé");
        }

        String motDePasse = genererMotDePasse();

        User user = User.builder()
                .nom(req.getNom())
                .prenom(req.getPrenom())
                .email(req.getEmail())
                .password(passwordEncoder.encode(motDePasse))
                .role(Role.DIRECTEUR)
                .statut(StatutCompte.ACTIF)
                .actif(true)
                .build();

        user = userRepo.save(user);

        // Envoyer email avec identifiants
        emailService.envoyerIdentifiants(
                req.getPrenom() + " " + req.getNom(),
                req.getEmail(),
                motDePasse,
                "Directeur",
                "ComptaAssist AI"
        );

        auditService.log(
                null, adminEmail, "ADMIN",
                "CREER_DIRECTEUR",
                "USER", user.getId().toString(),
                "Directeur créé : " + req.getEmail(),
                null
        );

        log.info("Directeur créé : {}", req.getEmail());
        return toResponse(user);
    }

    // ── Générer mot de passe pour un comptable ────────────────
    @Transactional
    public UserResponse genererMotDePasseComptable(
            UUID userId, String adminEmail) {

        User user = userRepo.findById(userId)
                .orElseThrow(() ->
                        new AuthException("Utilisateur introuvable"));

        if (user.getRole() != Role.COMPTABLE) {
            throw new AuthException(
                    "Cet utilisateur n'est pas un comptable");
        }

        String motDePasse = genererMotDePasse();
        user.setPassword(passwordEncoder.encode(motDePasse));
        user.setStatut(StatutCompte.ACTIF);
        user.setActif(true);
        userRepo.save(user);

        // Envoyer email
        emailService.envoyerIdentifiants(
                user.getPrenom() + " " + user.getNom(),
                user.getEmail(),
                motDePasse,
                "Comptable",
                "ComptaAssist AI"
        );

        auditService.log(
                null, adminEmail, "ADMIN",
                "GENERER_MOT_DE_PASSE",
                "USER", userId.toString(),
                "Mot de passe généré pour : "
                        + user.getEmail(),
                null
        );

        return toResponse(user);
    }

    // ── Lister les comptes en attente ─────────────────────────
    public List<UserResponse> getComptesEnAttente() {
        return userRepo
                .findByStatut(StatutCompte.EN_ATTENTE)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Lister tous les directeurs ────────────────────────────
    public List<UserResponse> getDirecteurs() {
        return userRepo.findByRole(Role.DIRECTEUR)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Lister tous les utilisateurs ──────────────────────────
    public List<UserResponse> getTousUtilisateurs() {
        return userRepo.findAll()
                .stream()
                .filter(u -> u.getRole() != Role.ADMIN)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Désactiver un compte ──────────────────────────────────
    @Transactional
    public void desactiverCompte(UUID userId,
                                 String adminEmail) {
        User user = userRepo.findById(userId)
                .orElseThrow(() ->
                        new AuthException(
                                "Utilisateur introuvable"));
        user.setActif(false);
        user.setStatut(StatutCompte.DESACTIVE);
        userRepo.save(user);

        auditService.log(
                null, adminEmail, "ADMIN",
                "DESACTIVER_COMPTE",
                "USER", userId.toString(),
                "Compte désactivé : " + user.getEmail(),
                null
        );
    }

    // ── Réactiver un compte ───────────────────────────────────
    @Transactional
    public void reactiverCompte(UUID userId,
                                String adminEmail) {
        User user = userRepo.findById(userId)
                .orElseThrow(() ->
                        new AuthException(
                                "Utilisateur introuvable"));
        user.setActif(true);
        user.setStatut(StatutCompte.ACTIF);
        userRepo.save(user);

        auditService.log(
                null, adminEmail, "ADMIN",
                "REACTIVER_COMPTE",
                "USER", userId.toString(),
                "Compte réactivé : " + user.getEmail(),
                null
        );
    }

    private String genererMotDePasse() {
        String upper   = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String lower   = "abcdefghjkmnpqrstuvwxyz";
        String digits  = "23456789";
        String special = "@#$!";
        String all     = upper + lower + digits + special;

        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(rng.nextInt(upper.length())));
        sb.append(lower.charAt(rng.nextInt(lower.length())));
        sb.append(digits.charAt(rng.nextInt(digits.length())));
        sb.append(special.charAt(rng.nextInt(special.length())));
        for (int i = 4; i < 12; i++) {
            sb.append(all.charAt(rng.nextInt(all.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .nom(u.getNom())
                .prenom(u.getPrenom())
                .email(u.getEmail())
                .role(u.getRole().name())
                .cabinetId(u.getCabinetId())
                .actif(u.isActif())
                .statut(u.getStatut().name())
                .build();
    }
}