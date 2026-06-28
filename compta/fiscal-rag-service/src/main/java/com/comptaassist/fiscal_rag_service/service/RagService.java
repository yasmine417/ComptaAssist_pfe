package com.comptaassist.fiscal_rag_service.service;

import com.comptaassist.fiscal_rag_service.config.JwtService;
import com.comptaassist.fiscal_rag_service.dto.ConversationRAGDto;
import com.comptaassist.fiscal_rag_service.dto.DocumentIndexeDto;
import com.comptaassist.fiscal_rag_service.dto.ReponseRAG;
import com.comptaassist.fiscal_rag_service.entity.DocumentIndexe;
import com.comptaassist.fiscal_rag_service.entity.ConversationRAG;
import com.comptaassist.fiscal_rag_service.repository.ConversationRAGRepository;
import com.comptaassist.fiscal_rag_service.repository.DocumentIndexeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class RagService {

    @Value("${python.rag.url}")
    private String pythonRagUrl;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${n8n.system.token:}")
    private String systemToken;

    private final RestTemplate             restTemplate = new RestTemplate();
    private final DocumentIndexeRepository documentRepo;
    private final ConversationRAGRepository conversationRepo;
    private final JwtService               jwtService;

    public RagService(DocumentIndexeRepository documentRepo,
                      ConversationRAGRepository conversationRepo,
                      JwtService jwtService) {
        this.documentRepo     = documentRepo;
        this.conversationRepo = conversationRepo;
        this.jwtService       = jwtService;
    }

    // ══════════════════════════════════════════════════════════
    // POSER UNE QUESTION (lois fiscales)
    // ══════════════════════════════════════════════════════════

    public ReponseRAG poserQuestion(String question, String comptableId) {
        log.info("Question RAG envoyée par {}", comptableId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            String body = "{\"question\": \""
                    + question
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    + "\"}";

            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    pythonRagUrl + "/api/chat",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> resp = response.getBody();
            if (resp == null) return reponseErreur("Réponse null");

            Object sources  = resp.get("sources");
            Object extraits = resp.get("extraits");
            Object nbExtr   = resp.get("nb_extraits");

            List<String> sourcesList = sources instanceof List
                    ? (List<String>) sources : List.of();
            int nb = nbExtr instanceof Number
                    ? ((Number) nbExtr).intValue() : 0;
            String reponseText = String.valueOf(resp.getOrDefault("reponse", ""));

            try {
                conversationRepo.save(ConversationRAG.builder()
                        .comptableId(comptableId)
                        .question(question)
                        .reponse(reponseText)
                        .sources(sourcesList)
                        .nbExtraits(nb)
                        .poseeA(LocalDateTime.now())
                        .build());
                log.info("✅ Conversation sauvegardée pour {}", comptableId);
            } catch (Exception ex) {
                log.error("❌ Erreur sauvegarde conversation : {}", ex.getMessage());
            }

            return ReponseRAG.builder()
                    .reponse(reponseText)
                    .sources(sourcesList)
                    .nbExtraits(nb)
                    .extraits(extraits instanceof List
                            ? (List<String>) extraits : List.of())
                    .build();

        } catch (Exception e) {
            log.error("Erreur RAG : {}", e.getMessage());
            return reponseErreur(e.getMessage() != null
                    ? e.getMessage() : "Erreur inconnue");
        }
    }

    // ══════════════════════════════════════════════════════════
    // POSER UNE QUESTION SUR UN CLIENT
    // ══════════════════════════════════════════════════════════

    public ReponseRAG poserQuestionClient(
            String question,
            String comptableId,
            UUID clientId,
            String mois,
            String jwtToken) {
        try {
            // ── Récupérer les données du client ───────────────
            Map<String, Object> donnees = new HashMap<>();
            try {
                // Récupérer d'abord le cabinetId depuis les factures
                String cabinetId = recupererCabinetId(comptableId, jwtToken);

                if (cabinetId != null) {
                    String url = "http://localhost:8086/api/rapport/mensuel"
                            + "?clientId=" + clientId
                            + "&cabinetId=" + cabinetId
                            + "&mois=" + mois;

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + jwtToken);

                    ResponseEntity<Map> rapportResp = restTemplate.exchange(
                            url, HttpMethod.GET,
                            new HttpEntity<>(headers), Map.class);

                    if (rapportResp.getBody() != null) {
                        Map<String, Object> rapport = rapportResp.getBody();
                        Map<String, Object> resume  = (Map<String, Object>)
                                rapport.getOrDefault("resume", Map.of());
                        Map<String, Object> tresor  = (Map<String, Object>)
                                rapport.getOrDefault("tresorerie", Map.of());

                        donnees.put("nomEntreprise",   rapport.get("nomEntreprise"));
                        donnees.put("periode",         rapport.get("moisLabel"));
                        donnees.put("chiffreAffaires", resume.get("chiffreAffaires"));
                        donnees.put("totalDepenses",   resume.get("totalDepenses"));
                        donnees.put("benefice",        resume.get("benefice"));
                        donnees.put("tvaCollectee",    resume.get("tvaCollectee"));
                        donnees.put("tvaRecuperable",  resume.get("tvaRecuperable"));
                        donnees.put("tvaAPayer",       resume.get("tvaAPayer"));
                        donnees.put("nbEmises",        resume.get("nbEmises"));
                        donnees.put("nbPayees",        resume.get("nbPayees"));
                        donnees.put("nbImpayes",       resume.get("nbImpayes"));
                        donnees.put("resteAPayer",     resume.get("resteAPayer"));
                        donnees.put("soldeBanque",     tresor.get("soldeBanque"));
                    }
                }
            } catch (Exception e) {
                log.warn("Impossible de récupérer données client : {}", e.getMessage());
                donnees.put("erreur", "Données client non disponibles");
            }

            // ── Appel Python ──────────────────────────────────
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("question",       question);
            body.put("donnees_client", donnees);

            ResponseEntity<Map> response = restTemplate.exchange(
                    pythonRagUrl + "/api/chat-client",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<String, Object> resp = response.getBody();
            if (resp == null) return reponseErreur("Réponse null");

            Object sources  = resp.get("sources");
            Object extraits = resp.get("extraits");
            Object nbExtr   = resp.get("nb_extraits");
            String reponseText = String.valueOf(resp.getOrDefault("reponse", ""));

            try {
                conversationRepo.save(ConversationRAG.builder()
                        .comptableId(comptableId)
                        .question("[Client] " + question)
                        .reponse(reponseText)
                        .sources(sources instanceof List
                                ? (List<String>) sources : List.of())
                        .nbExtraits(nbExtr instanceof Number
                                ? ((Number) nbExtr).intValue() : 0)
                        .poseeA(LocalDateTime.now())
                        .build());
            } catch (Exception ignored) {}

            return ReponseRAG.builder()
                    .reponse(reponseText)
                    .sources(sources instanceof List
                            ? (List<String>) sources : List.of())
                    .nbExtraits(nbExtr instanceof Number
                            ? ((Number) nbExtr).intValue() : 0)
                    .extraits(extraits instanceof List
                            ? (List<String>) extraits : List.of())
                    .build();

        } catch (Exception e) {
            log.error("Erreur question client : {}", e.getMessage());
            return reponseErreur(e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // HISTORIQUE CONVERSATIONS
    // ══════════════════════════════════════════════════════════

    public List<ConversationRAGDto> getHistoriqueConversations(String comptableId) {
        return conversationRepo
                .findByComptableIdOrderByPoseeADesc(comptableId)
                .stream()
                .map(c -> ConversationRAGDto.builder()
                        .id(c.getId())
                        .question(c.getQuestion())
                        .reponse(c.getReponse())
                        .sources(c.getSources())
                        .nbExtraits(c.getNbExtraits())
                        .poseeA(c.getPoseeA())
                        .build())
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    // INDEXER DEPUIS CHEMIN
    // ══════════════════════════════════════════════════════════

    public Map<String, Object> indexerDocument(
            String cheminPdf, String nomDocument) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = "{\"chemin_pdf\": \""
                    + cheminPdf.replace("\\", "\\\\")
                    + "\", \"nom_document\": \""
                    + nomDocument + "\"}";

            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    pythonRagUrl + "/api/indexer",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            return response.getBody() != null
                    ? response.getBody()
                    : Map.of("statut", "erreur");

        } catch (Exception e) {
            log.error("Erreur indexation : {}", e.getMessage());
            return Map.of("statut", "erreur", "message", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // UPLOAD ET INDEXER
    // ══════════════════════════════════════════════════════════

    public Map<String, Object> uploadEtIndexer(
            MultipartFile fichier,
            String nomDocument,
            boolean forcer,
            String adminId,
            String adminEmail) {
        try {
            ByteArrayResource resource =
                    new ByteArrayResource(fichier.getBytes()) {
                        @Override
                        public String getFilename() {
                            return fichier.getOriginalFilename();
                        }
                    };

            MultiValueMap<String, Object> body =
                    new LinkedMultiValueMap<>();
            body.add("fichier",      resource);
            body.add("nom_document", nomDocument);
            body.add("forcer",       String.valueOf(forcer));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ResponseEntity<Map> response = restTemplate.exchange(
                    pythonRagUrl + "/api/upload-indexer",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<String, Object> result = response.getBody() != null
                    ? response.getBody()
                    : Map.of("statut", "erreur");

            log.info("Réponse Python reçue : {}", result);

            String statut  = String.valueOf(result.get("statut"));
            String message = String.valueOf(result.getOrDefault("message", ""));

            log.info("statut={} message={}", statut, message);

            Integer nbMorceaux = null;
            try {
                Object nbObj = result.get("nb_morceaux");
                if (nbObj != null) {
                    nbMorceaux = Integer.parseInt(nbObj.toString());
                } else {
                    Matcher m = Pattern.compile("(\\d+)\\s+morceaux")
                            .matcher(message);
                    if (m.find()) nbMorceaux = Integer.parseInt(m.group(1));
                }
            } catch (Exception ignored) {}

            String statutNorm;
            if (statut.equalsIgnoreCase("ok"))               statutNorm = "OK";
            else if (statut.equalsIgnoreCase("deja_indexe")) statutNorm = "DEJA_INDEXE";
            else                                             statutNorm = "ERREUR";

            try {
                Optional<DocumentIndexe> existant =
                        documentRepo.findByNomDocument(nomDocument);

                if (existant.isPresent()) {
                    DocumentIndexe doc = existant.get();
                    doc.setStatut(statutNorm);
                    doc.setMessage(message);
                    doc.setNbMorceaux(nbMorceaux);
                    doc.setNomFichier(fichier.getOriginalFilename());
                    doc.setTailleFichier(fichier.getSize());
                    doc.setReindexeA(LocalDateTime.now());
                    documentRepo.save(doc);
                    log.info("✅ Historique mis à jour : {} statut={}", nomDocument, statutNorm);
                } else {
                    documentRepo.save(DocumentIndexe.builder()
                            .nomDocument(nomDocument)
                            .nomFichier(fichier.getOriginalFilename())
                            .tailleFichier(fichier.getSize())
                            .statut(statutNorm)
                            .message(message)
                            .nbMorceaux(nbMorceaux)
                            .adminId(adminId)
                            .adminEmail(adminEmail)
                            .indexeA(LocalDateTime.now())
                            .build());
                    log.info("✅ Historique créé : {} statut={}", nomDocument, statutNorm);
                }
            } catch (Exception ex) {
                log.error("❌ Erreur sauvegarde historique : {}", ex.getMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("Erreur upload indexation : {}", e.getMessage());
            try {
                documentRepo.save(DocumentIndexe.builder()
                        .nomDocument(nomDocument)
                        .nomFichier(fichier.getOriginalFilename())
                        .tailleFichier(fichier.getSize())
                        .statut("ERREUR")
                        .message(e.getMessage())
                        .adminId(adminId)
                        .adminEmail(adminEmail)
                        .indexeA(LocalDateTime.now())
                        .build());
            } catch (Exception ignored) {}
            return Map.of("statut", "erreur", "message", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // STATUT
    // ══════════════════════════════════════════════════════════

    public Map<String, Object> getStatut() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    pythonRagUrl + "/api/statut", Map.class);
            return response.getBody() != null
                    ? response.getBody()
                    : Map.of("statut", "indisponible");
        } catch (Exception e) {
            return Map.of("statut", "indisponible", "message", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // HISTORIQUE DOCUMENTS
    // ══════════════════════════════════════════════════════════

    public List<DocumentIndexeDto> getHistorique() {
        return documentRepo.findAllByOrderByIndexeADesc()
                .stream()
                .map(d -> DocumentIndexeDto.builder()
                        .id(d.getId())
                        .nomDocument(d.getNomDocument())
                        .nomFichier(d.getNomFichier())
                        .tailleFichier(d.getTailleFichier())
                        .statut(d.getStatut())
                        .message(d.getMessage())
                        .nbMorceaux(d.getNbMorceaux())
                        .adminEmail(d.getAdminEmail())
                        .indexeA(d.getIndexeA())
                        .reindexeA(d.getReindexeA())
                        .build())
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════

    private String recupererCabinetId(String comptableId, String jwtToken) {
        try {
            String url = "http://localhost:8086/api/factures-cpc/mes-factures";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            ResponseEntity<List> resp = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);
            if (resp.getBody() != null && !resp.getBody().isEmpty()) {
                Map<String, Object> facture =
                        (Map<String, Object>) resp.getBody().get(0);
                Object cabinetId = facture.get("cabinetId");
                return cabinetId != null ? cabinetId.toString() : null;
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer cabinetId : {}", e.getMessage());
        }
        return null;
    }

    private ReponseRAG reponseErreur(String message) {
        return ReponseRAG.builder()
                .reponse("❌ " + message)
                .sources(List.of())
                .nbExtraits(0)
                .extraits(List.of())
                .build();
    }
}