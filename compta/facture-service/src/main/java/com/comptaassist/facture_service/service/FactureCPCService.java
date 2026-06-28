package com.comptaassist.facture_service.service;

import com.comptaassist.facture_service.client.AuditClient;
import com.comptaassist.facture_service.client.CabinetServiceClient;
import com.comptaassist.facture_service.dto.FactureCPCResponse;
import com.comptaassist.facture_service.entity.EcritureComptable;
import com.comptaassist.facture_service.service.CpcService;
import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.entity.FactureCPC.StatutFacture;
import com.comptaassist.facture_service.entity.LienClientUpload;
import com.comptaassist.facture_service.repository.EcritureComptableRepository;
import com.comptaassist.facture_service.repository.FactureCPCRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FactureCPCService {

    private final FactureCPCRepository       repo;
    private final EcritureComptableRepository ecritureRepo;
    private final LienClientUploadService    lienService;
    private final MinioStorageService        minioStorage;
    private final JournalComptableService    journalService;
    private final CpcService                 cpcService;
    private final CabinetServiceClient       cabinetClient;
    private final ObjectMapper               mapper = new ObjectMapper();
    private final AuditClient auditClient;
    @Value("${ocr.service.url:http://localhost:8002}")
    private String ocrUrl;

    // ══════════════════════════════════════════════════════════
    // DÉTECTION VENTE / ACHAT PAR ICE
    // ══════════════════════════════════════════════════════════

    private String detectedSensParIce(
            Map<String, Object> ocr,
            UUID clientId,
            UUID cabinetId,
            String jwtToken) {

        String iceClientBd = cabinetClient.getIceClient(clientId, cabinetId, jwtToken);

        if (iceClientBd == null) {
            log.debug("ICE non renseigné pour client {} → LLM décide", clientId);
            return null;
        }

        String iceBdNorm       = normaliserIce(iceClientBd);
        String iceEmetteur     = normaliserIce(str(ocr, "ice"));
        String iceDestinataire = normaliserIce(str(ocr, "ice_client"));

        log.info("Détection ICE : bd='{}' emetteur='{}' destinataire='{}'",
                iceBdNorm, iceEmetteur, iceDestinataire);

        if (!iceEmetteur.isEmpty() && iceEmetteur.equals(iceBdNorm)) {
            log.info("✅ VENTE : ICE émetteur {} == client BD", iceBdNorm);
            return "VENTE";
        }
        if (!iceDestinataire.isEmpty() && iceDestinataire.equals(iceBdNorm)) {
            log.info("✅ ACHAT : ICE destinataire {} == client BD", iceBdNorm);
            return "ACHAT";
        }

        String fournisseurTexte = str(ocr, "fournisseur").toLowerCase();
        String clientTexte      = str(ocr, "client").toLowerCase();

        if (clientTexte.contains(iceBdNorm) || clientTexte.contains(iceClientBd)) {
            log.info("✅ ACHAT : ICE BD trouvé dans texte client '{}'", clientTexte);
            return "ACHAT";
        }
        if (fournisseurTexte.contains(iceBdNorm) || fournisseurTexte.contains(iceClientBd)) {
            log.info("✅ VENTE : ICE BD trouvé dans texte fournisseur '{}'", fournisseurTexte);
            return "VENTE";
        }
        if (!iceEmetteur.isEmpty() && iceEmetteur.equals(iceBdNorm)) {
            // Vérifier que l'ICE destinataire est DIFFÉRENT
            // sinon c'est ambigu → faire confiance au LLM
            if (!iceDestinataire.isEmpty() && iceDestinataire.equals(iceBdNorm)) {
                log.warn("ICE émetteur ET destinataire identiques → ambigu, LLM décide");
                return null;
            }
            log.info("✅ VENTE : ICE émetteur {} == client BD", iceBdNorm);
            return "VENTE";
        }
        log.warn("ICE BD '{}' introuvable dans la facture → LLM décide", iceBdNorm);
        return null;
    }

    private String normaliserIce(String ice) {
        if (ice == null) return "";
        return ice.replaceAll("[^0-9]", "").trim();
    }

    // ══════════════════════════════════════════════════════════
    // ANALYSER ET SAUVEGARDER
    // ══════════════════════════════════════════════════════════

    @Transactional
    public FactureCPCResponse analyserEtSauvegarder(
            MultipartFile fichier,
            UUID clientId, UUID cabinetId,
            UUID comptableId, UUID documentId,
            String minioObject,
            String jwtToken) {

        // ── Lire les bytes UNE SEULE FOIS ──────────────────
        final byte[] fichierBytes;
        final String nomOriginal = fichier.getOriginalFilename();
        final String contentType = fichier.getContentType();
        try {
            fichierBytes = fichier.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Impossible de lire le fichier : " + e.getMessage());
        }

        Map<String, Object> ocrResult = appelOcrService(fromBytes(fichierBytes, nomOriginal, contentType));

        String sensBd = detectedSensParIce(ocrResult, clientId, cabinetId, jwtToken);

        if (sensBd != null) {
            String opLlm     = str(ocrResult, "type_operation").toUpperCase();
            boolean llmVente = opLlm.startsWith("VENTE_");
            boolean bdVente  = "VENTE".equals(sensBd);

            if (llmVente != bdVente) {
                log.warn("Sens corrigé : LLM={} → BD={} — rappel OCR", opLlm, sensBd);
                Map<String, Object> ocrCorrige = appelOcrServiceAvecSens(
                        fromBytes(fichierBytes, nomOriginal, contentType),
                        bdVente ? "VENTE_SERVICE" : deduireTypeAchat(ocrResult));
                if (ocrCorrige != null && !ocrCorrige.isEmpty()) {
                    ocrResult = ocrCorrige;
                    log.info("✅ OCR corrigé appliqué : op={}", str(ocrResult, "type_operation"));
                } else {
                    log.warn("⚠️ OCR corrigé vide — forçage manuel du sens dans ocrResult");
                    ocrResult.put("type_operation",
                            bdVente ? "VENTE_SERVICE" : deduireTypeAchat(ocrResult));
                }
            }
        }

        FactureCPC facture = construireFacture(
                ocrResult, clientId, cabinetId,
                comptableId, documentId, minioObject,
                nomOriginal);
        facture = repo.save(facture);

        try {
            Map<String, Object> donneesOcr = new HashMap<>();
            if (facture.getItemsJson() != null)
                donneesOcr.put("items", mapper.readValue(
                        facture.getItemsJson(),
                        new TypeReference<List<Map<String, Object>>>() {}));
            journalService.genererEcrituresFacture(facture, donneesOcr);
            log.info("✅ Écritures sauvegardées en BD pour facture {}", facture.getId());
        } catch (Exception e) {
            log.warn("⚠️ Écritures non sauvegardées en BD : {}", e.getMessage());
        }

        try {
            _regenererCpcDepuisJson(facture);
            facture = repo.save(facture);
        } catch (Exception e) {
            log.warn("CPC non calculé : {}", e.getMessage());
        }
        auditClient.log(
                comptableId.toString(),
                "ROLE_COMPTABLE",
                "CREER_FACTURE",
                "FACTURE",
                facture.getId().toString(),
                "Création facture : " + facture.getNumeroFacture()
                        + " | " + facture.getTypeOperation()
                        + " | client : " + clientId
        );
        return toResponse(facture);
    }

    @Transactional
    public Map<String, Object> analyserDepuisToken(
            String token, MultipartFile fichier,
            String jwtToken) {

        LienClientUpload lien = lienService.validerToken(token);

        // ── Lire les bytes UNE SEULE FOIS avant minioStorage ──
        final byte[] fichierBytes;
        final String nomOriginal = fichier.getOriginalFilename();
        final String contentType = fichier.getContentType();
        try {
            fichierBytes = fichier.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Impossible de lire le fichier : " + e.getMessage());
        }

        // ── Sauvegarder dans MinIO depuis les bytes ────────
        String minioObj = minioStorage.sauvegarderBytes(fichierBytes, nomOriginal);

        Map<String, Object> ocrResult = appelOcrService(fromBytes(fichierBytes, nomOriginal, contentType));

        String sensBd = detectedSensParIce(
                ocrResult, lien.getClientId(), lien.getCabinetId(), jwtToken);

        if (sensBd != null) {
            String opLlm     = str(ocrResult, "type_operation").toUpperCase();
            boolean llmVente = opLlm.startsWith("VENTE_");
            boolean bdVente  = "VENTE".equals(sensBd);

            if (llmVente != bdVente) {
                log.warn("Token — sens corrigé : LLM={} → BD={}", opLlm, sensBd);
                Map<String, Object> ocrCorrige = appelOcrServiceAvecSens(
                        fromBytes(fichierBytes, nomOriginal, contentType),
                        bdVente ? "VENTE_SERVICE" : deduireTypeAchat(ocrResult));
                if (ocrCorrige != null && !ocrCorrige.isEmpty()) {
                    ocrResult = ocrCorrige;
                    log.info("✅ OCR corrigé appliqué : op={}", str(ocrResult, "type_operation"));
                } else {
                    log.warn("⚠️ OCR corrigé vide — forçage manuel du sens dans ocrResult");
                    ocrResult.put("type_operation",
                            bdVente ? "VENTE_SERVICE" : deduireTypeAchat(ocrResult));
                }
            }
        }

        FactureCPC facture = construireFacture(
                ocrResult, lien.getClientId(), lien.getCabinetId(),
                lien.getComptableId(), null, minioObj,
                nomOriginal);
        facture = repo.save(facture);

        try {
            Map<String, Object> donneesOcr = new HashMap<>();
            if (facture.getItemsJson() != null)
                donneesOcr.put("items", mapper.readValue(
                        facture.getItemsJson(),
                        new TypeReference<List<Map<String, Object>>>() {}));
            journalService.genererEcrituresFacture(facture, donneesOcr);
        } catch (Exception e) {
            log.warn("⚠️ Écritures token non sauvegardées : {}", e.getMessage());
        }

        try {
            _regenererCpcDepuisJson(facture);
            facture = repo.save(facture);
        } catch (Exception e) {
            log.warn("CPC token non calculé : {}", e.getMessage());
        }

        lienService.incrementerCompteur(token);
        auditClient.log(
                lien.getComptableId().toString(),
                "ROLE_COMPTABLE",
                "CREER_FACTURE",
                "FACTURE",
                facture.getId().toString(),
                "Création facture via token : " + facture.getNumeroFacture()
                        + " | client : " + lien.getClientId()
        );
        return Map.of(
                "success",   true,
                "message",   "Facture uploadée avec succès",
                "factureId", facture.getId().toString(),
                "sens",      facture.getTypeOperation() != null
                        && facture.getTypeOperation().startsWith("VENTE")
                        ? "VENTE" : "ACHAT");
    }

// ══════════════════════════════════════════════════════════
// HELPER — MultipartFile depuis bytes (sans Mock)
// ══════════════════════════════════════════════════════════

    private MultipartFile fromBytes(byte[] bytes, String nom, String type) {
        return new MultipartFile() {
            @Override public String getName()             { return "fichier"; }
            @Override public String getOriginalFilename() { return nom; }
            @Override public String getContentType()      { return type; }
            @Override public boolean isEmpty()            { return bytes.length == 0; }
            @Override public long getSize()               { return bytes.length; }
            @Override public byte[] getBytes()            { return bytes; }
            @Override public java.io.InputStream getInputStream() {
                return new java.io.ByteArrayInputStream(bytes);
            }
            @Override public void transferTo(java.io.File dest)
                    throws java.io.IOException, IllegalStateException {
                try (var out = new java.io.FileOutputStream(dest)) {
                    out.write(bytes);
                }
            }
        };
    }















    // ══════════════════════════════════════════════════════════
    // MODIFIER ÉCRITURE — avec régénération JPA
    // ══════════════════════════════════════════════════════════

    @Transactional
    public FactureCPCResponse modifierEcriture(UUID id, Map<String, Object> body) {
        FactureCPC f = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Facture introuvable"));

        try {
            Object ecritures = body.get("ecritureComptable");
            if (ecritures != null)
                f.setEcritureComptableJson(mapper.writeValueAsString(ecritures));
            if (body.containsKey("montantHt"))    f.setMontantHt(dbl(body, "montantHt"));
            if (body.containsKey("montantTva"))   f.setMontantTva(dbl(body, "montantTva"));
            if (body.containsKey("montantTtc"))   f.setMontantTtc(dbl(body, "montantTtc"));
            if (body.containsKey("compteCharge")) f.setCompteCharge(str(body, "compteCharge"));
            if (body.containsKey("compteTiers"))  f.setCompteTiers(str(body, "compteTiers"));
            if (body.containsKey("cpc")) {
                try {
                    Object cpcObj = body.get("cpc");
                    f.setCpcJson(mapper.writeValueAsString(cpcObj));
                } catch (Exception e) {
                    throw new RuntimeException("Erreur modification CPC");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur modification écriture");
        }

        FactureCPC saved = repo.save(f);

        // Régénérer les écritures JPA si demandé
        Boolean regenerer = (Boolean) body.get("regenererEcrituresComptables");
        if (Boolean.TRUE.equals(regenerer)) {
            _regenererEcrituresJpa(saved);
        }

        // Régénérer le CPC depuis le JSON modifié
        try {
            _regenererCpcDepuisJson(saved);
            saved = repo.save(saved);
        } catch (Exception e) {
            log.warn("CPC non régénéré après modification : {}", e.getMessage());
        }
        auditClient.log(
                saved.getComptableId() != null
                        ? saved.getComptableId().toString() : "",
                "ROLE_COMPTABLE",
                "MODIFIER_ECRITURE_FACTURE",
                "FACTURE",
                id.toString(),
                "Modification écriture facture : "
                        + saved.getNumeroFacture()
        );
        return toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════
    // RÉGÉNÉRER ÉCRITURES JPA DEPUIS JSON MODIFIÉ
    // ══════════════════════════════════════════════════════════


    private void _regenererEcrituresJpa(FactureCPC f) {
        try {
            // Supprimer les anciennes écritures JPA
            List<EcritureComptable> anciennes =
                    ecritureRepo.findByFactureIdOrderByNumLigne(f.getId());
            ecritureRepo.deleteAll(anciennes);

            // Relire le JSON modifié
            List<Map<String, Object>> lignesJson = mapper.readValue(
                    f.getEcritureComptableJson() != null
                            ? f.getEcritureComptableJson() : "[]",
                    new TypeReference<>() {});

            // Recréer les EcritureComptable depuis le JSON
            List<EcritureComptable> nouvelles = new ArrayList<>();
            int numLigne = 1;

            for (Map<String, Object> ligne : lignesJson) {
                String compte  = str(ligne, "compte");
                String libelle = str(ligne, "libelle");
                double debit   = ligne.get("debit")  != null
                        ? ((Number) ligne.get("debit")).doubleValue()  : 0.0;
                double credit  = ligne.get("credit") != null
                        ? ((Number) ligne.get("credit")).doubleValue() : 0.0;

                EcritureComptable e = EcritureComptable.builder()
                        .cabinetId(f.getCabinetId())
                        .clientId(f.getClientId())
                        .comptableId(f.getComptableId())
                        .factureId(f.getId())
                        .journal(f.getJournal() != null ? f.getJournal()
                                : (_estVente(f) ? "VE" : "AC"))
                        .dateEcriture(f.getDateFacture() != null
                                ? f.getDateFacture() : LocalDate.now())
                        .exercice(f.getDateFacture() != null
                                ? String.valueOf(f.getDateFacture().getYear())
                                : String.valueOf(LocalDate.now().getYear()))
                        .referencePiece(f.getNumeroFacture())
                        .typePiece(_estVente(f) ? "FACTURE_VENTE" : "FACTURE_ACHAT")
                        .numLigne(numLigne++)
                        .compte(compte)
                        .intituleCompte(libelle)
                        .libelle(libelle)
                        .debit(java.math.BigDecimal.valueOf(debit))
                        .credit(java.math.BigDecimal.valueOf(credit))
                        .devise(f.getDevise() != null ? f.getDevise() : "MAD")
                        .tiersNom(_estVente(f) ? f.getClient() : f.getFournisseur())
                        .tiersIce(f.getIce())
                        .statutEcriture(EcritureComptable.StatutEcriture.VALIDEE)
                        .build();

                nouvelles.add(e);
            }

            ecritureRepo.saveAll(nouvelles);
            log.info("✅ {} écritures JPA régénérées pour facture {}",
                    nouvelles.size(), f.getId());

        } catch (Exception ex) {
            log.error("Erreur régénération écritures JPA : {}", ex.getMessage());
            throw new RuntimeException("Erreur régénération écritures : " + ex.getMessage());
        }
    }

    private boolean _estVente(FactureCPC f) {
        String op = (f.getTypeOperation() != null
                ? f.getTypeOperation() : "").toUpperCase();
        return op.contains("VENTE")
                || op.contains("PRESTATION_CLIENT")
                || op.contains("AVOIR_CLIENT");
    }

    // ══════════════════════════════════════════════════════════
    // CHANGER STATUT
    // ══════════════════════════════════════════════════════════

    @Transactional
    public FactureCPCResponse changerStatut(UUID id, String statut) {
        FactureCPC f = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Facture introuvable"));
        StatutFacture nouveau = StatutFacture.valueOf(statut.toUpperCase());
        f.setStatut(nouveau);
        f = repo.save(f);
        auditClient.log(
                f.getComptableId() != null
                        ? f.getComptableId().toString() : "",
                "ROLE_COMPTABLE",
                "MODIFIER_STATUT_FACTURE",
                "FACTURE",
                id.toString(),
                "Statut → " + statut
                        + " | facture : " + f.getNumeroFacture()
        );
        return toResponse(f);
    }

    // ══════════════════════════════════════════════════════════
    // CONFIRMER PAIEMENT
    // ══════════════════════════════════════════════════════════

    @Transactional
    public FactureCPCResponse confirmerPaiement(UUID id, Map<String, Object> body) {
        FactureCPC f = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Facture introuvable"));

        double montantPaye = dbl(body, "montantPaye");
        double montantTtc  = f.getMontantTtc() != null ? f.getMontantTtc() : 0.0;

        f.setMontantPaye(montantPaye);
        f.setDatePaiement(LocalDate.now());
        f.setModePaiementReel(str(body, "modePaiement"));
        f.setReferenceVirement(str(body, "referenceVirement"));

        if (Math.abs(montantPaye - montantTtc) < 0.01 || montantPaye >= montantTtc) {
            f.setStatut(StatutFacture.PAYE);
            f.setPaiementComplet(true);
            f.setPaiementPartiel(false);
            f.setResteAPayer(0.0);
        } else {
            double reste = Math.round((montantTtc - montantPaye) * 100.0) / 100.0;
            f.setStatut(StatutFacture.PAIEMENT_PARTIEL);
            f.setPaiementComplet(false);
            f.setPaiementPartiel(true);
            f.setResteAPayer(reste);
        }

        f = repo.save(f);

        try {
            journalService.genererEcriturePaiement(f,
                    f.getMontantPaye(), f.getModePaiementReel(),
                    f.getReferenceVirement(), f.getDatePaiement());
        } catch (Exception e) {
            log.warn("⚠️ Erreur écriture paiement : {}", e.getMessage());
        }
        auditClient.log(
                f.getComptableId() != null
                        ? f.getComptableId().toString() : "",
                "ROLE_COMPTABLE",
                "CONFIRMER_PAIEMENT_FACTURE",
                "FACTURE",
                id.toString(),
                "Paiement confirmé : " + montantPaye + " MAD"
                        + " | facture : " + f.getNumeroFacture()
                        + " | mode : " + str(body, "modePaiement")
        );
        return toResponse(f);
    }

    // ══════════════════════════════════════════════════════════
    // CRUD
    // ══════════════════════════════════════════════════════════

    public List<FactureCPCResponse> listerParComptable(UUID comptableId) {
        return repo.findByComptableIdOrderByCreatedAtDesc(comptableId)
                .stream().map(this::toResponse).toList();
    }

    public List<FactureCPCResponse> listerParClient(UUID clientId) {
        log.info("listerParClient clientId={}", clientId);
        List<FactureCPC> result = repo.findByClientIdOrderByDateFactureDesc(clientId);
        log.info("→ {} factures trouvées pour clientId={}", result.size(), clientId);
        return result.stream().map(this::toResponse).toList();
    }

    public List<FactureCPCResponse> listerParClientEtStatut(UUID clientId, String statut) {
        return repo.findByClientIdAndStatut(clientId,
                        StatutFacture.valueOf(statut.toUpperCase()))
                .stream().map(this::toResponse).toList();
    }

    public Map<String, Long> statsParClient(UUID clientId) {
        Map<String, Long> stats = new HashMap<>();
        for (StatutFacture s : StatutFacture.values())
            stats.put(s.name().toLowerCase(),
                    repo.countByClientIdAndStatut(clientId, s));
        return stats;
    }

    public List<FactureCPCResponse> listerParStatut(UUID comptableId, String statut) {
        return repo.findAllByComptableIdAndStatut(comptableId,
                        StatutFacture.valueOf(statut.toUpperCase()))
                .stream().map(this::toResponse).toList();
    }

    public Map<String, Long> stats(UUID comptableId) {
        Map<String, Long> stats = new HashMap<>();
        for (StatutFacture s : StatutFacture.values())
            stats.put(s.name().toLowerCase(),
                    repo.countByComptableIdAndStatut(comptableId, s));
        return stats;
    }

    public FactureCPCResponse getById(UUID id) {
        return toResponse(repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Facture introuvable")));
    }

    // ══════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════

    public ResponseEntity<byte[]> exporterEcriture(UUID id, String format) {
        FactureCPC f = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Facture introuvable"));
        try {
            List<Map<String, Object>> ecritures = mapper.readValue(
                    f.getEcritureComptableJson() != null
                            ? f.getEcritureComptableJson() : "[]",
                    new TypeReference<>() {});
            return "xlsx".equalsIgnoreCase(format)
                    ? exportExcel(f, ecritures)
                    : exportCsv(f, ecritures);
        } catch (Exception e) {
            throw new RuntimeException("Erreur export : " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // OCR
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> appelOcrService(MultipartFile fichier) {
        try {
            RestTemplate rt = new RestTemplate();
            org.springframework.core.io.ByteArrayResource resource =
                    new org.springframework.core.io.ByteArrayResource(
                            fichier.getBytes()) {
                        @Override public String getFilename() {
                            return fichier.getOriginalFilename();
                        }
                    };
            org.springframework.util.LinkedMultiValueMap<String, Object>
                    body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("fichier", resource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<Map> resp = rt.exchange(
                    ocrUrl + "/api/ocr/analyser-facture",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);
            return resp.getBody() != null ? resp.getBody() : new HashMap<>();
        } catch (Exception e) {
            log.error("Erreur OCR : {}", e.getMessage());
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> appelOcrServiceAvecSens(
            MultipartFile fichier, String typeOperationForce) {
        try {
            RestTemplate rt = new RestTemplate();
            org.springframework.core.io.ByteArrayResource resource =
                    new org.springframework.core.io.ByteArrayResource(
                            fichier.getBytes()) {
                        @Override public String getFilename() {
                            return fichier.getOriginalFilename();
                        }
                    };
            org.springframework.util.LinkedMultiValueMap<String, Object>
                    body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("fichier", resource);
            body.add("type_operation_force", typeOperationForce);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<Map> resp = rt.exchange(
                    ocrUrl + "/api/ocr/analyser-facture",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);
            return resp.getBody() != null ? resp.getBody() : new HashMap<>();
        } catch (Exception e) {
            log.error("Erreur OCR forcé : {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String deduireTypeAchat(Map<String, Object> ocr) {
        String texte = str(ocr, "fournisseur").toLowerCase();
        Object itemsObj = ocr.get("items");
        if (itemsObj instanceof List<?> list)
            texte += list.stream().map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(" ")).toLowerCase();
        if (texte.contains("ordinateur") || texte.contains("écran")
                || texte.contains("laptop") || texte.contains("serveur"))
            return "ACHAT_MARCHANDISE";
        if (texte.contains("électricité") || texte.contains("eau ")
                || texte.contains("gaz ")) return "ACHAT_ENERGIE";
        if (texte.contains("téléphone") || texte.contains("internet"))
            return "ACHAT_TELEPHONE";
        if (texte.contains("loyer") || texte.contains("location"))
            return "ACHAT_LOYER";
        if (texte.contains("assurance")) return "ACHAT_ASSURANCE";
        if (texte.contains("transport") || texte.contains("livraison"))
            return "ACHAT_TRANSPORT";
        if (texte.contains("honoraire") || texte.contains("avocat"))
            return "ACHAT_HONORAIRES";
        return "ACHAT_SERVICE";
    }

    // ══════════════════════════════════════════════════════════
    // CPC
    // ══════════════════════════════════════════════════════════

    private void _regenererCpcDepuisJson(FactureCPC facture) throws Exception {
        List<Map<String, Object>> ecritures = mapper.readValue(
                facture.getEcritureComptableJson() != null
                        ? facture.getEcritureComptableJson() : "[]",
                new TypeReference<>() {});

        Map<String, java.math.BigDecimal> totaux = new LinkedHashMap<>();

        for (Map<String, Object> e : ecritures) {
            String compte = str(e, "compte");
            if (compte == null || compte.isEmpty()) continue;
            double debit  = dbl(e, "debit");
            double credit = dbl(e, "credit");
            if (compte.startsWith("7") && credit > 0)
                totaux.merge(compte,
                        java.math.BigDecimal.valueOf(credit),
                        java.math.BigDecimal::add);
            else if (compte.startsWith("6") && debit > 0)
                totaux.merge(compte,
                        java.math.BigDecimal.valueOf(debit),
                        java.math.BigDecimal::add);
        }

        if (totaux.isEmpty()) {
            double ht    = facture.getMontantHt() != null ? facture.getMontantHt() : 0.0;
            String typeOp = facture.getTypeOperation() != null
                    ? facture.getTypeOperation().toUpperCase() : "";
            if (typeOp.startsWith("VENTE_") && ht > 0)
                totaux.put("71243", java.math.BigDecimal.valueOf(ht));
        }

        String dateStr = facture.getDateFacture() != null
                ? facture.getDateFacture().toString() : "";
        Map<String, Object> cpc = cpcService.genererCpc(totaux, dateStr, dateStr);
        facture.setCpcJson(mapper.writeValueAsString(cpc));
    }

    // ══════════════════════════════════════════════════════════
    // CONSTRUIRE FACTURE
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private FactureCPC construireFacture(
            Map<String, Object> ocr,
            UUID clientId, UUID cabinetId,
            UUID comptableId, UUID documentId,
            String minioObject, String nomOriginal) {

        FactureCPC.FactureCPCBuilder b = FactureCPC.builder();

        b.clientId(clientId).cabinetId(cabinetId)
                .comptableId(comptableId).documentId(documentId)
                .minioObject(minioObject).nomOriginal(nomOriginal);

        b.numeroFacture(str(ocr, "numero_facture"))
                .fournisseur(str(ocr, "fournisseur"))
                .client(str(ocr, "client"))
                .ice(str(ocr, "ice"))
                .ifFournisseur(str(ocr, "if_fournisseur"))
                .rc(str(ocr, "rc"))
                .siret(str(ocr, "siret"))
                .devise(str(ocr, "devise"))
                .pays(str(ocr, "pays"))
                .typeFacture(str(ocr, "type_facture"))
                .typeFactureDetecte(str(ocr, "type_facture_detecte"))
                .typeOperation(str(ocr, "type_operation"))
                .modePaiement(str(ocr, "mode_paiement"))
                .journal(str(ocr, "journal"))
                .compteCharge(str(ocr, "compte_charge"))
                .compteTva(str(ocr, "compte_tva"))
                .compteTiers(str(ocr, "compte_tiers"))
                .libelleCompte(str(ocr, "libelle_compte"))
                .typeEcriture(str(ocr, "type_ecriture"))
                .confirmationRequise((Boolean) ocr.getOrDefault("confirmation_requise", false))
                .compteChargeAlternatif(str(ocr, "compte_charge_alternatif"))
                .libelleAlternatif(str(ocr, "libelle_alternatif"));

        parseDate(str(ocr, "date_facture"), "dd/MM/yyyy").ifPresent(b::dateFacture);
        parseDate(str(ocr, "echeance"),     "dd/MM/yyyy").ifPresent(b::echeance);

        b.montantHt(dbl(ocr, "montant_ht"))
                .tvaTaux(dbl(ocr, "tva_taux"))
                .montantTva(dbl(ocr, "montant_tva"))
                .remise(dbl(ocr, "remise"))
                .montantTtc(dbl(ocr, "montant_ttc"))
                .autresFrais(dbl(ocr, "autres_frais"));

        double portHt = dbl(ocr, "frais_port_ht");
        if (portHt == 0.0) portHt = dbl(ocr, "frais_port");
        b.fraisPortHt(portHt)
                .fraisPortTva(dbl(ocr, "frais_port_tva"))
                .fraisPortTtc(dbl(ocr, "frais_port_ttc"))
                .montantTvaMerch(dbl(ocr, "montant_tva_merch"))
                .montantTtcHorsPort(dbl(ocr, "montant_ttc_hors_port"));

        b.valide((Boolean) ocr.getOrDefault("valide", false))
                .scoreConfiance(dbl(ocr, "score_confiance"))
                .confianceMontants(dbl(ocr, "confiance_montants"))
                .regleResolution(str(ocr, "regle_resolution"))
                .coherenceOk((Boolean) ocr.getOrDefault("coherence_ok", false))
                .ecartCoherence(dbl(ocr, "ecart_coherence"))
                .tvaLueSurFacture((Boolean) ocr.getOrDefault("tva_lue_sur_facture", false))
                .tvaEtaitTotale((Boolean) ocr.getOrDefault("tva_etait_totale", false))
                .ecritureEquilibree((Boolean) ocr.getOrDefault("ecriture_equilibree", false));

        try { b.alertes(mapper.writeValueAsString(
                ocr.getOrDefault("alertes", new ArrayList<>()))); }
        catch (Exception e) { b.alertes("[]"); }

        try { b.warnings(mapper.writeValueAsString(
                ocr.getOrDefault("warnings", new ArrayList<>()))); }
        catch (Exception e) { b.warnings("[]"); }

        try { b.itemsJson(mapper.writeValueAsString(
                ocr.getOrDefault("items", new ArrayList<>()))); }
        catch (Exception e) { b.itemsJson("[]"); }

        Object ecritures = ocr.get("ecritures_comptables");
        if (ecritures == null)
            ecritures = ocr.getOrDefault("ecriture_comptable", new ArrayList<>());
        try { b.ecritureComptableJson(mapper.writeValueAsString(ecritures)); }
        catch (Exception e) { b.ecritureComptableJson("[]"); }

        try { b.montantsBrutsJson(mapper.writeValueAsString(
                ocr.getOrDefault("montants_bruts", new HashMap<>()))); }
        catch (Exception e) { b.montantsBrutsJson("{}"); }

        try { b.cpcJson(mapper.writeValueAsString(
                ocr.getOrDefault("cpc", new HashMap<>()))); }
        catch (Exception e) { b.cpcJson("{}"); }

        return b.build();
    }

    // ══════════════════════════════════════════════════════════
    // toResponse
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private FactureCPCResponse toResponse(FactureCPC f) {
        List<String> alertes   = new ArrayList<>();
        List<String> warnings  = new ArrayList<>();
        List<Object> items     = new ArrayList<>();
        List<Object> ecritures = new ArrayList<>();
        Map<String, Object> mb  = new HashMap<>();
        Map<String, Object> cpc = new HashMap<>();

        try { alertes   = mapper.readValue(f.getAlertes()   != null ? f.getAlertes()   : "[]", new TypeReference<>() {}); } catch (Exception ignored) {}
        try { warnings  = mapper.readValue(f.getWarnings()  != null ? f.getWarnings()  : "[]", new TypeReference<>() {}); } catch (Exception ignored) {}
        try { items     = mapper.readValue(f.getItemsJson() != null ? f.getItemsJson() : "[]", new TypeReference<>() {}); } catch (Exception ignored) {}
        try { ecritures = mapper.readValue(f.getEcritureComptableJson() != null ? f.getEcritureComptableJson() : "[]", new TypeReference<>() {}); } catch (Exception ignored) {}
        try { mb  = mapper.readValue(f.getMontantsBrutsJson() != null ? f.getMontantsBrutsJson() : "{}", new TypeReference<>() {}); } catch (Exception ignored) {}
        try { cpc = mapper.readValue(f.getCpcJson()           != null ? f.getCpcJson()           : "{}", new TypeReference<>() {}); } catch (Exception ignored) {}

        String urlPdf = "";
        if (f.getMinioObject() != null && !f.getMinioObject().isEmpty())
            urlPdf = minioStorage.getUrlPresignee(f.getMinioObject());

        return FactureCPCResponse.builder()
                .id(f.getId()).documentId(f.getDocumentId())
                .minioObject(f.getMinioObject()).nomOriginal(f.getNomOriginal())
                .clientId(f.getClientId()).cabinetId(f.getCabinetId()).comptableId(f.getComptableId())
                .numeroFacture(f.getNumeroFacture()).dateFacture(f.getDateFacture())
                .fournisseur(f.getFournisseur()).client(f.getClient())
                .ice(f.getIce()).ifFournisseur(f.getIfFournisseur())
                .rc(f.getRc()).siret(f.getSiret())
                .devise(f.getDevise()).pays(f.getPays())
                .typeFacture(f.getTypeFacture()).typeFactureDetecte(f.getTypeFactureDetecte())
                .typeOperation(f.getTypeOperation()).modePaiement(f.getModePaiement())
                .echeance(f.getEcheance())
                .montantHt(f.getMontantHt()).tvaTaux(f.getTvaTaux())
                .montantTva(f.getMontantTva()).remise(f.getRemise())
                .montantTtc(f.getMontantTtc())
                .fraisPortHt(f.getFraisPortHt()).fraisPortTva(f.getFraisPortTva())
                .fraisPortTtc(f.getFraisPortTtc()).autresFrais(f.getAutresFrais())
                .montantTvaMerch(f.getMontantTvaMerch()).montantTtcHorsPort(f.getMontantTtcHorsPort())
                .journal(f.getJournal()).compteCharge(f.getCompteCharge())
                .compteTva(f.getCompteTva()).compteTiers(f.getCompteTiers())
                .libelleCompte(f.getLibelleCompte())
                .valide(f.getValide()).scoreConfiance(f.getScoreConfiance())
                .confianceMontants(f.getConfianceMontants())
                .regleResolution(f.getRegleResolution())
                .coherenceOk(f.getCoherenceOk()).ecartCoherence(f.getEcartCoherence())
                .tvaLueSurFacture(f.getTvaLueSurFacture()).tvaEtaitTotale(f.getTvaEtaitTotale())
                .ecritureEquilibree(f.getEcritureEquilibree())
                .confirmationRequise(f.getConfirmationRequise())
                .typeEcriture(f.getTypeEcriture())
                .compteChargeAlternatif(f.getCompteChargeAlternatif())
                .libelleAlternatif(f.getLibelleAlternatif())
                .alertes(alertes).warnings(warnings).items(items)
                .ecritureComptable(ecritures).montantsBruts(mb).cpc(cpc)
                .statut(f.getStatut()).createdAt(f.getCreatedAt())
                .urlTelechargement(urlPdf)
                .datePaiement(f.getDatePaiement()).modePaiementReel(f.getModePaiementReel())
                .referenceVirement(f.getReferenceVirement()).montantPaye(f.getMontantPaye())
                .paiementComplet(f.getPaiementComplet()).paiementPartiel(f.getPaiementPartiel())
                .resteAPayer(f.getResteAPayer())
                .build();
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════

    private Optional<LocalDate> parseDate(String s, String pattern) {
        if (s == null || s.isBlank()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : "";
    }

    private Double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return 0.0;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    // ══════════════════════════════════════════════════════════
    // EXPORT CSV / EXCEL
    // ══════════════════════════════════════════════════════════

    private ResponseEntity<byte[]> exportCsv(
            FactureCPC f, List<Map<String, Object>> ecritures) throws Exception {
        StringBuilder csv = new StringBuilder('\uFEFF');
        csv.append("Journal;Date;Pièce;Compte;Libellé;Débit;Crédit\n");
        for (Map<String, Object> e : ecritures) {
            csv.append(e.getOrDefault("journal", "")).append(";")
                    .append(e.getOrDefault("date",    "")).append(";")
                    .append(e.getOrDefault("piece",   "")).append(";")
                    .append(e.getOrDefault("compte",  "")).append(";")
                    .append(e.getOrDefault("libelle", "")).append(";")
                    .append(e.getOrDefault("debit",   "0")).append(";")
                    .append(e.getOrDefault("credit",  "0")).append("\n");
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String nom = "ecriture_"
                + (f.getNumeroFacture() != null ? f.getNumeroFacture() : f.getId())
                + ".csv";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + nom + "\"")
                .header("Content-Type", "text/csv;charset=UTF-8")
                .body(bytes);
    }

    private ResponseEntity<byte[]> exportExcel(
            FactureCPC f, List<Map<String, Object>> ecritures) throws Exception {
        try (var wb  = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var bos = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Écriture_PCG");
            var hRow  = sheet.createRow(0);
            String[] cols = {"Journal","Date","Pièce","Compte","Libellé","Débit","Crédit"};
            for (int i = 0; i < cols.length; i++) {
                hRow.createCell(i).setCellValue(cols[i]);
                sheet.setColumnWidth(i, 4000);
            }
            sheet.setColumnWidth(4, 8000);
            int rowNum = 1;
            for (Map<String, Object> e : ecritures) {
                var row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(str(e, "journal"));
                row.createCell(1).setCellValue(str(e, "date"));
                row.createCell(2).setCellValue(str(e, "piece"));
                row.createCell(3).setCellValue(str(e, "compte"));
                row.createCell(4).setCellValue(str(e, "libelle"));
                row.createCell(5).setCellValue(dbl(e, "debit"));
                row.createCell(6).setCellValue(dbl(e, "credit"));
            }
            wb.write(bos);
            String nom = "ecriture_"
                    + (f.getNumeroFacture() != null ? f.getNumeroFacture() : f.getId())
                    + ".xlsx";
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + nom + "\"")
                    .header("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(bos.toByteArray());
        }
    }
    public Map<String, Object> getDashboardStats(
            UUID comptableId, UUID clientId) {
        log.info("🔍 dashboard-stats: comptableId={} clientId={}",
                comptableId, clientId);
        List<FactureCPC> factures = clientId != null
                ? repo.findByClientIdOrderByDateFactureDesc(clientId)
                : repo.findByComptableIdOrderByCreatedAtDesc(comptableId);
        log.info("→ {} factures trouvées", factures.size());
        // ── CA mensuel (6 derniers mois) ──────────────
        Map<String, Double> caMensuel = new LinkedHashMap<>();
        java.time.LocalDate now = java.time.LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            java.time.LocalDate mois = now.minusMonths(i);
            String label = mois.format(
                    java.time.format.DateTimeFormatter.ofPattern("MMM yy",
                            java.util.Locale.FRENCH));
            caMensuel.put(label, 0.0);
        }

        for (FactureCPC f : factures) {
            if (f.getDateFacture() == null) continue;
            if (f.getMontantHt() == null) continue;
            String op = f.getTypeOperation() != null
                    ? f.getTypeOperation().toUpperCase() : "";
            if (!op.startsWith("VENTE")) continue;
            java.time.LocalDate d = f.getDateFacture();
            if (d.isBefore(now.minusMonths(6))) continue;
            String label = d.format(
                    java.time.format.DateTimeFormatter.ofPattern("MMM yy",
                            java.util.Locale.FRENCH));
            if (caMensuel.containsKey(label))
                caMensuel.merge(label, f.getMontantHt(), Double::sum);
        }

        // ── Charges vs Produits ───────────────────────
        double totalCharges  = 0.0;
        double totalProduits = 0.0;
        for (FactureCPC f : factures) {
            if (f.getMontantHt() == null) continue;
            String op = f.getTypeOperation() != null
                    ? f.getTypeOperation().toUpperCase() : "";
            if (op.startsWith("VENTE"))
                totalProduits += f.getMontantHt();
            else
                totalCharges  += f.getMontantHt();
        }


        // ── Total TTC (produits avec TVA) ─────────────────


        // ── Statuts ───────────────────────────────────
        Map<String, Long> statuts = new LinkedHashMap<>();
        statuts.put("BROUILLON", 0L);
        statuts.put("VALIDE",    0L);
        statuts.put("PAYE",      0L);
        statuts.put("EN_ATTENTE_PAIEMENT", 0L);

        for (FactureCPC f : factures) {
            if (f.getStatut() == null) continue;
            String s = f.getStatut().name();
            statuts.merge(s, 1L, Long::sum);
        }


        double totalTtcProduits = factures.stream()
                .filter(f -> {
                    String op = f.getTypeOperation() != null
                            ? f.getTypeOperation().toUpperCase() : "";
                    return op.startsWith("VENTE");
                })
                .mapToDouble(f -> f.getMontantTtc() != null
                        ? f.getMontantTtc() : 0.0)
                .sum();


        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caMensuel",    caMensuel);
        result.put("totalCharges", Math.round(totalCharges * 100.0) / 100.0);
        result.put("totalProduits",Math.round(totalProduits * 100.0) / 100.0);
        result.put("totalTtcProduits",
                Math.round(totalTtcProduits * 100.0) / 100.0);
        result.put("statuts",      statuts);
        result.put("nbFactures",   factures.size());
        return result;
    }

    public Map<String, Object> getStatsAvancement(
            UUID clientId) {

        List<FactureCPC> factures = repo
                .findByClientIdOrderByDateFactureDesc(clientId);

        long enAttente = factures.stream()
                .filter(f -> f.getStatut() ==
                        StatutFacture.NOUVEAU
                        || f.getStatut() ==
                        StatutFacture.ENREGISTRE)
                .count();

        long traitees = factures.stream()
                .filter(f -> f.getStatut() ==
                        StatutFacture.APPROUVE
                        || f.getStatut() ==
                        StatutFacture.PAYE
                        || f.getStatut() ==
                        StatutFacture.PAIEMENT_PARTIEL)
                .count();

        LocalDate debutMois = LocalDate.now()
                .withDayOfMonth(1);

        long facturesMoisActuel = factures.stream()
                .filter(f -> f.getCreatedAt() != null
                        && !f.getCreatedAt()
                        .toLocalDate()
                        .isBefore(debutMois))
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFactures", factures.size());
        stats.put("facturesEnAttente", enAttente);
        stats.put("facturesTraitees", traitees);
        stats.put("facturesMoisActuel",
                facturesMoisActuel);

        return stats;
    }

}