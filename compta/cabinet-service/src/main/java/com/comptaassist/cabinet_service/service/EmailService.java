package com.comptaassist.cabinet_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public void envoyerIdentifiantsComptable(
            String nomComplet,
            String email,
            String motDePasse,
            String nomCabinet) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(
                    "Vos identifiants ComptaAssist — " + nomCabinet);
            helper.setText(
                    buildEmailHtml(nomComplet, email,
                            motDePasse, nomCabinet),
                    true
            );

            mailSender.send(message);
            log.info("Email envoyé à {}", email);

        } catch (Exception e) {
            log.error("Erreur envoi email à {} : {}",
                    email, e.getMessage());
        }
    }

    private String buildEmailHtml(String nom, String email,
                                  String mdp, String cabinet) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body { font-family: 'Segoe UI', sans-serif;
                       background: #f3f4f6; margin: 0; padding: 0; }
                .container { max-width: 560px; margin: 40px auto;
                             background: white; border-radius: 16px;
                             overflow: hidden;
                             box-shadow: 0 4px 24px rgba(0,0,0,0.08); }
                .header { background: linear-gradient(
                            135deg, #065f46, #059669);
                          padding: 40px 32px; text-align: center; }
                .header h1 { color: white; font-size: 24px;
                              margin: 0; font-weight: 700; }
                .header p  { color: #a7f3d0; margin: 8px 0 0;
                              font-size: 14px; }
                .body { padding: 36px 32px; }
                .greeting { font-size: 18px; font-weight: 600;
                             color: #111827; margin-bottom: 12px; }
                .text { color: #6b7280; font-size: 14px;
                        line-height: 1.7; margin-bottom: 28px; }
                .creds-box { background: #f0fdf4;
                              border: 1px solid #bbf7d0;
                              border-radius: 12px; padding: 24px;
                              margin-bottom: 28px; }
                .creds-title { font-size: 12px; font-weight: 600;
                                color: #059669; text-transform: uppercase;
                                letter-spacing: 0.8px;
                                margin-bottom: 16px; }
                .cred-row { display: flex; align-items: center;
                             gap: 12px; margin-bottom: 12px; }
                .cred-label { font-size: 12px; color: #6b7280;
                               width: 80px; flex-shrink: 0; }
                .cred-value { font-size: 15px; font-weight: 600;
                               color: #065f46; background: white;
                               padding: 8px 14px; border-radius: 8px;
                               border: 1px solid #d1fae5;
                               font-family: monospace;
                               letter-spacing: 1px; flex: 1; }
                .btn { display: block; text-align: center;
                        background: linear-gradient(
                          135deg, #059669, #047857);
                        color: white; text-decoration: none;
                        padding: 14px 32px; border-radius: 10px;
                        font-weight: 600; font-size: 15px;
                        margin-bottom: 24px; }
                .warning { background: #fffbeb;
                            border: 1px solid #fde68a;
                            border-radius: 8px; padding: 14px;
                            font-size: 13px; color: #92400e; }
                .footer { background: #f9fafb; padding: 20px 32px;
                           text-align: center; font-size: 12px;
                           color: #9ca3af; border-top: 1px solid #f3f4f6; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>ComptaAssist AI</h1>
                  <p>Plateforme Comptable Intelligente</p>
                </div>
                <div class="body">
                  <div class="greeting">Bonjour %s 👋</div>
                  <p class="text">
                    Vous avez été ajouté(e) comme comptable au cabinet
                    <strong>%s</strong>. Voici vos identifiants de connexion :
                  </p>

                  <div class="creds-box">
                    <div class="creds-title">Vos identifiants</div>
                    <div class="cred-row">
                      <span class="cred-label">Email</span>
                      <span class="cred-value">%s</span>
                    </div>
                    <div class="cred-row">
                      <span class="cred-label">Mot de passe</span>
                      <span class="cred-value">%s</span>
                    </div>
                  </div>

                  <a href="http://localhost:4200/auth/login" class="btn">
                    Se connecter à ComptaAssist →
                  </a>

                  <div class="warning">
                    ⚠️ Pour votre sécurité, modifiez votre mot de passe
                    dès votre première connexion.
                  </div>
                </div>
                <div class="footer">
                  © 2025 ComptaAssist AI · Cabinet %s
                </div>
              </div>
            </body>
            </html>
            """.formatted(nom, cabinet, email, mdp, cabinet);
    }
}