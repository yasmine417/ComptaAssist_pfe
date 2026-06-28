package com.comptaassist.cabinet_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class DocumentServiceClient {

    @Value("${document.service.url:http://localhost:8083}")
    private String documentServiceUrl;

    @Value("${local.uploads.path:uploads}")
    private String dossierUploadsLocal;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Sauvegarder PDF → retourne minioObject exact ──────────────────────
    @SuppressWarnings("unchecked")
    public String sauvegarderPdf(
            byte[] pdfBytes,
            String nomFichier,
            UUID clientId,
            UUID cabinetId,
            UUID comptableId,
            String jwtToken) {

        // ── Sauvegarde locale en premier (résilience si MinIO tombe) ──
        try {
            sauvegarderLocal(pdfBytes, nomFichier);
        } catch (Exception e) {
            log.warn("Sauvegarde locale échouée (non bloquant) : {}",
                    e.getMessage());
        }

        try {
            ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
                @Override public String getFilename() { return nomFichier; }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("fichier",      resource);
            body.add("typeDocument", "SIGNATURE");
            body.add("clientId",     clientId.toString());
            body.add("cabinetId",    cabinetId.toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + jwtToken);

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    documentServiceUrl + "/api/documents",
                    HttpMethod.POST, request, Map.class
            );

            Map<String, Object> result = response.getBody();
            if (result == null)
                throw new RuntimeException("Réponse vide document-service");

            String minioObject = result.get("minioObject") != null
                    ? result.get("minioObject").toString() : "";

            log.info("PDF sauvegardé, minioObject={}", minioObject);
            return minioObject;

        } catch (Exception e) {
            log.error("Erreur document-service : {}", e.getMessage());
            throw new RuntimeException("Erreur sauvegarde PDF : " + e.getMessage());
        }
    }

    // ── Sauvegarde locale dans le dossier uploads ─────────────────
    private void sauvegarderLocal(
            byte[] bytes, String nomFichier) throws Exception {

        Path dossier = Paths.get(dossierUploadsLocal, "signatures");
        Files.createDirectories(dossier);

        String nomUnique = UUID.randomUUID() + "_" + nomFichier;
        Path cheminFichier = dossier.resolve(nomUnique);
        Files.write(cheminFichier, bytes);

        log.info("PDF sauvegardé localement : {}",
                cheminFichier.toAbsolutePath());
    }

    // ── Générer URL presignée — NE PAS encoder le slash ──────────────────
    @SuppressWarnings("unchecked")
    public String getUrlPresignee(String minioObject) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(documentServiceUrl + "/api/documents/presigned-url")
                    .queryParam("object", minioObject)
                    .build(false)
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> result = response.getBody();
            if (result == null) return "";
            return result.getOrDefault("url", "").toString();

        } catch (Exception e) {
            log.error("Erreur URL presignée : {}", e.getMessage());
            return "";
        }
    }
}