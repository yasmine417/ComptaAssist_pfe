package com.comptaassist.tva_service.client;

import com.comptaassist.tva_service.dto.FactureDto;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Client REST vers facture-service (port 8086).
 * Récupère les factures d'un client pour calculer la TVA.
 */
@Component
@Slf4j
public class FactureServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${facture.service.url:http://localhost:8086}")
    private String factureServiceUrl;

    public List<FactureDto> getFacturesClient(
            UUID clientId, String jwtToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);

            String url = factureServiceUrl
                    + "/api/factures-cpc/mes-factures"
                    + "?clientId=" + clientId;

            log.info("→ Appel facture-service : {}", url);

            ResponseEntity<List<FactureDto>> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            new ParameterizedTypeReference<>() {});

            List<FactureDto> factures = response.getBody();
            log.info("← {} factures reçues pour client={}",
                    factures != null ? factures.size() : 0, clientId);

            if (factures != null) {
                for (FactureDto f : factures) {
                    log.info("  facture={} type={} date={} ht={} tva={} tvaTaux={}",
                            f.getId(), f.getTypeOperation(),
                            f.getDateFacture(), f.getMontantHt(),
                            f.getMontantTva(), f.getTvaTaux());
                }
            }
            return factures != null ? factures : Collections.emptyList();

        } catch (Exception e) {
            log.error("Erreur récupération factures client={} : {}",
                    clientId, e.getMessage());
            return Collections.emptyList();
        }
    }
}