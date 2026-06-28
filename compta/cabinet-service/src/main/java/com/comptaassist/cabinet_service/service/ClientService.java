package com.comptaassist.cabinet_service.service;

import com.comptaassist.cabinet_service.client.AuditClient;
import com.comptaassist.cabinet_service.dto.ClientRequest;
import com.comptaassist.cabinet_service.dto.ClientResponse;
import com.comptaassist.cabinet_service.entity.Client;
import com.comptaassist.cabinet_service.entity.Membre;
import com.comptaassist.cabinet_service.exception.CabinetException;
import com.comptaassist.cabinet_service.repository.CabinetRepository;
import com.comptaassist.cabinet_service.repository.ClientRepository;
import com.comptaassist.cabinet_service.repository.MembreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository  clientRepository;
    private final CabinetRepository cabinetRepository;
    private final AuditClient       auditClient;
    private final TrelloService trelloService;
    @Transactional
    public ClientResponse creer(UUID cabinetId,
                                ClientRequest request,
                                UUID directeurId) {
        cabinetRepository.findById(cabinetId)
                .filter(c -> c.getDirecteurId().equals(directeurId))
                .orElseThrow(() ->
                        new CabinetException(
                                "Cabinet introuvable ou accès refusé"));

        if (clientRepository.existsByNumeroFiscal(
                request.getNumeroFiscal())) {
            throw new CabinetException("Numéro fiscal déjà existant");
        }

        Client client = Client.builder()
                .cabinetId(cabinetId)
                .comptableId(request.getComptableId())
                .nomEntreprise(request.getNomEntreprise())
                .numeroFiscal(request.getNumeroFiscal())
                .ice(request.getIce())
                .email(request.getEmail())
                .telephone(request.getTelephone())
                .adresse(request.getAdresse())
                .secteur(request.getSecteur())
                .capitalSocial(request.getCapitalSocial())
                .actif(true)
                .build();

        Client saved = clientRepository.save(client);
        String cardId = trelloService.creerCarte(
                saved.getNomEntreprise(),
                saved.getId().toString());
        if (cardId != null) {
            saved.setTrelloCardId(cardId);
            saved = clientRepository.save(saved);
        }
        auditClient.log(
                directeurId.toString(),
                "ROLE_DIRECTEUR",
                "CREER_CLIENT",
                "CLIENT",
                saved.getId().toString(),
                "Création client : " + saved.getNomEntreprise()
        );

        return toResponse(saved);
    }

    public List<ClientResponse> listerParCabinet(UUID cabinetId) {
        return clientRepository
                .findAllByCabinetIdAndActifTrue(cabinetId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ClientResponse> listerParComptable(
            UUID cabinetId, UUID userId) {
        return clientRepository
                .findAllByCabinetIdAndComptableIdAndActifTrue(
                        cabinetId, userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ClientResponse getById(UUID id) {
        return toResponse(clientRepository.findById(id)
                .orElseThrow(() ->
                        new CabinetException("Client introuvable")));
    }

    @Transactional
    public ClientResponse modifier(UUID id,
                                   ClientRequest request,
                                   UUID directeurId) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() ->
                        new CabinetException("Client introuvable"));

        cabinetRepository.findById(client.getCabinetId())
                .filter(c -> c.getDirecteurId().equals(directeurId))
                .orElseThrow(() ->
                        new CabinetException("Accès refusé"));

        client.setNomEntreprise(request.getNomEntreprise());
        client.setIce(request.getIce());
        client.setEmail(request.getEmail());
        client.setTelephone(request.getTelephone());
        client.setAdresse(request.getAdresse());
        client.setSecteur(request.getSecteur());
        client.setComptableId(request.getComptableId());
        client.setCapitalSocial(request.getCapitalSocial());
        Client saved = clientRepository.save(client);

        auditClient.log(
                directeurId.toString(),
                "ROLE_DIRECTEUR",
                "MODIFIER_CLIENT",
                "CLIENT",
                saved.getId().toString(),
                "Modification client : " + saved.getNomEntreprise()
        );

        return toResponse(saved);
    }

    @Transactional
    public void desactiver(UUID id, UUID directeurId) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() ->
                        new CabinetException("Client introuvable"));

        cabinetRepository.findById(client.getCabinetId())
                .filter(c -> c.getDirecteurId().equals(directeurId))
                .orElseThrow(() ->
                        new CabinetException("Accès refusé"));

        client.setActif(false);
        clientRepository.save(client);

        auditClient.log(
                directeurId.toString(),
                "ROLE_DIRECTEUR",
                "DESACTIVER_CLIENT",
                "CLIENT",
                id.toString(),
                "Désactivation client : " + client.getNomEntreprise()
        );
    }

    private ClientResponse toResponse(Client c) {
        return ClientResponse.builder()
                .id(c.getId())
                .cabinetId(c.getCabinetId())
                .comptableId(c.getComptableId())
                .nomEntreprise(c.getNomEntreprise())
                .numeroFiscal(c.getNumeroFiscal())
                .ice(c.getIce())
                .email(c.getEmail())
                .telephone(c.getTelephone())
                .capitalSocial(c.getCapitalSocial())
                .adresse(c.getAdresse())
                .secteur(c.getSecteur())
                .actif(c.isActif())
                .createdAt(c.getCreatedAt())
                .build();
    }



    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${facture.service.url:http://localhost:8086}")
    private String factureServiceUrl;

    private final MembreRepository membreRepository;  // ← ajoute cette dépendance

    @SuppressWarnings("unchecked")
    public Map<String, Object> getDetailComplet(
            UUID cabinetId, UUID clientId, String jwtToken) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() ->
                        new CabinetException(
                                "Client introuvable"));

        Map<String, Object> result = new HashMap<>();
        result.put("client", toResponse(client));

        if (client.getComptableId() != null) {
            try {
                Membre m = membreRepository
                        .findByUserId(client.getComptableId())
                        .orElse(null);
                if (m != null) {
                    result.put("comptableNom",
                            m.getPrenom() + " " + m.getNom());
                    result.put("comptableId",
                            client.getComptableId().toString());
                } else {
                    result.put("comptableNom", "Non assigné");
                }
            } catch (Exception e) {
                result.put("comptableNom", "Non assigné");
            }
        } else {
            result.put("comptableNom", "Non assigné");
        }

        org.springframework.http.HttpHeaders headers =
                new org.springframework.http.HttpHeaders();
        if (jwtToken != null && !jwtToken.isBlank()) {
            headers.set("Authorization", "Bearer " + jwtToken);
        }
        org.springframework.http.HttpEntity<Void> entity =
                new org.springframework.http.HttpEntity<>(headers);

        // ── Factures ────────────────────────────────────────
        try {
            String url = factureServiceUrl
                    + "/api/factures-cpc/par-client/" + clientId;
            ResponseEntity<List> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET,
                    entity, List.class);
            result.put("factures",
                    response.getBody() != null
                            ? response.getBody() : new ArrayList<>());
        } catch (Exception e) {
            log.error("Erreur factures: {}", e.getMessage());
            result.put("factures", new ArrayList<>());
        }

        // ── Écritures comptables ────────────────────────────
        try {
            String url = factureServiceUrl
                    + "/api/ecritures/client/" + clientId;
            ResponseEntity<List> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET,
                    entity, List.class);
            result.put("ecritures",
                    response.getBody() != null
                            ? response.getBody() : new ArrayList<>());
        } catch (Exception e) {
            log.error("Erreur écritures: {}", e.getMessage());
            result.put("ecritures", new ArrayList<>());
        }

        // ── Déclarations TVA ─────────────────────────────────
        try {
            String url = "http://localhost:8087/api/tva/client/"
                    + clientId;
            ResponseEntity<List> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET,
                    entity, List.class);
            result.put("declarationsTva",
                    response.getBody() != null
                            ? response.getBody() : new ArrayList<>());
        } catch (Exception e) {
            log.error("Erreur TVA: {}", e.getMessage());
            result.put("declarationsTva", new ArrayList<>());
        }

        return result;
    }
}