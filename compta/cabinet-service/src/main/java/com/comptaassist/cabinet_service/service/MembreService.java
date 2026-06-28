package com.comptaassist.cabinet_service.service;

import com.comptaassist.cabinet_service.client.AuditClient;
import com.comptaassist.cabinet_service.dto.MembreRequest;
import com.comptaassist.cabinet_service.dto.MembreResponse;
import com.comptaassist.cabinet_service.entity.Membre;
import com.comptaassist.cabinet_service.exception.CabinetException;
import com.comptaassist.cabinet_service.repository.CabinetRepository;
import com.comptaassist.cabinet_service.repository.MembreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MembreService {

    private final MembreRepository  membreRepository;
    private final CabinetRepository cabinetRepository;
    private final AuditClient       auditClient;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Transactional
    public MembreResponse ajouter(UUID cabinetId,
                                  MembreRequest request,
                                  UUID directeurId) {

        cabinetRepository.findById(cabinetId)
                .filter(c -> c.getDirecteurId().equals(directeurId))
                .orElseThrow(() -> new CabinetException(
                        "Cabinet introuvable ou accès refusé"));

        if (membreRepository.existsByEmailAndCabinetId(
                request.getEmail(), cabinetId)) {
            throw new CabinetException(
                    "Ce comptable est déjà membre du cabinet");
        }

        UUID userId = creerCompteAuthService(
                request.getNom(),
                request.getPrenom(),
                request.getEmail(),
                cabinetId
        );

        Membre membre = Membre.builder()
                .cabinetId(cabinetId)
                .userId(userId)
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .email(request.getEmail())
                .role("COMPTABLE")
                .actif(true)
                .build();

        membre = membreRepository.save(membre);
        log.info("Comptable {} créé — EN_ATTENTE", membre.getEmail());

        auditClient.log(
                directeurId.toString(),
                "ROLE_DIRECTEUR",
                "AJOUTER_MEMBRE",
                "MEMBRE",
                membre.getId().toString(),
                "Ajout comptable : " + membre.getEmail()
                        + " au cabinet " + cabinetId
        );

        return toResponse(membre);
    }

    @SuppressWarnings("unchecked")
    private UUID creerCompteAuthService(String nom, String prenom,
                                        String email, UUID cabinetId) {
        try {
            String url = authServiceUrl + "/api/internal/users";
            String jsonBody = String.format(
                    "{\"nom\":\"%s\",\"prenom\":\"%s\"," +
                            "\"email\":\"%s\",\"cabinetId\":\"%s\"}",
                    nom, prenom, email, cabinetId.toString()
            );

            log.info("Appel : {} body : {}", url, jsonBody);

            java.net.URL urlObj = new java.net.URL(url);
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            log.info("Status réponse : {}", status);

            if (status != 200) {
                throw new CabinetException(
                        "Erreur auth-service status : " + status);
            }

            String responseStr;
            try (java.io.BufferedReader br =
                         new java.io.BufferedReader(
                                 new java.io.InputStreamReader(
                                         conn.getInputStream(),
                                         java.nio.charset.StandardCharsets.UTF_8
                                 ))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                responseStr = sb.toString();
            }

            log.info("Réponse : {}", responseStr);

            String id = responseStr
                    .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

            return UUID.fromString(id);

        } catch (CabinetException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur création compte : {}", e.getMessage());
            throw new CabinetException(
                    "Erreur création compte : " + e.getMessage());
        }
    }

    public List<MembreResponse> listerParCabinet(UUID cabinetId) {
        return membreRepository
                .findAllByCabinetId(cabinetId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public MembreResponse getById(UUID id) {
        return toResponse(
                membreRepository.findById(id)
                        .orElseThrow(() ->
                                new CabinetException("Membre introuvable")));
    }

    @Transactional
    public void desactiver(UUID id, UUID directeurId) {
        Membre membre = membreRepository.findById(id)
                .orElseThrow(() ->
                        new CabinetException("Membre introuvable"));

        cabinetRepository.findById(membre.getCabinetId())
                .filter(c -> c.getDirecteurId().equals(directeurId))
                .orElseThrow(() ->
                        new CabinetException("Accès refusé"));

        membre.setActif(false);
        membreRepository.save(membre);

        notifierAuthService("/api/internal/users/"
                + membre.getUserId() + "/desactiver", "PATCH");

        auditClient.log(
                directeurId.toString(),
                "ROLE_DIRECTEUR",
                "DESACTIVER_MEMBRE",
                "MEMBRE",
                id.toString(),
                "Désactivation membre : " + membre.getEmail()
        );

        log.info("Membre désactivé : {}", membre.getEmail());
    }

    @Transactional
    public void reactiver(UUID id, UUID directeurId) {
        Membre membre = membreRepository.findById(id)
                .orElseThrow(() ->
                        new CabinetException("Membre introuvable"));

        cabinetRepository.findById(membre.getCabinetId())
                .filter(c -> c.getDirecteurId().equals(directeurId))
                .orElseThrow(() ->
                        new CabinetException("Accès refusé"));

        membre.setActif(true);
        membreRepository.save(membre);

        notifierAuthService("/api/internal/users/"
                + membre.getUserId() + "/reactiver", "PATCH");

        auditClient.log(
                directeurId.toString(),
                "ROLE_DIRECTEUR",
                "REACTIVER_MEMBRE",
                "MEMBRE",
                id.toString(),
                "Réactivation membre : " + membre.getEmail()
        );

        log.info("Membre réactivé : {}", membre.getEmail());
    }

    @Transactional
    public void supprimer(UUID id, UUID directeurId) {
        Membre membre = membreRepository.findById(id)
                .orElseThrow(() ->
                        new CabinetException("Membre introuvable"));

        cabinetRepository.findById(membre.getCabinetId())
                .filter(c -> c.getDirecteurId().equals(directeurId))
                .orElseThrow(() ->
                        new CabinetException("Accès refusé"));

        notifierAuthService("/api/internal/users/"
                + membre.getUserId(), "DELETE");

        auditClient.log(
                directeurId.toString(),
                "ROLE_DIRECTEUR",
                "SUPPRIMER_MEMBRE",
                "MEMBRE",
                id.toString(),
                "Suppression membre : " + membre.getEmail()
        );

        membreRepository.delete(membre);
        log.info("Membre supprimé : {}", membre.getEmail());
    }

    // ── Helper HTTP interne ────────────────────────────────────
    private void notifierAuthService(String path, String method) {
        try {
            java.net.URL urlObj =
                    new java.net.URL(authServiceUrl + path);
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.connect();
            int status = conn.getResponseCode();
            log.info("Auth-service {} {} → status {}",
                    method, path, status);
        } catch (Exception e) {
            log.warn("Erreur appel auth-service {} : {}",
                    path, e.getMessage());
        }
    }

    private MembreResponse toResponse(Membre m) {
        return MembreResponse.builder()
                .id(m.getId())
                .cabinetId(m.getCabinetId())
                .userId(m.getUserId())
                .nom(m.getNom())
                .prenom(m.getPrenom())
                .email(m.getEmail())
                .role(m.getRole())
                .actif(m.isActif())
                .createdAt(m.getCreatedAt())
                .build();
    }
    public MembreResponse getByUserId(UUID userId) {
        Membre m = membreRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new RuntimeException("Membre introuvable"));
        return toResponse(m);
    }
}