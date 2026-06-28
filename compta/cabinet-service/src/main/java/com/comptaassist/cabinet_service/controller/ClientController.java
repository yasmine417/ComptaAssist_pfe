package com.comptaassist.cabinet_service.controller;

// ClientController.java


import com.comptaassist.cabinet_service.dto.ClientRequest;
import com.comptaassist.cabinet_service.dto.ClientResponse;
import com.comptaassist.cabinet_service.service.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cabinets/{cabinetId}/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    // Directeur crée un dossier client
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<ClientResponse> creer(
            @PathVariable UUID cabinetId,
            @Valid @RequestBody ClientRequest request,
            @AuthenticationPrincipal String directeurId) {
        return ResponseEntity.ok(
                clientService.creer(cabinetId, request,
                        UUID.fromString(directeurId)));
    }




    // Lister tous les clients du cabinet (directeur)
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<List<ClientResponse>> lister(
            @PathVariable UUID cabinetId) {
        return ResponseEntity.ok(
                clientService.listerParCabinet(cabinetId));
    }

    // Comptable voit ses propres clients assignés

    @GetMapping("/mes-clients")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<ClientResponse>> mesClients(
            @PathVariable UUID cabinetId,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(
                clientService.listerParComptable(
                        cabinetId,
                        UUID.fromString(userId)));
    }

    // Consulter un client
    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getById(
            @PathVariable UUID cabinetId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.getById(id));
    }

    // Modifier un dossier client
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<ClientResponse> modifier(
            @PathVariable UUID cabinetId,
            @PathVariable UUID id,
            @Valid @RequestBody ClientRequest request,
            @AuthenticationPrincipal String directeurId) {
        return ResponseEntity.ok(
                clientService.modifier(id, request,
                        UUID.fromString(directeurId)));
    }

    // Désactiver un dossier client
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DIRECTEUR')")
    public ResponseEntity<Void> desactiver(
            @PathVariable UUID cabinetId,
            @PathVariable UUID id,
            @AuthenticationPrincipal String directeurId) {
        clientService.desactiver(id, UUID.fromString(directeurId));
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/detail")
    public ResponseEntity<Map<String, Object>>
    getDetailComplet(
            @PathVariable UUID cabinetId,
            @PathVariable UUID id,
            HttpServletRequest request) {

        String jwtToken = extraireToken(request);

        return ResponseEntity.ok(
                clientService.getDetailComplet(
                        cabinetId, id, jwtToken));
    }

    private String extraireToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return "";
    }



}