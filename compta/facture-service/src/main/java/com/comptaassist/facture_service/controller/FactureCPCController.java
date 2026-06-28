package com.comptaassist.facture_service.controller;

import com.comptaassist.facture_service.dto.FactureCPCResponse;
import com.comptaassist.facture_service.service.FactureCPCService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/factures-cpc")
@RequiredArgsConstructor
@Slf4j
public class FactureCPCController {

    private final FactureCPCService service;

    // ── Analyser depuis comptable ─────────────────────────────
    @PostMapping("/analyser")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<FactureCPCResponse> analyser(
            @RequestParam("fichier")    MultipartFile fichier,
            @RequestParam("clientId")   UUID clientId,
            @RequestParam("cabinetId")  UUID cabinetId,
            @RequestParam(value = "documentId",  required = false) UUID documentId,
            @RequestParam(value = "minioObject", required = false) String minioObject,
            @AuthenticationPrincipal String comptableId,
            HttpServletRequest request) {      // ← pour récupérer le JWT

        String jwtToken = extraireToken(request);

        return ResponseEntity.ok(service.analyserEtSauvegarder(
                fichier, clientId, cabinetId,
                UUID.fromString(comptableId),
                documentId, minioObject,
                jwtToken));                    // ← passe le JWT
    }

    // ── Analyser depuis token client (upload sans login) ──────
    @PostMapping("/upload-client/{token}")
    public ResponseEntity<Map<String, Object>> uploadClient(
            @PathVariable String token,
            @RequestParam("fichier") MultipartFile fichier,
            HttpServletRequest request) {

        String jwtToken = extraireToken(request);

        return ResponseEntity.ok(
                service.analyserDepuisToken(token, fichier, jwtToken));
    }

    // ── Mes factures ──────────────────────────────────────────
    @GetMapping("/mes-factures")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<FactureCPCResponse>> mesFactures(
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        log.info("mesFactures — comptableId={} clientId={}", comptableId, clientId);
        if (clientId != null) {
            log.info("→ filtrage par clientId={}", clientId);
            return ResponseEntity.ok(service.listerParClient(clientId));
        }
        log.info("→ filtrage par comptableId={}", comptableId);
        return ResponseEntity.ok(
                service.listerParComptable(UUID.fromString(comptableId)));
    }

    // ── Par statut ────────────────────────────────────────────
    @GetMapping("/mes-factures/statut/{statut}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<FactureCPCResponse>> parStatut(
            @AuthenticationPrincipal String comptableId,
            @PathVariable String statut,
            @RequestParam(required = false) UUID clientId) {
        if (clientId != null)
            return ResponseEntity.ok(
                    service.listerParClientEtStatut(clientId, statut));
        return ResponseEntity.ok(
                service.listerParStatut(UUID.fromString(comptableId), statut));
    }

    // ── Détail ────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<FactureCPCResponse> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    // ── Changer statut ────────────────────────────────────────
    @PatchMapping("/{id}/statut/{statut}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<FactureCPCResponse> changerStatut(
            @PathVariable UUID id,
            @PathVariable String statut,
            @AuthenticationPrincipal String comptableId) {
        return ResponseEntity.ok(service.changerStatut(id, statut));
    }

    // ── Stats ─────────────────────────────────────────────────
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<Map<String, Long>> stats(
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        if (clientId != null)
            return ResponseEntity.ok(service.statsParClient(clientId));
        return ResponseEntity.ok(
                service.stats(UUID.fromString(comptableId)));
    }

    // ── Modifier écriture ─────────────────────────────────────
    @PutMapping("/{id}/ecriture")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<FactureCPCResponse> modifierEcriture(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.modifierEcriture(id, body));
    }

    // ── Confirmer paiement ────────────────────────────────────
    @PatchMapping("/{id}/paiement")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<FactureCPCResponse> confirmerPaiement(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.confirmerPaiement(id, body));
    }

    // ── Export ────────────────────────────────────────────────
    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<byte[]> exporter(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "csv") String format) {
        return service.exporterEcriture(id, format);
    }

    // ── Helper : extraire le JWT depuis le header Authorization ──
    private String extraireToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return "";
    }
    @GetMapping("/dashboard-stats")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<Map<String, Object>> dashboardStats(
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(
                service.getDashboardStats(
                        UUID.fromString(comptableId), clientId));
    }

    // ── Stats avancement pour un client ───────────────────────
    @GetMapping("/stats-avancement/{clientId}")
    public ResponseEntity<Map<String, Object>>
    statsAvancement(@PathVariable UUID clientId) {
        return ResponseEntity.ok(
                service.getStatsAvancement(clientId));
    }

    // ── Factures par client (accessible directeur) ────────────
    @GetMapping("/par-client/{clientId}")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<List<FactureCPCResponse>> parClientPublic(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(
                service.listerParClient(clientId));
    }
}