package com.comptaassist.chat_service.controller;

import com.comptaassist.chat_service.config.JwtUtil;
import com.comptaassist.chat_service.dto.*;
import com.comptaassist.chat_service.entity.Message;
import com.comptaassist.chat_service.service.ChatService;
import com.comptaassist.chat_service.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate =
            new RestTemplate();

    @Value("${cabinet.service.url}")
    private String cabinetServiceUrl;
    // ── Ouvrir / créer une conversation ──────────────────
    @PostMapping("/conversations")
    public ResponseEntity<ConversationDto> ouvrirConversation(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String token) {

        String jwt = token.substring(7);
        UUID comptableId = UUID.fromString(
                jwtUtil.extractUserId(jwt));
        String comptableEmail = jwtUtil.extractEmail(jwt);

        // Priorité 1 : nom venant d'Angular
        String comptableNom = body.getOrDefault(
                "comptableNom", "").trim();

        // Priorité 2 : récupérer depuis cabinet-service
        if (comptableNom.isBlank()) {
            try {
                String url = cabinetServiceUrl
                        + "/api/membres/by-user/"
                        + comptableId;

                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + jwt);

                ResponseEntity<Map> response =
                        restTemplate.exchange(
                                url,
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                Map.class);

                if (response.getBody() != null) {
                    String prenom = (String) response
                            .getBody().get("prenom");
                    String nom = (String) response
                            .getBody().get("nom");
                    comptableNom =
                            ((prenom != null ? prenom : "")
                                    + " "
                                    + (nom != null ? nom : ""))
                                    .trim();
                }
            } catch (Exception e) {
                log.warn("Impossible de récupérer le nom " +
                        "du comptable: {}", e.getMessage());
                comptableNom = comptableEmail;
            }
        }

        log.info("ouvrirConversation: comptableNom='{}'",
                comptableNom);

        ConversationDto dto = chatService.ouvrirConversation(
                comptableId,
                comptableNom,
                comptableEmail,
                UUID.fromString(body.get("clientId")),
                body.get("clientNom"),
                body.get("clientEmail"),
                UUID.fromString(body.get("cabinetId")));

        return ResponseEntity.ok(dto);
    }

    // ── Mes conversations (comptable) ─────────────────────
    @GetMapping("/conversations/comptable")
    public ResponseEntity<List<ConversationDto>>
    mesConversationsComptable(
            @RequestHeader("Authorization") String token) {

        String jwt = token.substring(7);
        UUID comptableId = UUID.fromString(
                jwtUtil.extractUserId(jwt));

        return ResponseEntity.ok(
                chatService.getConversationsComptable(
                        comptableId));
    }

    // ── Mes conversations (client) ────────────────────────
    @GetMapping("/conversations/client/{clientId}")
    public ResponseEntity<List<ConversationDto>>
    conversationsClient(
            @PathVariable UUID clientId) {

        return ResponseEntity.ok(
                chatService.getConversationsClient(
                        clientId));
    }

    // ── Messages d'une conversation ───────────────────────
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                chatService.getMessages(id));
    }

    // ── Envoyer message REST (fallback sans WS) ───────────
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageDto> envoyerMessage(
            @PathVariable UUID id,
            @RequestBody MessageRequest request,
            @RequestHeader(value = "Authorization",
                    required = false) String token) {

        UUID expediteurId = null;

        // Priorité 1 : expediteurId explicite dans le body
        // (envoi par le client sans JWT)
        if (request.getExpediteurId() != null
                && !request.getExpediteurId().isBlank()) {
            expediteurId = UUID.fromString(
                    request.getExpediteurId());
        }

        // Priorité 2 : extraire du JWT (comptable)
        if (expediteurId == null
                && token != null
                && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            expediteurId = UUID.fromString(
                    jwtUtil.extractUserId(jwt));
        }

        if (expediteurId == null) {
            return ResponseEntity.badRequest().build();
        }

        log.info("REST envoyerMessage: expediteurId={}", expediteurId);

        MessageDto msg = chatService.envoyerMessageAuto(
                id,
                expediteurId,
                request.getContenu() != null
                        ? request.getContenu()
                        : request.getNomFichier(),
                request.getTypeMessage() != null
                        ? request.getTypeMessage()
                        : Message.TypeMessage.TEXTE,
                request.getUrlFichier(),
                request.getNomFichier());

        return ResponseEntity.ok(msg);
    }


    // ── Marquer comme lus ─────────────────────────────────
    @PutMapping("/conversations/{id}/lire")
    public ResponseEntity<Void> marquerLus(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization",
                    required = false) String token,
            @RequestParam(required = false) String clientId) {

        Message.ExpediteurType type;

        // Si clientId est passé en param → c'est le CLIENT
        // peu importe le token
        if (clientId != null && !clientId.isBlank()) {
            type = Message.ExpediteurType.CLIENT;
        } else if (token != null
                && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            String role = jwtUtil.extractRole(jwt);
            type = role != null && role.contains("COMPTABLE")
                    ? Message.ExpediteurType.COMPTABLE
                    : Message.ExpediteurType.CLIENT;
        } else {
            type = Message.ExpediteurType.CLIENT;
        }
        log.info("marquerLus: id={} type={} clientId={}",
                id, type, clientId);

        chatService.marquerLus(id, type);
        return ResponseEntity.ok().build();
    }

    private final FileStorageService fileStorage;

    @PostMapping("/conversations/{id}/upload")
    public ResponseEntity<Map<String, String>> uploadFichier(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {

        try {
            String url = fileStorage.uploadFichier(
                    file, id.toString());

            String type = file.getContentType();
            String msgType = type != null
                    && type.startsWith("image")
                    ? "IMAGE" : "FICHIER";

            return ResponseEntity.ok(Map.of(
                    "url",      url,
                    "nom",      file.getOriginalFilename() != null
                            ? file.getOriginalFilename() : "fichier",
                    "type",     msgType
            ));
        } catch (Exception e) {
            log.error("Erreur upload: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("erreur", e.getMessage()));
        }
    }
}