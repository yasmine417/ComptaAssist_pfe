package com.comptaassist.cabinet_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
@Slf4j
public class TrelloService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${trello.api.key}")
    private String apiKey;

    @Value("${trello.api.token}")
    private String apiToken;

    @Value("${trello.list.non-demarre}")
    private String listeNonDemarre;

    @Value("${trello.list.en-cours}")
    private String listeEnCours;

    @Value("${trello.list.a-jour}")
    private String listeAJour;

    private static final String BASE_URL = "https://api.trello.com/1";

    // ── Créer une carte pour un nouveau client ────────
    public String creerCarte(String nomClient, String clientId) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/cards")
                    .queryParam("key", apiKey)
                    .queryParam("token", apiToken)
                    .queryParam("idList", listeNonDemarre)
                    .queryParam("name", nomClient)
                    .queryParam("desc",
                            "Dossier client ComptaAssist\nID: " + clientId)
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, null, Map.class);

            if (response.getBody() != null) {
                String cardId = (String) response.getBody().get("id");
                log.info("Carte Trello créée: {} pour client {}",
                        cardId, nomClient);
                return cardId;
            }
        } catch (Exception e) {
            log.error("Erreur création carte Trello: {}", e.getMessage());
        }
        return null;
    }

    // ── Déplacer une carte selon le statut calculé ────
    public void deplacerCarte(String cardId, String statutCalcule) {
        if (cardId == null || cardId.isBlank()) return;

        String listeId = switch (statutCalcule) {
            case "NON DÉMARRÉ" -> listeNonDemarre;
            case "EN COURS"    -> listeEnCours;
            case "À JOUR"      -> listeAJour;
            default             -> listeEnCours;
        };

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/cards/" + cardId)
                    .queryParam("key", apiKey)
                    .queryParam("token", apiToken)
                    .queryParam("idList", listeId)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange(
                    url, HttpMethod.PUT,
                    new HttpEntity<>(headers), Void.class);

            log.info("Carte {} déplacée vers liste {}",
                    cardId, statutCalcule);
        } catch (Exception e) {
            log.error("Erreur déplacement carte Trello: {}",
                    e.getMessage());
        }
    }

    // ── Mettre à jour la description avec les détails ──
    public void mettreAJourDescription(
            String cardId, String description) {
        if (cardId == null || cardId.isBlank()) return;

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(BASE_URL + "/cards/" + cardId)
                    .queryParam("key", apiKey)
                    .queryParam("token", apiToken)
                    .queryParam("desc", description)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange(
                    url, HttpMethod.PUT,
                    new HttpEntity<>(headers), Void.class);
        } catch (Exception e) {
            log.error("Erreur mise à jour description: {}",
                    e.getMessage());
        }
    }
}