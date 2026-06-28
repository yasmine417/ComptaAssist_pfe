// TendanceController.java
package com.comptaassist.bilan_service.controller;

import com.comptaassist.bilan_service.dto.TendanceResponse;
import com.comptaassist.bilan_service.service.TendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tendances")
@RequiredArgsConstructor
public class TendanceController {

    private final TendanceService tendanceService;

    // Toutes les tendances d'un client
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<TendanceResponse>> getTendances(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(
                tendanceService.getTendancesClient(clientId));
    }

    // Alertes non traitées d'un client
    @GetMapping("/client/{clientId}/alertes")
    public ResponseEntity<List<TendanceResponse>> getAlertes(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(
                tendanceService.getAlertesNonTraitees(clientId));
    }

    // Toutes les alertes non traitées d'un cabinet
    @GetMapping("/cabinet/{cabinetId}/alertes")
    public ResponseEntity<List<TendanceResponse>> getAlertesCabinet(
            @PathVariable UUID cabinetId) {
        return ResponseEntity.ok(
                tendanceService.getAlertesNonTraiteesCabinet(cabinetId));
    }

    // Marquer une alerte comme traitée
    @PatchMapping("/{id}/traiter")
    public ResponseEntity<Void> marquerTraite(@PathVariable UUID id) {
        tendanceService.marquerTraite(id);
        return ResponseEntity.noContent().build();
    }
}