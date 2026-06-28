package com.comptaassist.cabinet_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VisioNotificationService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${chat.service.url:http://localhost:8088}")
    private String chatServiceUrl;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // ── Envoie un message dans le chat avec lien visio ──
    public void notifierVisioClient(
            UUID conversationId,
            String roomName,
            String nomComptable,
            String expediteurId) {

        try {
            String lienVisio = frontendUrl
                    + "/visio/rejoindre?room=" + roomName;

            Map<String, Object> body = new HashMap<>();
            body.put("conversationId", conversationId.toString());
            body.put("contenu",
                    nomComptable + " vous invite à une visioconférence. "
                            + "Cliquez pour rejoindre : " + lienVisio);
            body.put("typeMessage", "TEXTE");
            body.put("expediteurId", expediteurId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.postForEntity(
                    chatServiceUrl + "/api/chat/conversations/"
                            + conversationId + "/messages",
                    new HttpEntity<>(body, headers),
                    Void.class);

            log.info("Notification visio envoyée dans conv {}",
                    conversationId);
        } catch (Exception e) {
            log.error("Erreur notification visio client: {}",
                    e.getMessage());
        }
    }
}