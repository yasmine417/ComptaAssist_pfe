// BilanController.java
package com.comptaassist.bilan_service.controller;

import com.comptaassist.bilan_service.dto.AnalyseBilanResponse;
import com.comptaassist.bilan_service.dto.DemandeAnalyseRequest;
import com.comptaassist.bilan_service.service.BilanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bilans")
@RequiredArgsConstructor
public class BilanController {

    private final BilanService bilanService;

    // Analyser un bilan PDF
    @PostMapping("/analyser")
    public ResponseEntity<AnalyseBilanResponse> analyser(
            @Valid @RequestBody DemandeAnalyseRequest request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(
                bilanService.analyser(request,
                        UUID.fromString(userId)));
    }

    // Historique des analyses d'un client
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<AnalyseBilanResponse>> getHistorique(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(
                bilanService.getHistoriqueClient(clientId));
    }

    // Consulter une analyse par id
    @GetMapping("/{id}")
    public ResponseEntity<AnalyseBilanResponse> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(bilanService.getById(id));
    }

    // Toutes les analyses d'un cabinet
    @GetMapping("/cabinet/{cabinetId}")
    public ResponseEntity<List<AnalyseBilanResponse>> getByCabinet(
            @PathVariable UUID cabinetId) {
        return ResponseEntity.ok(
                bilanService.getByCabinet(cabinetId));
    }

    // Supprimer une analyse
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        bilanService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}