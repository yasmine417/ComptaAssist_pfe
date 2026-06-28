package com.comptaassist.cabinet_service.controller;

// MembreController.java


import com.comptaassist.cabinet_service.dto.MembreRequest;
import com.comptaassist.cabinet_service.dto.MembreResponse;
import com.comptaassist.cabinet_service.service.MembreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cabinets/{cabinetId}/membres")
@RequiredArgsConstructor
public class MembreController {

    private final MembreService membreService;

    // Directeur ajoute un comptable à son équipe
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<MembreResponse> ajouter(
            @PathVariable UUID cabinetId,
            @Valid @RequestBody MembreRequest request,
            @AuthenticationPrincipal String directeurId) {
        return ResponseEntity.ok(
                membreService.ajouter(cabinetId, request,
                        UUID.fromString(directeurId)));
    }

    // Lister tous les membres du cabinet
    @GetMapping
    public ResponseEntity<List<MembreResponse>> lister(
            @PathVariable UUID cabinetId) {
        return ResponseEntity.ok(
                membreService.listerParCabinet(cabinetId));
    }

    // Consulter un membre
    @GetMapping("/{id}")
    public ResponseEntity<MembreResponse> getById(
            @PathVariable UUID cabinetId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(membreService.getById(id));
    }

    // Directeur désactive un membre



    // Désactiver un membre
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<Void> desactiver(
            @PathVariable UUID cabinetId,
            @PathVariable UUID id,
            @AuthenticationPrincipal String directeurId) {
        membreService.desactiver(
                id, UUID.fromString(directeurId));
        return ResponseEntity.noContent().build();
    }

    // Supprimer définitivement un membre
    @DeleteMapping("/{id}/supprimer")
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<Void> supprimer(
            @PathVariable UUID cabinetId,
            @PathVariable UUID id,
            @AuthenticationPrincipal String directeurId) {
        membreService.supprimer(
                id, UUID.fromString(directeurId));
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}/reactiver")
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<Void> reactiver(
            @PathVariable UUID cabinetId,
            @PathVariable UUID id,
            @AuthenticationPrincipal String directeurId) {
        membreService.reactiver(
                id, UUID.fromString(directeurId));
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<MembreResponse> getByUserId(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(
                membreService.getByUserId(userId));
    }
}