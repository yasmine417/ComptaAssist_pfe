package com.comptaassist.facture_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Component
@Slf4j
public class AuditClient {

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void log(String userEmail,
                    String userRole,
                    String action,
                    String objetType,
                    String objetId,
                    String details) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of(
                    "userEmail", userEmail != null ? userEmail : "",
                    "userRole",  userRole  != null ? userRole  : "",
                    "action",    action    != null ? action    : "",
                    "objetType", objetType != null ? objetType : "",
                    "objetId",   objetId   != null ? objetId   : "",
                    "details",   details   != null ? details   : ""
            );

            restTemplate.exchange(
                    authServiceUrl + "/api/audit/log",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Void.class
            );

            log.info("Audit log envoyé : {} - {}", action, objetId);
        } catch (Exception e) {
            log.warn("Audit log échoué : {}", e.getMessage());
        }
    }
}