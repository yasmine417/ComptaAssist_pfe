package com.comptaassist.cabinet_service.controller;

import com.comptaassist.cabinet_service.entity.Client;
import com.comptaassist.cabinet_service.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Endpoints réservés au compte système n8n.
 * Utilisés UNIQUEMENT pour les automatisations.
 */
@RestController
@RequestMapping("/api/n8n")
@RequiredArgsConstructor
@Slf4j
public class N8nController {

    private final ClientRepository clientRepo;
    private final RestTemplate     restTemplate = new RestTemplate();

    // URL de auth-service pour récupérer un user par id
    private static final String AUTH_SERVICE_URL =
            "http://localhost:8081/api/users/";

    /**
     * Retourne tous les clients actifs de tous les cabinets.
     * Pour chaque client, récupère l'email du comptable
     * assigné via un appel REST vers auth-service.
     *
     * n8n appelle cet endpoint pour savoir
     * pour quels clients générer un rapport.
     */
    @GetMapping("/clients-actifs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> clientsActifs(
            @RequestHeader("Authorization") String authHeader) {

        List<Client> clients = clientRepo.findByActifTrue();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Client c : clients) {

            // Récupérer l'email du comptable via auth-service
            String comptableEmail = null;
            if (c.getComptableId() != null) {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", authHeader);

                    ResponseEntity<Map> resp = restTemplate.exchange(
                            AUTH_SERVICE_URL + c.getComptableId(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            Map.class
                    );

                    if (resp.getBody() != null) {
                        comptableEmail = (String) resp.getBody().get("email");
                        log.info("Email comptable récupéré pour client={} : {}",
                                c.getNomEntreprise(), comptableEmail);
                    }
                } catch (Exception e) {
                    log.warn("Impossible de récupérer email comptable {} : {}",
                            c.getComptableId(), e.getMessage());
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("clientId",       c.getId().toString());
            item.put("nomEntreprise",  c.getNomEntreprise());
            item.put("email",          c.getEmail());
            item.put("ice",            c.getIce());
            item.put("cabinetId",      c.getCabinetId().toString());
            item.put("comptableId",    c.getComptableId() != null
                    ? c.getComptableId().toString() : null);
            item.put("comptableEmail", comptableEmail);
            result.add(item);
        }

        return ResponseEntity.ok(result);
    }
}