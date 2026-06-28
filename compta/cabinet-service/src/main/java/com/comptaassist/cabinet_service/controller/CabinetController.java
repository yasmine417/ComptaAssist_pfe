package com.comptaassist.cabinet_service.controller;

// CabinetController.java

import com.comptaassist.cabinet_service.dto.CabinetRequest;
import com.comptaassist.cabinet_service.dto.CabinetResponse;
import com.comptaassist.cabinet_service.service.CabinetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cabinets")
@RequiredArgsConstructor
public class CabinetController {

    private final CabinetService cabinetService;

    // Directeur crée son cabinet
    @PostMapping
    public ResponseEntity<CabinetResponse> creer(
            @Valid @RequestBody CabinetRequest request,
            @AuthenticationPrincipal String directeurId) {
        return ResponseEntity.ok(
                cabinetService.creer(request, UUID.fromString(directeurId)));
    }

    // Directeur consulte son cabinet
    @GetMapping("/mon-cabinet")
    public ResponseEntity<CabinetResponse> getMonCabinet(
            @AuthenticationPrincipal String directeurId) {
        return ResponseEntity.ok(
                cabinetService.getByDirecteurId(
                        UUID.fromString(directeurId)));
    }

    // Consulter un cabinet par id
    @GetMapping("/{id}")
    public ResponseEntity<CabinetResponse> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(cabinetService.getById(id));
    }

    // Modifier le cabinet
    @PutMapping("/{id}")
    public ResponseEntity<CabinetResponse> modifier(
            @PathVariable UUID id,
            @Valid @RequestBody CabinetRequest request,
            @AuthenticationPrincipal String directeurId) {
        return ResponseEntity.ok(
                cabinetService.modifier(id, request,
                        UUID.fromString(directeurId)));
    }
}