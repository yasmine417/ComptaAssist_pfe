package com.comptaassist.auth_service.controller;

import com.comptaassist.auth_service.dto.*;
import com.comptaassist.auth_service.entity.AuditLog;
import com.comptaassist.auth_service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {

    private final AdminService  adminService;
    private final AuditService  auditService;

    @Value("${fiscal.rag.url:http://localhost:8085}")
    private String fiscalRagUrl;

    private final RestTemplate restTemplate =
            new RestTemplate();

    // ── Créer directeur ───────────────────────────────────────
    @PostMapping("/directeurs")
    public ResponseEntity<UserResponse> creerDirecteur(
            @Valid @RequestBody CreateDirecteurRequest req,
            @AuthenticationPrincipal String adminEmail) {
        return ResponseEntity.ok(
                adminService.creerDirecteur(req, adminEmail));
    }

    // ── Lister directeurs ─────────────────────────────────────
    @GetMapping("/directeurs")
    public ResponseEntity<List<UserResponse>> getDirecteurs() {
        return ResponseEntity.ok(
                adminService.getDirecteurs());
    }

    // ── Tous les utilisateurs ─────────────────────────────────
    @GetMapping("/utilisateurs")
    public ResponseEntity<List<UserResponse>> getTous() {
        return ResponseEntity.ok(
                adminService.getTousUtilisateurs());
    }

    // ── Comptes EN_ATTENTE ────────────────────────────────────
    @GetMapping("/en-attente")
    public ResponseEntity<List<UserResponse>> getEnAttente() {
        return ResponseEntity.ok(
                adminService.getComptesEnAttente());
    }

    // ── Générer MDP comptable ─────────────────────────────────
    @PostMapping("/utilisateurs/{id}/generer-mdp")
    public ResponseEntity<UserResponse> genererMdp(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminEmail) {
        return ResponseEntity.ok(
                adminService.genererMotDePasseComptable(
                        id, adminEmail));
    }

    // ── Désactiver compte ─────────────────────────────────────
    @DeleteMapping("/utilisateurs/{id}")
    public ResponseEntity<Void> desactiver(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminEmail) {
        adminService.desactiverCompte(id, adminEmail);
        return ResponseEntity.noContent().build();
    }

    // ── Réactiver compte ──────────────────────────────────────
    @PatchMapping("/utilisateurs/{id}/reactiver")
    public ResponseEntity<Void> reactiver(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminEmail) {
        adminService.reactiverCompte(id, adminEmail);
        return ResponseEntity.noContent().build();
    }

    // ── Logs audit ────────────────────────────────────────────
    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String action) {

        PageRequest pageable = PageRequest.of(
                page, size,
                Sort.by("createdAt").descending());

        Page<AuditLog> auditPage;

        if (email != null && !email.isEmpty()) {
            auditPage = auditService.getLogsByEmail(
                    email, pageable);
        } else if (action != null && !action.isEmpty()) {
            auditPage = auditService.getLogsByAction(
                    action, pageable);
        } else {
            auditPage = auditService.getLogs(pageable);
        }

        // Sérialiser manuellement
        List<Map<String, Object>> content =
                auditPage.getContent().stream()
                        .map(log -> {
                            Map<String, Object> m =
                                    new java.util.LinkedHashMap<>();
                            m.put("id", log.getId() != null
                                    ? log.getId().toString() : null);
                            m.put("userId", log.getUserId() != null
                                    ? log.getUserId().toString() : null);
                            m.put("userEmail",  log.getUserEmail());
                            m.put("userRole",   log.getUserRole());
                            m.put("action",     log.getAction());
                            m.put("objetType",  log.getObjetType());
                            m.put("objetId",    log.getObjetId());
                            m.put("details",    log.getDetails());
                            m.put("ipAddress",  log.getIpAddress());
                            m.put("createdAt",  log.getCreatedAt() != null
                                    ? log.getCreatedAt().toString() : null);
                            return m;
                        })
                        .collect(Collectors.toList());

        Map<String, Object> response =
                new java.util.LinkedHashMap<>();
        response.put("content",       content);
        response.put("totalElements", auditPage.getTotalElements());
        response.put("totalPages",    auditPage.getTotalPages());
        response.put("currentPage",   auditPage.getNumber());
        response.put("size",          auditPage.getSize());

        return ResponseEntity.ok(response);
    }

    // ── Indexer RAG — appelle fiscal-rag-service ──────────────
    // ── Indexer RAG — appelle fiscal-rag-service ──────────────
    @PostMapping("/rag/indexer")
    public ResponseEntity<Map<String, Object>> indexerRag(
            @RequestParam String cheminPdf,
            @RequestParam String nomDocument,
            @AuthenticationPrincipal String adminEmail,
            @RequestHeader("Authorization") String authHeader) {

        auditService.log(
                null, adminEmail, "ADMIN",
                "INDEXER_DOCUMENT_RAG",
                "DOCUMENT", nomDocument,
                "Indexation : " + cheminPdf,
                null
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Transmettre le token JWT au fiscal-rag-service
            headers.set("Authorization", authHeader);

            HttpEntity<Void> entity =
                    new HttpEntity<>(headers);

            ResponseEntity<Map> resp =
                    restTemplate.exchange(
                            fiscalRagUrl
                                    + "/api/fiscal/indexer"
                                    + "?cheminPdf=" + cheminPdf
                                    + "&nomDocument=" + nomDocument,
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );

            return ResponseEntity.ok(
                    resp.getBody() != null
                            ? resp.getBody()
                            : Map.of("statut", "ok"));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "statut",  "erreur",
                    "message", e.getMessage()));
        }
    }

    // ── Statut RAG ────────────────────────────────────────────
    @GetMapping("/rag/statut")
    public ResponseEntity<Map<String, Object>> statutRag(
            @RequestHeader("Authorization") String authHeader) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);

            HttpEntity<Void> entity =
                    new HttpEntity<>(headers);

            ResponseEntity<Map> resp =
                    restTemplate.exchange(
                            fiscalRagUrl + "/api/fiscal/statut",
                            HttpMethod.GET,
                            entity,
                            Map.class
                    );

            return ResponseEntity.ok(
                    resp.getBody() != null
                            ? resp.getBody()
                            : Map.of("statut", "indisponible"));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "statut",  "indisponible",
                    "message", e.getMessage()));
        }
    }

}