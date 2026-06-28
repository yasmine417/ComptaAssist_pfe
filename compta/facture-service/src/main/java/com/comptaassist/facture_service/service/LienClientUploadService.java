package com.comptaassist.facture_service.service;

import com.comptaassist.facture_service.dto
        .LienUploadResponse;
import com.comptaassist.facture_service.entity
        .LienClientUpload;
import com.comptaassist.facture_service.repository
        .LienClientUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LienClientUploadService {

    private final LienClientUploadRepository repo;

    // ── Générer lien unique pour client ───────────────
    public LienUploadResponse genererLien(
            UUID clientId, UUID cabinetId,
            UUID comptableId, String nomClient,
            String emailClient) {

        // Révoquer ancien lien si existant
        repo.findAllByClientId(clientId)
                .forEach(l -> {
                    l.setActif(false);
                    repo.save(l);
                });

        String token = UUID.randomUUID().toString()
                + "-" + UUID.randomUUID().toString();

        LienClientUpload lien =
                LienClientUpload.builder()
                        .token(token)
                        .clientId(clientId)
                        .cabinetId(cabinetId)
                        .comptableId(comptableId)
                        .nomClient(nomClient)
                        .emailClient(emailClient)
                        .actif(true)
                        .expiresAt(LocalDateTime.now()
                                .plusDays(30))
                        .build();

        lien = repo.save(lien);
        log.info("Lien généré pour client {} : {}",
                clientId, token);

        return LienUploadResponse.builder()
                .token(token)
                .url("http://localhost:4200/upload/"
                        + token)
                .expiresAt(lien.getExpiresAt())
                .nomClient(nomClient)
                .actif(true)
                .build();
    }

    // ── Valider token ─────────────────────────────────
    public LienClientUpload validerToken(
            String token) {
        LienClientUpload lien = repo.findByToken(token)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Lien invalide"));

        if (!lien.getActif()) {
            throw new RuntimeException(
                    "Lien désactivé");
        }
        if (lien.getExpiresAt()
                .isBefore(LocalDateTime.now())) {
            throw new RuntimeException(
                    "Lien expiré");
        }
        return lien;
    }

    // ── Incrémenter compteur ──────────────────────────
    public void incrementerCompteur(String token) {
        repo.findByToken(token).ifPresent(l -> {
            l.setNbFichiersUploades(
                    l.getNbFichiersUploades() + 1);
            repo.save(l);
        });
    }

    // ── Lister liens du comptable ─────────────────────
    public List<LienClientUpload>
    listerParComptable(UUID comptableId) {
        return repo.findAllByComptableId(comptableId);
    }
}