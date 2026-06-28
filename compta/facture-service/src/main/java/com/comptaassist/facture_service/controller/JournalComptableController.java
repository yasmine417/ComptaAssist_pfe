package com.comptaassist.facture_service.controller;

import com.comptaassist.facture_service.entity.EcritureComptable;
import com.comptaassist.facture_service.repository.FactureCPCRepository;
import com.comptaassist.facture_service.service.JournalComptableService;
import com.comptaassist.facture_service.service.CpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalComptableController {

    private final JournalComptableService journalService;
    private final CpcService              cpcService;
    private final FactureCPCRepository    factureRepo;

    /**
     * Résout le cabinetId depuis le comptableId JWT.
     * Le cabinetId est stocké sur les factures du comptable.
     * S'il n'y a pas encore de factures → retourne null
     * et on lève une exception explicite.
     */
    private UUID resoudreCabinetId(String comptableId) {
        UUID cId = UUID.fromString(comptableId);
        return factureRepo
                .findByComptableIdOrderByCreatedAtDesc(cId)
                .stream()
                .map(f -> f.getCabinetId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Aucune facture trouvée pour ce comptable — "
                                + "cabinetId introuvable"));
    }

    // ── CPC ───────────────────────────────────────────
    /**
     * CPC généré depuis les écritures comptables.
     *
     * Par exercice : GET /api/journal/cpc?exercice=2025
     * Par période  : GET /api/journal/cpc?debut=2025-01-01&fin=2025-12-31
     *
     * Le cabinetId est résolu automatiquement depuis le JWT.
     * Plus besoin de le passer en paramètre.
     */
    @GetMapping("/cpc")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<Map<String, Object>> cpc(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) String exercice,
            @RequestParam(required = false) UUID clientId,
            @AuthenticationPrincipal String comptableId) {

        UUID cabinetId = resoudreCabinetId(comptableId);
        Map<String, java.math.BigDecimal> totaux;
        String periodeDebut, periodeFin;

        if (exercice != null && !exercice.isBlank()) {
            totaux       = clientId != null
                    ? journalService.calculerTotauxPourCpcExerciceEtClient(cabinetId, exercice, clientId)
                    : journalService.calculerTotauxPourCpcExercice(cabinetId, exercice);
            periodeDebut = exercice + "-01-01";
            periodeFin   = exercice + "-12-31";
        } else {
            if (debut == null) debut = LocalDate.now().withDayOfYear(1);
            if (fin   == null) fin   = LocalDate.now();
            totaux       = clientId != null
                    ? journalService.calculerTotauxPourCpcEtClient(cabinetId, debut, fin, clientId)
                    : journalService.calculerTotauxPourCpc(cabinetId, debut, fin);
            periodeDebut = debut.toString();
            periodeFin   = fin.toString();
        }
        return ResponseEntity.ok(cpcService.genererCpc(totaux, periodeDebut, periodeFin));
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<Map<String, Object>>> balance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) UUID clientId,
            @AuthenticationPrincipal String comptableId) {

        UUID cabinetId = resoudreCabinetId(comptableId);
        List<Map<String, Object>> result = clientId != null
                ? journalService.balanceParClient(cabinetId, clientId, debut, fin)
                : journalService.balance(cabinetId, debut, fin);
        return ResponseEntity.ok(result);
    }

    // Dans JournalComptableController.java
// Remplacer le endpoint grand-livre existant par celui-ci :

    @GetMapping("/grand-livre/{compte}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<EcritureComptable>> grandLivre(
            @PathVariable String compte,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) UUID clientId,
            @AuthenticationPrincipal String comptableId) {

        UUID cabinetId = resoudreCabinetId(comptableId);
        return ResponseEntity.ok(
                journalService.grandLivre(cabinetId, compte, debut, fin, clientId));
    }

    // ── Écritures d'une facture ───────────────────────
    /**
     * GET /api/journal/facture/{factureId}
     */
    @GetMapping("/facture/{factureId}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<EcritureComptable>> ecrituresFacture(
            @PathVariable UUID factureId) {
        return ResponseEntity.ok(
                journalService.ecrituresFacture(factureId));
    }
    @GetMapping("/ecritures")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<List<EcritureComptable>> ecritures(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) UUID clientId,
            @AuthenticationPrincipal String comptableId) {

        UUID cabinetId = resoudreCabinetId(comptableId);
        return ResponseEntity.ok(
                journalService.ecrituresPeriode(cabinetId, debut, fin, clientId));
    }
    // ── Validation ────────────────────────────────────
    /**
     * POST /api/journal/valider/{factureId}
     */
    @PostMapping("/valider/{factureId}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<Void> valider(
            @PathVariable UUID factureId) {
        journalService.validerEcritures(factureId);
        return ResponseEntity.ok().build();
    }
}