package com.comptaassist.facture_service.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications/rapport")
@RequiredArgsConstructor
public class RapportNotificationController {

    private final RapportNotificationRepository repo;

    // n8n appelle cet endpoint après envoi email
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_COMPTABLE')")
    public ResponseEntity<RapportNotification> creer(
            @RequestBody Map<String, String> body) {
        RapportNotification notif = RapportNotification.builder()
                .comptableId(body.get("comptableId"))
                .clientId(body.get("clientId"))
                .nomEntreprise(body.get("nomEntreprise"))
                .moisLabel(body.get("moisLabel"))
                .message("Le rapport de " + body.get("nomEntreprise")
                        + " pour " + body.get("moisLabel")
                        + " a été envoyé par email.")
                .lu(false)
                .build();
        return ResponseEntity.ok(repo.save(notif));
    }

    // Angular appelle cet endpoint pour afficher les notifs
    @GetMapping("/{comptableId}")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<List<RapportNotification>> getNotifs(
            @PathVariable String comptableId) {
        return ResponseEntity.ok(
                repo.findByComptableIdOrderByCreatedAtDesc(comptableId));
    }

    // Nombre de notifs non lues
    @GetMapping("/{comptableId}/count")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<Long> countNonLues(
            @PathVariable String comptableId) {
        return ResponseEntity.ok(
                repo.countByComptableIdAndLuFalse(comptableId));
    }

    // Marquer comme lu
    @PutMapping("/{id}/lu")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<Void> marquerLu(@PathVariable String id) {
        repo.findById(id).ifPresent(n -> {
            n.setLu(true);
            repo.save(n);
        });
        return ResponseEntity.ok().build();
    }
}