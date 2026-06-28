package com.comptaassist.facture_service.controller;

import com.comptaassist.facture_service.service.BilanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bilan")
@RequiredArgsConstructor
public class BilanController {

    private final BilanService bilanService;

    @GetMapping("/{clientId}")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<Map<String, Object>> getBilan(
            @PathVariable UUID clientId,
            @RequestParam(required = false) Integer exercice,
            @RequestParam(required = false) Double capitalSocial) {

        BigDecimal capital = capitalSocial != null
                ? BigDecimal.valueOf(capitalSocial)
                : BigDecimal.ZERO;

        return ResponseEntity.ok(
                bilanService.getBilanClient(
                        clientId, exercice, capital));
    }



    @GetMapping("/{clientId}/export-excel")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<byte[]> exportExcel(
            @PathVariable UUID clientId,
            @RequestParam(required = false) Integer exercice,
            @RequestParam(required = false) Double capitalSocial)
            throws Exception {

        BigDecimal capital = capitalSocial != null
                ? BigDecimal.valueOf(capitalSocial)
                : BigDecimal.ZERO;

        int anneeN   = exercice != null ? exercice
                : java.time.LocalDate.now().getYear();
        int anneeN1  = anneeN - 1;

        Map<String, Object> bilanN  = bilanService.getBilanClient(
                clientId, anneeN,  capital);
        Map<String, Object> bilanN1 = bilanService.getBilanClient(
                clientId, anneeN1, capital);

        // Dernière écriture pour déterminer la date de clôture réelle
        java.time.LocalDate dateCloture =
                bilanService.getDateCloture(clientId, anneeN);

        byte[] excel = bilanService.exporterExcel(
                bilanN, bilanN1, clientId.toString(),
                anneeN, dateCloture);

        String nomFichier = "bilan-" + anneeN + ".xlsx";

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=" + nomFichier)
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet")
                .body(excel);
    }

    @GetMapping("/{clientId}/existe")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<Map<String, Boolean>>
    bilanExiste(
            @PathVariable UUID clientId,
            @RequestParam int exercice) {

        Map<String, Object> bilan =
                bilanService.getBilanClient(
                        clientId, exercice, BigDecimal.ZERO);

        Map<String, Object> actif =
                (Map<String, Object>) bilan.get("actif");
        BigDecimal totalActif =
                (BigDecimal) actif.get("totalActif");

        boolean existe = totalActif != null
                && totalActif.compareTo(BigDecimal.ZERO) != 0;

        return ResponseEntity.ok(
                Map.of("existe", existe));
    }
}