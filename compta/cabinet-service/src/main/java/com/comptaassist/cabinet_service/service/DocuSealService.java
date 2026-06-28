package com.comptaassist.cabinet_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class DocuSealService {

    @Value("${docuseal.api-token}")
    private String apiToken;

    @Value("${docuseal.base-url:http://localhost:3003/api}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Auth-Token", apiToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ══════════════════════════════════════════════════════════
    // MÉTHODE PRINCIPALE : Créer submission depuis PDF en une seule requête
    // Endpoint : POST /api/submissions/pdf
    // ══════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    public Map<String, Object> envoyerDocument(
            String pdfBase64,
            String nomFichier,
            String titreDocument,
            String clientEmail,
            String clientNom) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", titreDocument);
            body.put("send_email", true);

            // Document PDF
            body.put("documents", List.of(Map.of(
                    "name", nomFichier,
                    "file", "data:application/pdf;base64," + pdfBase64,
                    "fields", List.of(
                            Map.of(
                                    "name",     "Signature",
                                    "type",     "signature",
                                    "role",     "First Party",
                                    "required", true,
                                    "areas",    List.of(Map.of(
                                            "x", 0.55, "y", 0.88,
                                            "w", 0.35, "h", 0.06,
                                            "page", 1
                                    ))
                            ),
                            Map.of(
                                    "name",     "Date",
                                    "type",     "date",
                                    "role",     "First Party",
                                    "required", true,
                                    "areas",    List.of(Map.of(
                                            "x", 0.55, "y", 0.94,
                                            "w", 0.25, "h", 0.04,
                                            "page", 1
                                    ))
                            )
                    )
            )));

            // Signataire
            body.put("submitters", List.of(Map.of(
                    "role",  "First Party",
                    "email", clientEmail,
                    "name",  clientNom
            )));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers());

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/submissions/pdf",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            log.info("DocuSeal soumission créée : {}", response.getBody());
            return response.getBody() != null ? response.getBody() : new HashMap<>();

        } catch (Exception e) {
            log.error("Erreur DocuSeal envoyerDocument : {}", e.getMessage());
            throw new RuntimeException("Erreur envoi DocuSeal : " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // RÉCUPÉRER LE STATUT D'UNE SOUMISSION
    // ══════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStatut(String submissionId) {
        try {
            HttpEntity<Void> request = new HttpEntity<>(headers());
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/submissions/" + submissionId,
                    HttpMethod.GET,
                    request,
                    Map.class
            );
            return response.getBody() != null ? response.getBody() : new HashMap<>();
        } catch (Exception e) {
            log.error("Erreur DocuSeal getStatut : {}", e.getMessage());
            throw new RuntimeException("Erreur statut DocuSeal : " + e.getMessage());
        }
    }
}