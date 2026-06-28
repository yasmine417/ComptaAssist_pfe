package com.comptaassist.cabinet_service.service;

import com.comptaassist.cabinet_service.dto.AvancementDossierResponse;
import com.comptaassist.cabinet_service.entity.Client;
import com.comptaassist.cabinet_service.entity.Membre;
import com.comptaassist.cabinet_service.repository.ClientRepository;
import com.comptaassist.cabinet_service.repository.MembreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AvancementService {

    private final ClientRepository clientRepository;
    private final MembreRepository membreRepository;
    private final TrelloService trelloService;
    private final RestTemplate restTemplate =
            new RestTemplate();

    @Value("${facture.service.url:http://localhost:8086}")
    private String factureServiceUrl;

    @Value("${tva.service.url:http://localhost:8087}")
    private String tvaServiceUrl;

    public List<AvancementDossierResponse>
    getAvancementParCabinet(UUID cabinetId, String jwtToken) {

        List<Client> clients = clientRepository
                .findAllByCabinetIdAndActifTrue(cabinetId);

        return clients.stream()
                .map(c -> evaluerDossier(c, jwtToken))
                .collect(Collectors.toList());
    }

    public List<AvancementDossierResponse>
    getAvancementParComptable(
            UUID cabinetId, UUID comptableId, String jwtToken) {

        List<Client> clients = clientRepository
                .findAllByCabinetIdAndComptableIdAndActifTrue(
                        cabinetId, comptableId);

        return clients.stream()
                .map(c -> evaluerDossier(c, jwtToken))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private AvancementDossierResponse
    evaluerDossier(Client client, String jwtToken) {

        long facturesEnAttente = 0;
        long facturesTraitees  = 0;
        String comptableNom    = "Non assigné";

        if (client.getComptableId() != null) {
            Membre m = membreRepository
                    .findByUserId(client.getComptableId())
                    .orElse(null);
            if (m != null) {
                comptableNom = m.getPrenom() + " " + m.getNom();
            }
        }

        org.springframework.http.HttpHeaders headers =
                new org.springframework.http.HttpHeaders();
        if (jwtToken != null && !jwtToken.isBlank()) {
            headers.set("Authorization", "Bearer " + jwtToken);
        }
        org.springframework.http.HttpEntity<Void> entity =
                new org.springframework.http.HttpEntity<>(headers);

        // ── Factures ─────────────────────────────────────
        try {
            String url = factureServiceUrl
                    + "/api/factures-cpc/stats-avancement/"
                    + client.getId();
            org.springframework.http.ResponseEntity<Map> resp =
                    restTemplate.exchange(
                            url,
                            org.springframework.http.HttpMethod.GET,
                            entity, Map.class);
            Map<String, Object> stats = resp.getBody();

            if (stats != null) {
                facturesEnAttente = ((Number) stats
                        .getOrDefault("facturesEnAttente", 0))
                        .longValue();
                facturesTraitees = ((Number) stats
                        .getOrDefault("facturesTraitees", 0))
                        .longValue();
            }
        } catch (Exception e) {
            log.warn("Erreur factures client {}: {}",
                    client.getId(), e.getMessage());
        }

        // ── TVA ──────────────────────────────────────────
        String statutTva = "AUCUNE_OBLIGATION";
        try {
            String url = tvaServiceUrl
                    + "/api/tva/prochaine-periode?clientId="
                    + client.getId();
            org.springframework.http.ResponseEntity<Map> resp =
                    restTemplate.exchange(
                            url,
                            org.springframework.http.HttpMethod.GET,
                            entity, Map.class);
            Map<String, Object> periode = resp.getBody();

            if (periode != null) {
                boolean enRetard = Boolean.TRUE.equals(
                        periode.get("enRetard"));
                String statutExistant = (String) periode.get(
                        "declarationExistanteStatut");

                if (enRetard) {
                    statutTva = "EN_RETARD";
                } else if ("SOUMISE".equals(statutExistant)
                        || "VALIDEE".equals(statutExistant)) {
                    statutTva = "DECLAREE";
                } else {
                    statutTva = "A_VENIR";
                }
            }
        } catch (Exception e) {
            log.warn("Erreur TVA client {}: {}",
                    client.getId(), e.getMessage());
        }

        String statut;
        if ("EN_RETARD".equals(statutTva)
                || facturesEnAttente > 5) {
            statut = "EN RETARD";
        } else if (facturesEnAttente == 0
                && ("DECLAREE".equals(statutTva)
                || "AUCUNE_OBLIGATION".equals(statutTva))) {
            statut = "TERMINÉ";
        } else {
            statut = "EN COURS";
        }

        return AvancementDossierResponse.builder()
                .clientId(client.getId())
                .nomEntreprise(client.getNomEntreprise())
                .comptableNom(comptableNom)
                .statutCalcule(statut)
                .facturesEnAttente(facturesEnAttente)
                .facturesTraitees(facturesTraitees)
                .statutTva(statutTva)
                .build();
    }
}