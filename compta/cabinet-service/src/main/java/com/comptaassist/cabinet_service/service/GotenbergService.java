package com.comptaassist.cabinet_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class GotenbergService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gotenberg.url:http://localhost:3002}")
    private String gotenbergUrl;

    public byte[] convertirHtmlEnPdf(String html) {
        try {
            ByteArrayResource resource = new ByteArrayResource(
                    html.getBytes()) {
                @Override
                public String getFilename() {
                    return "index.html";
                }
            };

            LinkedMultiValueMap<String, Object> body =
                    new LinkedMultiValueMap<>();
            body.add("files", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    gotenbergUrl + "/forms/chromium/convert/html",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    byte[].class);

            return response.getBody();

        } catch (Exception e) {
            log.error("Erreur conversion PDF Gotenberg : {}",
                    e.getMessage());
            throw new RuntimeException(
                    "Erreur génération PDF : " + e.getMessage());
        }
    }
}