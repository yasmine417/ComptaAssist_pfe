package com.comptaassist.cabinet_service.controller;

import com.comptaassist.cabinet_service.client.DocumentServiceClient;
import com.comptaassist.cabinet_service.entity.SignatureLien;
import com.comptaassist.cabinet_service.entity.SignatureLien.StatutLien;
import com.comptaassist.cabinet_service.entity.SignatureElectronique.StatutSignature;
import com.comptaassist.cabinet_service.repository.SignatureLienRepository;
import com.comptaassist.cabinet_service.repository.SignatureElectroniqueRepository;
import com.comptaassist.cabinet_service.service.PdfSignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/public/signature")
@RequiredArgsConstructor
@Slf4j
public class SignaturePublicController {

    private final SignatureLienRepository         lienRepo;
    private final SignatureElectroniqueRepository sigRepo;
    private final DocumentServiceClient           documentClient;
    private final PdfSignatureService             pdfSignatureService;

    @Value("${document.service.url:http://localhost:8083}")
    private String documentServiceUrl;
    @Value("${local.uploads.path:uploads}")
    private String dossierUploadsLocal;
    @Value("${app.internal-secret:comptaassist-internal-secret}")
    private String internalSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Infos du lien ─────────────────────────────────────────────────────
    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> getInfos(@PathVariable String token) {
        SignatureLien lien = lienRepo.findByToken(token).orElse(null);
        if (lien == null)
            return ResponseEntity.status(404).body(Map.of("erreur", "Lien invalide"));

        if (lien.getExpiresAt().isBefore(LocalDateTime.now())) {
            lien.setStatut(StatutLien.EXPIRE);
            lienRepo.save(lien);
            return ResponseEntity.status(410).body(Map.of("erreur", "Lien expiré"));
        }

        if (lien.getStatut() == StatutLien.EN_ATTENTE_COMPTABLE)
            return ResponseEntity.status(403).body(
                    Map.of("erreur", "Ce document attend la signature du cabinet."));

        if (lien.getStatut() == StatutLien.SIGNE) {
            String urlSigne = lien.getPdfSigneMinioKey() != null
                    ? documentClient.getUrlPresignee(lien.getPdfSigneMinioKey()) : "";
            return ResponseEntity.ok(Map.of(
                    "statut",      "SIGNE",
                    "clientNom",   lien.getClientNom(),
                    "message",     "Ce document a déjà été signé.",
                    "urlPdfSigne", urlSigne
            ));
        }

        // ── Servir le PDF avec signature comptable intégrée ───────────────
        // Le client voit la signature du comptable avant de signer
        String urlPdf;
        if (lien.getPdfAvecComptableMinioKey() != null) {
            // PDF avec signature comptable déjà intégrée
            urlPdf = documentClient.getUrlPresignee(lien.getPdfAvecComptableMinioKey());
        } else {
            // Fallback : PDF original
            urlPdf = documentClient.getUrlPresignee(lien.getPdfMinioKey());
        }

        return ResponseEntity.ok(Map.of(
                "statut",    "EN_ATTENTE_CLIENT",
                "clientNom", lien.getClientNom(),
                "urlPdf",    urlPdf,
                "expiresAt", lien.getExpiresAt().toString()
        ));
    }

    // ── Client signe ──────────────────────────────────────────────────────
    @PostMapping("/{token}/signer")
    public ResponseEntity<Map<String, Object>> signer(
            @PathVariable String token,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            jakarta.servlet.http.HttpServletRequest request) {

        SignatureLien lien = lienRepo.findByToken(token).orElse(null);
        if (lien == null)
            return ResponseEntity.status(404).body(Map.of("erreur", "Lien invalide"));
        if (lien.getExpiresAt().isBefore(LocalDateTime.now()))
            return ResponseEntity.status(410).body(Map.of("erreur", "Lien expiré"));
        if (lien.getStatut() == StatutLien.SIGNE)
            return ResponseEntity.badRequest().body(Map.of("erreur", "Déjà signé"));
        if (lien.getStatut() == StatutLien.EN_ATTENTE_COMPTABLE)
            return ResponseEntity.status(403).body(Map.of("erreur", "Signature comptable manquante"));

        String signatureClientBase64 = body.get("signature");
        if (signatureClientBase64 == null || signatureClientBase64.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("erreur", "Signature manquante"));

        lien.setSignatureBase64(signatureClientBase64);
        lien.setSignedAt(LocalDateTime.now());
        lien.setIpClient(request.getRemoteAddr());
        lien.setUserAgent(userAgent);
        lien.setStatut(StatutLien.SIGNE);

        // ── Générer PDF FINAL avec les 2 signatures ───────────────────────
        try {
            String htmlOriginal = lien.getHtmlOriginal();
            if (htmlOriginal != null && !htmlOriginal.isBlank()
                    && lien.getSignatureComptableBase64() != null) {

                byte[] pdfFinal = pdfSignatureService.genererPdfAvecSignatures(
                        htmlOriginal,
                        lien.getSignatureComptableBase64(), lien.getComptableNom(),
                        signatureClientBase64, lien.getClientNom()
                );

                String minioKeySigne = sauvegarderPdfInterne(
                        pdfFinal,
                        "FINAL_" + lien.getClientNom().replaceAll("[^a-zA-Z0-9]", "_") + ".pdf",
                        lien
                );
                lien.setPdfSigneMinioKey(minioKeySigne);
                log.info("✅ PDF final avec 2 signatures : {}", minioKeySigne);
            }
        } catch (Exception e) {
            log.warn("Erreur PDF final : {}", e.getMessage());
        }

        lienRepo.save(lien);

        sigRepo.findById(lien.getSignatureId()).ifPresent(sig -> {
            sig.setStatut(StatutSignature.SIGNE);
            sig.setSignedAt(LocalDateTime.now());
            sigRepo.save(sig);
        });

        return ResponseEntity.ok(Map.of(
                "message",  "Document signé avec succès !",
                "signedAt", lien.getSignedAt().toString()
        ));
    }

    // ── Sauvegarder via endpoint interne ─────────────────────────────────
    // ── Sauvegarder via endpoint interne ─────────────────────────────────
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
            String cabinetId = sigRepo.findById(lien.getSignatureId())
                    .map(s -> s.getCabinetId()).orElse("unknown");
            String clientId  = sigRepo.findById(lien.getSignatureId())
                    .map(s -> s.getClientId()).orElse("unknown");

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
            log.error("Erreur upload interne : {}", e.getMessage());
            return null;
        }
    }

    // ── Sauvegarde locale dans uploads/signatures ─────────────
    private void sauvegarderLocal(byte[] bytes, String nomFichier)
            throws Exception {

        java.nio.file.Path dossier = java.nio.file.Paths.get(
                dossierUploadsLocal, "signatures");
        java.nio.file.Files.createDirectories(dossier);

        String nomUnique = java.util.UUID.randomUUID() + "_" + nomFichier;
        java.nio.file.Path cheminFichier = dossier.resolve(nomUnique);
        java.nio.file.Files.write(cheminFichier, bytes);

        log.info("PDF final (2 signatures) sauvegardé localement : {}",
                cheminFichier.toAbsolutePath());
    }
}