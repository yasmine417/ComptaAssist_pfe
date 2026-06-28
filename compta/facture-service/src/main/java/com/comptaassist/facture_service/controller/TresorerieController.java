package com.comptaassist.facture_service.controller;

import com.comptaassist.facture_service.dto.tresorerie.*;
import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.repository.FactureCPCRepository;
import com.comptaassist.facture_service.service.TresorerieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tresorerie")
@RequiredArgsConstructor
public class TresorerieController {

    private final TresorerieService    service;
    private final FactureCPCRepository repo;

    /**
     * Charge les factures selon le contexte :
     * - Si clientId fourni → factures de ce client uniquement
     * - Sinon → toutes les factures du comptable
     */
    private List<FactureCPC> chargerFactures(
            String comptableId, UUID clientId) {
        if (clientId != null)
            return repo.findByClientIdOrderByDateFactureDesc(clientId);
        return repo.findByComptableIdOrderByCreatedAtDesc(
                UUID.fromString(comptableId));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<DashboardTresorerieDto> dashboard(
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(
                service.getDashboard(UUID.fromString(comptableId), clientId));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<KpisTresorerieDto> kpis(
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        List<FactureCPC> toutes = chargerFactures(comptableId, clientId);
        return ResponseEntity.ok(service.getKpis(toutes));
    }

    @GetMapping("/evolution-ca")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<EvolutionMoisDto>> evolutionCa(
            @RequestParam(defaultValue = "6") int mois,
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        List<FactureCPC> toutes = chargerFactures(comptableId, clientId);
        return ResponseEntity.ok(service.getEvolutionCa(toutes, mois));
    }

    @GetMapping("/aging")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<AgingCreancesDto> aging(
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        List<FactureCPC> toutes = chargerFactures(comptableId, clientId);
        return ResponseEntity.ok(service.getAgingCreances(toutes));
    }

    @GetMapping("/previsions")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<PrevisionMoisDto>> previsions(
            @RequestParam(defaultValue = "3") int mois,
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        List<FactureCPC> toutes = chargerFactures(comptableId, clientId);
        return ResponseEntity.ok(service.getPrevisions(toutes, mois));
    }

    @GetMapping("/retards")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<FactureRetardDto>> retards(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal String comptableId,
            @RequestParam(required = false) UUID clientId) {
        List<FactureCPC> toutes = chargerFactures(comptableId, clientId);
        return ResponseEntity.ok(service.getFacturesEnRetard(toutes, limit));
    }
}