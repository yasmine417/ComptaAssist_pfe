package com.comptaassist.facture_service.controller;

import com.comptaassist.facture_service.dto.ReclassementRequest;
import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.service.ReclassementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/factures-cpc")
@RequiredArgsConstructor
public class ReclassementController {

    private final ReclassementService reclassementService;

    /**
     * POST /api/factures-cpc/{id}/reclasser
     * Body: { "factureId": "uuid", "classification": "IMMOBILISATION" | "CHARGE" }
     *
     * Appelé quand le comptable confirme la popup de classification.
     */
    @PostMapping("/{id}/reclasser")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<Map<String, Object>> reclasser(
            @PathVariable String id,
            @RequestBody ReclassementRequest req) {
        try {
            req.setFactureId(id);
            FactureCPC facture = reclassementService.reclasser(req);
            return ResponseEntity.ok(Map.of(
                    "success",        true,
                    "classification", req.getClassification(),
                    "factureId",      id,
                    "typeEcriture",   facture.getTypeEcriture() != null
                            ? facture.getTypeEcriture() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}