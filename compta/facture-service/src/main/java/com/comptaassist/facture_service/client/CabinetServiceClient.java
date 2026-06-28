package com.comptaassist.facture_service.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.UUID;

/**
 * Appel REST vers cabinet-service pour récupérer l'ICE du client.
 * On réutilise l'endpoint GET /api/cabinets/{cabinetId}/clients/{id}
 * qui retourne déjà un ClientResponse avec le champ ice.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CabinetServiceClient {

    private final RestTemplate restTemplate;

    @Value("${cabinet.service.url:http://localhost:8085}")
    private String cabinetServiceUrl;

    /**
     * Récupère l'ICE d'un client depuis cabinet-service.
     * Utilise l'endpoint existant GET /api/cabinets/{cabinetId}/clients/{id}
     * qui retourne ClientResponse (déjà existant, pas besoin de le modifier).
     *
     * @return ICE ou null si absent / erreur
     */
    public String getIceClient(UUID clientId,
                               UUID cabinetId,
                               String jwtToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);

            ResponseEntity<ClientResponseRemote> resp =
                    restTemplate.exchange(
                            cabinetServiceUrl
                                    + "/api/cabinets/" + cabinetId
                                    + "/clients/" + clientId,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            ClientResponseRemote.class);

            if (resp.getBody() != null) {
                String ice = resp.getBody().getIce();
                log.debug("ICE client {} : '{}'", clientId, ice);
                return (ice != null && !ice.isBlank()) ? ice : null;
            }
            return null;

        } catch (Exception e) {
            log.warn("Impossible de récupérer l'ICE du client {} : {}",
                    clientId, e.getMessage());
            return null;
        }
    }

    /**
     * Classe interne qui mappe uniquement les champs utiles
     * de ClientResponse (cabinet-service).
     * Pas besoin de tout mapper — juste l'ICE.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    public static class ClientResponseRemote {
        private String id;
        private String nomEntreprise;
        private String ice;
    }
}