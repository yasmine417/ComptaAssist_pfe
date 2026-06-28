package com.comptaassist.cabinet_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signatures_electroniques")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignatureElectronique {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String cabinetId;
    private String clientId;
    private String clientEmail;
    private String clientNom;

    @Enumerated(EnumType.STRING)
    private TypeDocument typeDocument;

    // OpenSign data
    private String openSignDocumentId;  // ID du document chez OpenSign
    private String openSignSigningUrl;   // URL de signature envoyée au client
    private String openSignStatus;       // OUT_FOR_SIGNATURE, COMPLETED, etc.

    @Enumerated(EnumType.STRING)
    private StatutSignature statut;

    private String signedDocumentUrl;    // URL du PDF signé (après signature)
    private String auditTrailUrl;        // URL du certificat

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime signedAt;
    private LocalDateTime expiresAt;

    public enum TypeDocument {
        LETTRE_MISSION,
        MANDAT_TVA,
        APPROBATION_BILAN
    }

    public enum StatutSignature {
        ENVOYE,
        VU,
        SIGNE,
        REFUSE,
        EXPIRE
    }
}