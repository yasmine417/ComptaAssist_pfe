package com.comptaassist.facture_service.controller;

import com.comptaassist.facture_service.dto
        .LienUploadResponse;
import com.comptaassist.facture_service.service
        .LienClientUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost
        .PreAuthorize;
import org.springframework.security.core.annotation
        .AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/liens-upload")
@RequiredArgsConstructor
public class LienClientUploadController {

    private final LienClientUploadService service;

    // ── Générer lien pour un client ───────────────────
    @PostMapping("/generer")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<LienUploadResponse>
    generer(
            @RequestParam UUID clientId,
            @RequestParam String nomClient,
            @RequestParam String emailClient,
            @RequestParam UUID cabinetId,
            @AuthenticationPrincipal
            String comptableId) {

        return ResponseEntity.ok(
                service.genererLien(
                        clientId, cabinetId,
                        UUID.fromString(comptableId),
                        nomClient, emailClient));
    }

    // ── Valider token (pour page upload client) ───────
    @GetMapping("/valider/{token}")
    public ResponseEntity<Map<String, Object>>
    valider(@PathVariable String token) {
        try {
            var lien = service.validerToken(token);
            return ResponseEntity.ok(Map.of(
                    "valide",     true,
                    "nomClient",  lien.getNomClient(),
                    "expiresAt",  lien.getExpiresAt(),
                    "clientId",  lien.getClientId().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "valide",  false,
                    "message", e.getMessage()
            ));
        }
    }

    // ── Lister liens du comptable ─────────────────────
    @GetMapping("/mes-liens")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<?>> mesLiens(
            @AuthenticationPrincipal
            String comptableId) {
        return ResponseEntity.ok(
                service.listerParComptable(
                        UUID.fromString(comptableId)));
    }
}