package com.comptaassist.cabinet_service.service;

import com.comptaassist.cabinet_service.client.DocumentServiceClient;
import com.comptaassist.cabinet_service.entity.SignatureElectronique;
import com.comptaassist.cabinet_service.entity.SignatureLien;
import com.comptaassist.cabinet_service.entity.SignatureLien.StatutLien;
import com.comptaassist.cabinet_service.repository.SignatureLienRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SignatureEmailService {

    private final JavaMailSender          mailSender;
    private final SignatureLienRepository lienRepo;
    private final DocumentServiceClient   documentClient;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ══════════════════════════════════════════════════════════
    // CRÉER LIEN — HTML stocké dans champ dédié
    // ══════════════════════════════════════════════════════════
    @Transactional
    public SignatureLien creerLienSansEnvoiEmail(
            SignatureElectronique sig,
            byte[] pdfBytes,
            String htmlOriginal,
            String nomFichier,
            String typeDocument,
            String jwtToken) {

        // Sauvegarder PDF dans MinIO
        String minioKey = documentClient.sauvegarderPdf(
                pdfBytes, nomFichier,
                UUID.fromString(sig.getClientId()),
                UUID.fromString(sig.getCabinetId()),
                UUID.fromString(sig.getCabinetId()),
                jwtToken
        );

        String token = UUID.randomUUID().toString().replace("-", "");

        SignatureLien lien = SignatureLien.builder()
                .signatureId(sig.getId())
                .token(token)
                .clientEmail(sig.getClientEmail())
                .clientNom(sig.getClientNom())
                .pdfMinioKey(minioKey)
                .htmlOriginal(htmlOriginal)        // ← champ dédié
                .statut(StatutLien.EN_ATTENTE_COMPTABLE)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        return lienRepo.save(lien);
    }

    // ══════════════════════════════════════════════════════════
    // ENVOYER EMAIL AU CLIENT
    // ══════════════════════════════════════════════════════════
    public void envoyerEmailClient(SignatureLien lien, String jwtToken) {
        String lienSignature = frontendUrl + "/signer/" + lien.getToken();
        envoyerEmail(lien.getClientEmail(), lien.getClientNom(), lienSignature);
    }

    private void envoyerEmail(String to, String clientNom, String lienSignature) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Document à signer — ComptaAssist");

            String html =
                    "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>" +
                            "<body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;color:#1e293b'>" +
                            "<div style='background:#6366f1;padding:24px;border-radius:10px 10px 0 0;text-align:center'>" +
                            "  <h1 style='color:white;margin:0;font-size:22px'>ComptaAssist</h1>" +
                            "</div>" +
                            "<div style='background:#f8fafc;border:1px solid #e2e8f0;border-top:none;border-radius:0 0 10px 10px;padding:32px'>" +
                            "  <p>Bonjour <strong>" + clientNom + "</strong>,</p>" +
                            "  <p style='color:#475569;margin-bottom:24px'>Votre cabinet comptable a signé un document et vous invite à le signer :</p>" +
                            "  <div style='text-align:center;margin-bottom:28px'>" +
                            "    <a href='" + lienSignature + "' style='background:#6366f1;color:white;padding:14px 32px;" +
                            "border-radius:8px;text-decoration:none;font-weight:700;font-size:15px;display:inline-block'>" +
                            "✍️ Signer le document</a>" +
                            "  </div>" +
                            "  <p style='font-size:12px;color:#94a3b8;text-align:center'>Lien valable 30 jours.<br>" +
                            "<span style='color:#6366f1'>" + lienSignature + "</span></p>" +
                            "</div></body></html>";

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email envoyé à : {}", to);
        } catch (Exception e) {
            log.error("Erreur email : {}", e.getMessage());
            throw new RuntimeException("Erreur email : " + e.getMessage());
        }
    }
}