package com.comptaassist.cabinet_service.controller;

import com.comptaassist.cabinet_service.dto.AvancementDossierResponse;
import com.comptaassist.cabinet_service.service.AvancementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cabinets/{cabinetId}/avancement")
@RequiredArgsConstructor
public class AvancementController {

    private final AvancementService avancementService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<List<AvancementDossierResponse>>
    listerTous(
            @PathVariable UUID cabinetId,
            HttpServletRequest request) {
        String jwt = extraireToken(request);
        return ResponseEntity.ok(
                avancementService
                        .getAvancementParCabinet(cabinetId, jwt));
    }

    @GetMapping("/mes-dossiers")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<AvancementDossierResponse>>
    mesDossiers(
            @PathVariable UUID cabinetId,
            @AuthenticationPrincipal String userId,
            HttpServletRequest request) {
        String jwt = extraireToken(request);
        return ResponseEntity.ok(
                avancementService.getAvancementParComptable(
                        cabinetId,
                        UUID.fromString(userId), jwt));
    }

    private String extraireToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return "";
    }
}