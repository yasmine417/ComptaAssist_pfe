package com.comptaassist.facture_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table centrale du journal comptable.
 * Toutes les écritures passent par cette table.
 * Structure similaire à Sage / Odoo.
 *
 * Journaux :
 *   VE = Ventes
 *   AC = Achats
 *   BQ = Banque
 *   CA = Caisse
 *   OD = Opérations diverses
 */
@Entity
@Table(name = "ecritures_comptables", indexes = {
        @Index(name = "idx_ec_cabinet",   columnList = "cabinet_id"),
        @Index(name = "idx_ec_journal",   columnList = "journal"),
        @Index(name = "idx_ec_compte",    columnList = "compte"),
        @Index(name = "idx_ec_exercice",  columnList = "exercice"),
        @Index(name = "idx_ec_piece",     columnList = "reference_piece"),
        @Index(name = "idx_ec_date",      columnList = "date_ecriture"),
})
@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class EcritureComptable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Identification cabinet / société ──────────────
    @Column(name = "cabinet_id", nullable = false)
    private UUID cabinetId;

    @Column(name = "client_id")
    private UUID clientId;       // client du cabinet

    @Column(name = "comptable_id")
    private UUID comptableId;

    // ── En-tête de l'écriture ─────────────────────────
    /**
     * Code journal : VE | AC | BQ | CA | OD
     */
    @Column(name = "journal", length = 5, nullable = false)
    private String journal;

    @Column(name = "date_ecriture", nullable = false)
    private LocalDate dateEcriture;

    /**
     * Exercice comptable : ex. "2024", "2025"
     */
    @Column(name = "exercice", length = 4, nullable = false)
    private String exercice;

    /**
     * Numéro de séquence dans le journal pour la période.
     * Ex: "VE-2024-001", "AC-2024-042"
     */
    @Column(name = "numero_sequence", length = 30)
    private String numeroSequence;

    // ── Pièce justificative ───────────────────────────
    /**
     * Référence de la pièce source : numéro de facture,
     * référence virement, etc.
     */
    @Column(name = "reference_piece", length = 100)
    private String referencePiece;

    /**
     * Type de pièce : FACTURE_VENTE | FACTURE_ACHAT |
     * AVOIR | VIREMENT | CHEQUE | ESPECES | RAPPROCHEMENT
     */
    @Column(name = "type_piece", length = 30)
    private String typePiece;

    /**
     * ID de la facture source dans factures_cpc
     */
    @Column(name = "facture_id")
    private UUID factureId;

    // ── Ligne d'écriture ──────────────────────────────
    /**
     * Numéro de ligne dans l'écriture (1, 2, 3...)
     * Toutes les lignes d'une même écriture partagent
     * le même referencePiece.
     */
    @Column(name = "num_ligne")
    private Integer numLigne;

    /**
     * Compte PCG : 3421, 4411, 4455, 6111, 7122...
     */
    @Column(name = "compte", length = 10, nullable = false)
    private String compte;

    /**
     * Intitulé du compte (ex: "Clients")
     */
    @Column(name = "intitule_compte", length = 100)
    private String intituleCompte;

    /**
     * Libellé de la ligne (ex: "Fac. F-001 — SARL ALPHA")
     */
    @Column(name = "libelle", length = 255)
    private String libelle;

    @Column(name = "debit",  precision = 15, scale = 2)
    private BigDecimal debit;

    @Column(name = "credit", precision = 15, scale = 2)
    private BigDecimal credit;

    /**
     * Devise : MAD | EUR | USD
     */
    @Column(name = "devise", length = 5)
    private String devise;

    // ── Tiers ─────────────────────────────────────────
    @Column(name = "tiers_nom", length = 200)
    private String tiersNom;

    @Column(name = "tiers_ice", length = 20)
    private String tiersIce;

    // ── Lettrage (rapprochement) ───────────────────────
    /**
     * Code de lettrage pour rapprocher débit/crédit.
     * Ex: "A1", "B4" — null si non lettré
     */
    @Column(name = "lettrage", length = 10)
    private String lettrage;

    @Column(name = "date_lettrage")
    private LocalDate dateLettrage;

    // ── Statut ────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_ecriture")
    private StatutEcriture statutEcriture;

    // ── Audit ─────────────────────────────────────────
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (statutEcriture == null)
            statutEcriture = StatutEcriture.PROVISOIRE;
        if (debit  == null) debit  = BigDecimal.ZERO;
        if (credit == null) credit = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum StatutEcriture {
        PROVISOIRE,   // générée automatiquement, modifiable
        VALIDEE,      // validée par le comptable
        LETTREE,      // rapprochée avec un paiement
        EXTOURNEE     // annulée par extourne
    }
}