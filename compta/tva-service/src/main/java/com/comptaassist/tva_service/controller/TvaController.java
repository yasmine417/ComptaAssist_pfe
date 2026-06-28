package com.comptaassist.tva_service.controller;

import com.comptaassist.tva_service.dto.*;
import com.comptaassist.tva_service.service.TvaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/tva")
@RequiredArgsConstructor
public class TvaController {

    private final TvaService service;

    // ── Dashboard ─────────────────────────────────────
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<DashboardTvaDto> dashboard(
            @RequestParam UUID cabinetId) {
        return ResponseEntity.ok(service.getDashboard(cabinetId));
    }

    // ── Prochaine période à déclarer ──────────────────
    /**
     * GET /api/tva/prochaine-periode?clientId=...
     * Retourne la prochaine période non déclarée
     * avec les dates proposées automatiquement.
     * Le comptable peut les ajuster avant de confirmer.
     */
    @GetMapping("/prochaine-periode")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<ProchainePeriodeDto> prochainePeriode(
            @RequestParam UUID clientId) {
        return ResponseEntity.ok(
                service.calculerProchainePeriode(clientId));
    }

    // ── Configurer régime TVA d'un client ─────────────
    /**
     * POST /api/tva/configurer-regime
     * À faire UNE SEULE FOIS par client.
     * Définit si le client est en régime mensuel ou trimestriel.
     */
    @PostMapping("/configurer-regime")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<ClientTvaConfigDto> configurerRegime(
            @RequestBody ConfigurerRegimeRequest req,
            @AuthenticationPrincipal String comptableId) {
        return ResponseEntity.ok(service.configurerRegime(
                req.getClientId(), req.getCabinetId(),
                UUID.fromString(comptableId), req.getRegime()));
    }

    // ── Récupérer la config TVA d'un client ───────────
    @GetMapping("/config/{clientId}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<?> getConfig(@PathVariable UUID clientId) {
        Optional<ClientTvaConfigDto> config =
                service.getConfigClient(clientId);
        if (config.isPresent())
            return ResponseEntity.ok(config.get());
        return ResponseEntity.notFound().build();
    }

    // ── Calculer TVA (comptable confirme la période) ──
    /**
     * POST /api/tva/calculer
     * Le comptable confirme la période proposée
     * (ou l'ajuste légèrement).
     * Le système calcule la TVA depuis les factures IA.
     */
    @PostMapping("/calculer")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<DeclarationTvaDto> calculer(
            @RequestBody ConfirmerPeriodeRequest req,
            @AuthenticationPrincipal String comptableId,
            HttpServletRequest request) {
        return ResponseEntity.ok(service.calculerDepuisFactures(
                req.getClientId(),
                req.getCabinetId(),
                UUID.fromString(comptableId),
                req.getDateDebut(),
                req.getDateFin(),
                extraireToken(request)));
    }

    // ── Soumettre ─────────────────────────────────────
    @PostMapping("/{id}/soumettre")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<DeclarationTvaDto> soumettre(
            @PathVariable UUID id,
            @AuthenticationPrincipal String comptableId) {
        return ResponseEntity.ok(
                service.soumettre(id, UUID.fromString(comptableId)));
    }

    // ── Historique d'un client ────────────────────────
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<List<DeclarationTvaDto>> parClient(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(service.listerParClient(clientId));
    }

    // ── Détail ────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<DeclarationTvaDto> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    private String extraireToken(HttpServletRequest r) {
        String h = r.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ")
                ? h.substring(7) : "";
    }
    @GetMapping("/alertes")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<DeclarationTvaDto>> alertes(
            @RequestParam UUID cabinetId) {
        return ResponseEntity.ok(
                service.getDeclarationsEnRetard(cabinetId));
    }
}