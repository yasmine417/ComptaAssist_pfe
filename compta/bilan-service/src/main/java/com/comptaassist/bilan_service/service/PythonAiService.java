package com.comptaassist.bilan_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PythonAiService {

    private final WebClient webClient;

    public Map<String, Object> analyserBilan(String cheminPdf) {
        log.info("Appel Python AI pour analyser : {}", cheminPdf);
        try {
            Map<String, Object> response = webClient.post()
                    .uri("/analyser-bilan")
                    .bodyValue(Map.of("chemin_pdf", cheminPdf))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            log.info("Réponse Python AI reçue");
            return response;
        } catch (Exception e) {
            log.error("Erreur appel Python AI : {}", e.getMessage());
            throw new RuntimeException(
                    "Erreur communication avec le moteur IA : "
                            + e.getMessage());
        }
    }
}