package com.comptaassist.auth_service.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public void envoyerIdentifiants(
            String nomComplet,
            String email,
            String motDePasse,
            String role,
            String nomApp) {
        try {
            MimeMessage message =
                    mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(
                    "Vos identifiants " + nomApp
                            + " — " + role);
            helper.setText(
                    buildHtml(nomComplet, email,
                            motDePasse, role, nomApp),
                    true);

            mailSender.send(message);
            log.info("Email envoyé à {}", email);

        } catch (Exception e) {
            log.error("Erreur email : {}",
                    e.getMessage());
        }
    }

    private String buildHtml(String nom, String email,
                             String mdp, String role,
                             String app) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family:Inter,sans-serif;
                         background:#f3f4f6;margin:0;padding:0">
              <div style="max-width:560px;margin:40px auto;
                          background:white;border-radius:16px;
                          overflow:hidden;
                          box-shadow:0 4px 24px rgba(0,0,0,0.08)">
                <div style="background:linear-gradient(
                              135deg,#065f46,#059669);
                            padding:40px 32px;text-align:center">
                  <h1 style="color:white;font-size:24px;margin:0">
                    %s
                  </h1>
                  <p style="color:#a7f3d0;margin:8px 0 0;
                             font-size:14px">
                    Plateforme Comptable Intelligente
                  </p>
                </div>
                <div style="padding:36px 32px">
                  <p style="font-size:18px;font-weight:600;
                             color:#111827">
                    Bonjour %s 👋
                  </p>
                  <p style="color:#6b7280;font-size:14px;
                             line-height:1.7">
                    Votre compte <strong>%s</strong>
                    a été créé. Voici vos identifiants :
                  </p>
                  <div style="background:#f0fdf4;
                              border:1px solid #bbf7d0;
                              border-radius:12px;padding:24px;
                              margin:24px 0">
                    <p style="font-size:12px;font-weight:600;
                               color:#059669;
                               text-transform:uppercase;
                               letter-spacing:0.8px;margin:0 0 16px">
                      Vos identifiants
                    </p>
                    <div style="margin-bottom:12px">
                      <span style="font-size:12px;color:#6b7280;
                                   display:block">
                        Email
                      </span>
                      <span style="font-size:15px;font-weight:600;
                                   color:#065f46;
                                   font-family:monospace;
                                   background:white;
                                   padding:6px 12px;
                                   border-radius:6px;
                                   border:1px solid #d1fae5;
                                   display:inline-block;
                                   margin-top:4px">
                        %s
                      </span>
                    </div>
                    <div>
                      <span style="font-size:12px;color:#6b7280;
                                   display:block">
                        Mot de passe
                      </span>
                      <span style="font-size:18px;font-weight:700;
                                   color:#065f46;
                                   font-family:monospace;
                                   background:white;
                                   padding:8px 16px;
                                   border-radius:6px;
                                   border:1px solid #d1fae5;
                                   display:inline-block;
                                   margin-top:4px;
                                   letter-spacing:2px">
                        %s
                      </span>
                    </div>
                  </div>
                  <a href="http://localhost:4200/auth/login"
                     style="display:block;text-align:center;
                            background:linear-gradient(
                              135deg,#059669,#047857);
                            color:white;text-decoration:none;
                            padding:14px 32px;border-radius:10px;
                            font-weight:600;font-size:15px">
                    Se connecter →
                  </a>
                  <p style="text-align:center;color:#9ca3af;
                             font-size:12px;margin-top:20px">
                    ⚠️ Changez votre mot de passe à la
                    première connexion.
                  </p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(app, nom, role, email, mdp);
    }
}