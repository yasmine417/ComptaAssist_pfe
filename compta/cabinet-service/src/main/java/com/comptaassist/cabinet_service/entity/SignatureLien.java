package com.comptaassist.cabinet_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signature_liens")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SignatureLien {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String signatureId;
    private String token;
    private String clientEmail;
    private String clientNom;

    // PDF original (sans signatures)
    private String pdfMinioKey;

    // PDF avec signature comptable intégrée → envoyé au client
    private String pdfAvecComptableMinioKey;

    // PDF final avec les 2 signatures
    private String pdfSigneMinioKey;

    @Enumerated(EnumType.STRING)
    private StatutLien statut;

    // HTML original pour régénération
    @Column(columnDefinition = "TEXT")
    private String htmlOriginal;

    // Signature comptable
    @Column(columnDefinition = "TEXT")
    private String signatureComptableBase64;
    private LocalDateTime signedAtComptable;
    private String comptableNom;

    // Signature client
    @Column(columnDefinition = "TEXT")
    private String signatureBase64;
    private LocalDateTime signedAt;
    private String ipClient;

    @Column(length = 500)
    private String userAgent;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime expiresAt;

    public enum StatutLien {
        EN_ATTENTE_COMPTABLE,
        EN_ATTENTE_CLIENT,
        SIGNE,
        EXPIRE,
        REFUSE
    }
}