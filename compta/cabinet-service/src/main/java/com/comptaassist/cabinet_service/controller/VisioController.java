package com.comptaassist.cabinet_service.controller;

import com.comptaassist.cabinet_service.service.LiveKitTokenService;
import com.comptaassist.cabinet_service.service.VisioNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/visio")
@RequiredArgsConstructor
public class VisioController {

    private final LiveKitTokenService tokenService;
    private final VisioNotificationService notifService;

    // ── Le COMPTABLE démarre la visio avec un client ────
    @PostMapping("/demarrer-client")
    public ResponseEntity<Map<String, String>> demarrerAvecClient(
            @RequestParam UUID conversationId,
            @RequestParam String nomComptable,
            @AuthenticationPrincipal String comptableId) {

        String roomName = "visio-" + conversationId;

        String token = tokenService.genererToken(
                roomName, comptableId, nomComptable);

        notifService.notifierVisioClient(
                conversationId, roomName, nomComptable, comptableId);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "url", "ws://localhost:7880",
                "roomName", roomName
        ));
    }

    // ── N'IMPORTE QUI rejoint une room existante ────────
    // (le client clique sur le lien dans le chat)
    @PostMapping("/rejoindre")
    public ResponseEntity<Map<String, String>> rejoindre(
            @RequestParam String roomName,
            @RequestParam String nomAffiche,
            @RequestParam(required = false) String identityId) {

        String identity = identityId != null
                ? identityId : UUID.randomUUID().toString();

        String token = tokenService.genererToken(
                roomName, identity, nomAffiche);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "url", "ws://localhost:7880",
                "roomName", roomName
        ));
    }
}