package com.comptaassist.facture_service.controller;

import com.comptaassist.facture_service.entity.EcritureComptable;
import com.comptaassist.facture_service.repository.EcritureComptableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ecritures")
@RequiredArgsConstructor
public class EcritureComptableController {

    private final EcritureComptableRepository ecritureRepo;

    // ── Écritures par client (accessible directeur) ───────────
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<List<EcritureComptable>>
    parClient(@PathVariable UUID clientId) {
        return ResponseEntity.ok(
                ecritureRepo.findByClientIdOrderByCompteAsc(
                        clientId));
    }
}