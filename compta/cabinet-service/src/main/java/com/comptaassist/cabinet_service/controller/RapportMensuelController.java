package com.comptaassist.cabinet_service.controller;

import com.comptaassist.cabinet_service.dto.RapportMensuelRequest;
import com.comptaassist.cabinet_service.entity.RapportMensuel;
import com.comptaassist.cabinet_service.repository.RapportMensuelRepository;
import com.comptaassist.cabinet_service.service.GotenbergService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rapports-mensuels")
@RequiredArgsConstructor
@Slf4j
public class RapportMensuelController {
    private final GotenbergService gotenbergService;

    private final RapportMensuelRepository repo;

    @Value("${app.internal-secret:comptaassist-internal-secret-2024}")
    private String internalSecret;

    // ── Réception depuis n8n (sans JWT, avec secret interne) ──
    @PostMapping("/depuis-n8n")
    public ResponseEntity<Map<String, Object>> recevoirDepuisN8n(
            @RequestBody RapportMensuelRequest request,
            @RequestHeader("X-Internal-Secret") String secret) {

        log.info("Secret reçu: '{}' | Secret attendu: '{}'",
                secret, internalSecret);

        if (!internalSecret.equals(secret)) {
            return ResponseEntity.status(403)
                    .body(Map.of("erreur", "Accès refusé"));
        }


        RapportMensuel rapport = RapportMensuel.builder()
                .clientId(request.getClientId())
                .cabinetId(request.getCabinetId())
                .comptableId(request.getComptableId())
                .nomEntreprise(request.getNomEntreprise())
                .moisLabel(request.getMoisLabel())
                .contenuHtml(request.getContenuHtml())
                .build();

        rapport = repo.save(rapport);
        log.info("Rapport mensuel reçu depuis n8n : {} pour {}",
                rapport.getId(), rapport.getNomEntreprise());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "rapportId", rapport.getId().toString()
        ));
    }

    // ── Liste des rapports pour le comptable ───────────
    @GetMapping("/mes-rapports")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<RapportMensuel>> mesRapports(
            @AuthenticationPrincipal String comptableId) {
        return ResponseEntity.ok(
                repo.findByComptableIdOrderByCreatedAtDesc(
                        UUID.fromString(comptableId)));
    }

    // ── Détail d'un rapport ────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<RapportMensuel> getById(
            @PathVariable UUID id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    // ── Rapports d'un client précis (pour le comptable connecté) ──
    @GetMapping("/par-client/{clientId}")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<List<RapportMensuel>> parClient(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(
                repo.findByClientIdOrderByCreatedAtDesc(clientId));
    }

    // ── Télécharger le rapport en PDF ──────────────────
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('ROLE_COMPTABLE')")
    public ResponseEntity<byte[]> telechargerPdf(
            @PathVariable UUID id) {

        RapportMensuel rapport = repo.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Rapport introuvable"));

        byte[] pdfBytes = gotenbergService.convertirHtmlEnPdf(
                rapport.getContenuHtml());

        String nomFichier = "rapport-"
                + rapport.getMoisLabel().replace(" ", "-")
                + ".pdf";

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + nomFichier + "\"")
                .header("Content-Type", "application/pdf")
                .body(pdfBytes);
    }
}