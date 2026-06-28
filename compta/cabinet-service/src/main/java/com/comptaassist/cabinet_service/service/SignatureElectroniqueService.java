package com.comptaassist.cabinet_service.service;

import com.comptaassist.cabinet_service.entity.SignatureElectronique;
import com.comptaassist.cabinet_service.entity.SignatureElectronique.*;
import com.comptaassist.cabinet_service.entity.SignatureLien;
import com.comptaassist.cabinet_service.entity.SignatureLien.StatutLien;
import com.comptaassist.cabinet_service.repository.SignatureElectroniqueRepository;
import com.comptaassist.cabinet_service.repository.SignatureLienRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SignatureElectroniqueService {

    private final SignatureElectroniqueRepository repo;
    private final SignatureLienRepository         lienRepo;
    private final DocumentLegalService            documentService;
    private final SignatureEmailService           emailService;
    private final PdfSignatureService             pdfSignatureService;

    @Value("${document.service.url:http://localhost:8083}")
    private String documentServiceUrl;
    @Value("${local.uploads.path:uploads}")
    private String dossierUploadsLocal;
    @Value("${app.internal-secret:comptaassist-internal-secret}")
    private String internalSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    // ══════════════════════════════════════════════════════════
    // ÉTAPE 1 : Créer document → statut EN_ATTENTE_COMPTABLE
    // ══════════════════════════════════════════════════════════
    @Transactional
    public SignatureElectronique envoyerDocument(
            String cabinetId, String clientId,
            String clientEmail, String clientNom,
            TypeDocument typeDocument,
            Map<String, String> formData,
            String jwtToken) {

        byte[] pdfBytes   = genererPdf(typeDocument, formData);
        String htmlOriginal = genererHtml(typeDocument, formData);

        SignatureElectronique sig = SignatureElectronique.builder()
                .cabinetId(cabinetId).clientId(clientId)
                .clientEmail(clientEmail).clientNom(clientNom)
                .typeDocument(typeDocument)
                .openSignDocumentId("").openSignSigningUrl("")
                .openSignStatus("pending").statut(StatutSignature.ENVOYE)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        sig = repo.save(sig);

        String nomFichier = typeDocument.name() + "_" +
                clientNom.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";

        emailService.creerLienSansEnvoiEmail(
                sig, pdfBytes, htmlOriginal, nomFichier, typeDocument.name(), jwtToken);

        return sig;
    }

    // ══════════════════════════════════════════════════════════
    // ÉTAPE 2 : Comptable signe
    // → Génère PDF avec signature comptable intégrée
    // → Envoie CE PDF au client (pas le PDF original)
    // ══════════════════════════════════════════════════════════
    @Transactional
    public void signerComptable(
            String signatureId,
            String signatureComptableBase64,
            String comptableNom,
            String jwtToken) {

        SignatureLien lien = lienRepo.findBySignatureId(signatureId)
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Lien introuvable"));

        if (lien.getStatut() != StatutLien.EN_ATTENTE_COMPTABLE)
            throw new RuntimeException("Statut invalide");

        // Enregistrer signature comptable
        lien.setSignatureComptableBase64(signatureComptableBase64);
        lien.setComptableNom(comptableNom);
        lien.setSignedAtComptable(LocalDateTime.now());

        // ── Générer PDF avec signature comptable intégrée ─────
        try {
            String htmlOriginal = lien.getHtmlOriginal();
            if (htmlOriginal != null && !htmlOriginal.isBlank()) {

                // PDF avec seulement la signature comptable (zone client vide)
                byte[] pdfAvecComptable = pdfSignatureService
                        .genererPdfAvecSignatureComptableSeulement(
                                htmlOriginal, signatureComptableBase64, comptableNom);

                // Sauvegarder dans MinIO via endpoint interne
                String nomFichier = "AVEC_COMPTABLE_" +
                        lien.getClientNom().replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";
                String minioKey = sauvegarderPdfInterne(pdfAvecComptable, nomFichier, lien);
                lien.setPdfAvecComptableMinioKey(minioKey);
                log.info("PDF avec signature comptable généré : {}", minioKey);
            }
        } catch (Exception e) {
            log.warn("Erreur génération PDF avec comptable : {}", e.getMessage());
        }

        lien.setStatut(StatutLien.EN_ATTENTE_CLIENT);
        lienRepo.save(lien);

        // Mettre à jour statut
        repo.findById(signatureId).ifPresent(sig -> {
            sig.setOpenSignStatus("comptable_signed");
            repo.save(sig);
        });

        // Envoyer email au client
        emailService.envoyerEmailClient(lien, jwtToken);
        log.info("Email envoyé au client {}", lien.getClientEmail());
    }

    // ── Sauvegarder PDF via endpoint interne ──────────────────

    // ── Sauvegarder PDF via endpoint interne ──────────────────
    @SuppressWarnings("unchecked")
    private String sauvegarderPdfInterne(byte[] pdf, String nom, SignatureLien lien) {

        // ── Sauvegarde locale systématique (peu importe MinIO) ──
        try {
            sauvegarderLocal(pdf, nom);
        } catch (Exception e) {
            log.warn("Sauvegarde locale échouée (non bloquant) : {}",
                    e.getMessage());
        }

        try {
            String cabinetId = repo.findById(lien.getSignatureId())
                    .map(s -> s.getCabinetId()).orElse(UUID.randomUUID().toString());
            String clientId  = repo.findById(lien.getSignatureId())
                    .map(s -> s.getClientId()).orElse(UUID.randomUUID().toString());

            ByteArrayResource resource = new ByteArrayResource(pdf) {
                @Override public String getFilename() { return nom; }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("fichier",   resource);
            body.add("clientId",  clientId);
            body.add("cabinetId", cabinetId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("X-Internal-Secret", internalSecret);

            ResponseEntity<Map> response = restTemplate.exchange(
                    documentServiceUrl + "/api/internal/documents/upload",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<String, Object> result = response.getBody();
            return result != null && result.get("minioObject") != null
                    ? result.get("minioObject").toString() : null;

        } catch (Exception e) {
            log.error("Erreur sauvegarde PDF interne : {}", e.getMessage());
            return null;
        }
    }

    // ── Sauvegarde locale dans uploads/signatures ─────────────
    private void sauvegarderLocal(byte[] bytes, String nomFichier)
            throws Exception {

        java.nio.file.Path dossier = java.nio.file.Paths.get(
                dossierUploadsLocal, "signatures");
        java.nio.file.Files.createDirectories(dossier);

        String nomUnique = UUID.randomUUID() + "_" + nomFichier;
        java.nio.file.Path cheminFichier = dossier.resolve(nomUnique);
        java.nio.file.Files.write(cheminFichier, bytes);

        log.info("PDF (avec signature) sauvegardé localement : {}",
                cheminFichier.toAbsolutePath());
    }

    // ── Queries ───────────────────────────────────────────────
    public List<SignatureElectronique> listerParCabinet(String cabinetId) {
        return repo.findByCabinetIdOrderByCreatedAtDesc(cabinetId);
    }
    public List<SignatureElectronique> listerParClient(String clientId) {
        return repo.findByClientIdOrderByCreatedAtDesc(clientId);
    }
    public SignatureElectronique synchroniserStatut(String signatureId) {
        return repo.findById(signatureId)
                .orElseThrow(() -> new RuntimeException("Signature introuvable"));
    }
    public SignatureElectronique revoquer(String signatureId) {
        SignatureElectronique sig = repo.findById(signatureId)
                .orElseThrow(() -> new RuntimeException("Signature introuvable"));
        sig.setStatut(StatutSignature.REFUSE);
        return repo.save(sig);
    }
    public void renvoyerEmail(String signatureId) {}
    public void traiterWebhook(Map<String, Object> payload) {}

    // ── Helpers ───────────────────────────────────────────────
    private byte[] genererPdf(TypeDocument type, Map<String, String> data) {
        return switch (type) {
            case LETTRE_MISSION    -> documentService.genererLettreMission(data);
            case MANDAT_TVA        -> documentService.genererMandatTva(data);
            case APPROBATION_BILAN -> documentService.genererLettreMission(data);
        };
    }
    private String genererHtml(TypeDocument type, Map<String, String> data) {
        return switch (type) {
            case LETTRE_MISSION    -> documentService.genererHtmlLettreMission(data);
            case MANDAT_TVA        -> documentService.genererHtmlMandatTva(data);
            case APPROBATION_BILAN -> documentService.genererHtmlLettreMission(data);
        };
    }
}