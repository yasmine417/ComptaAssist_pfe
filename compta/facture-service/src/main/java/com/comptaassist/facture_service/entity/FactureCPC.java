package com.comptaassist.facture_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "factures_cpc")
@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class FactureCPC {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID   documentId;
    private String minioObject;
    private String nomOriginal;

    private UUID cabinetId;
    private UUID clientId;
    private UUID comptableId;

    private String    numeroFacture;
    private LocalDate dateFacture;
    private String    fournisseur;
    private String    client;
    private String    ice;
    private String    ifFournisseur;
    private String    rc;
    private String    siret;
    private String    devise;
    private String    pays;

    private String    typeFacture;
    private String    typeFactureDetecte;
    private String    typeOperation;
    private String    modePaiement;
    private LocalDate echeance;

    private Double montantHt;
    private Double tvaTaux;
    private Double montantTva;
    private Double remise;
    private Double montantTtc;

    private Double fraisPortHt;
    private Double fraisPortTva;
    private Double fraisPortTtc;
    private Double autresFrais;
    private Double montantTvaMerch;
    private Double montantTtcHorsPort;

    private Boolean valide;
    private Double  scoreConfiance;
    private Double  confianceMontants;
    private String  regleResolution;
    private Boolean coherenceOk;
    private Double  ecartCoherence;
    private Boolean tvaLueSurFacture;
    private Boolean tvaEtaitTotale;
    private Boolean ecritureEquilibree;

    // ── Confirmation immobilisation / charge ──────────────────
    // Renseigné par Python quand le prix unitaire ≥ 5 000 MAD
    @Column(name = "confirmation_requise")
    private Boolean confirmationRequise = false;

    @Column(name = "type_ecriture", length = 30)
    private String typeEcriture;   // PRODUIT | CHARGE | IMMOBILISATION

    @Column(name = "compte_charge_alternatif", length = 20)
    private String compteChargeAlternatif; // compte 6xxx si l'utilisateur choisit CHARGE

    @Column(name = "libelle_alternatif", length = 200)
    private String libelleAlternatif;

    private String journal;
    private String compteCharge;
    private String compteTva;
    private String compteTiers;
    private String libelleCompte;

    // ── Paiement (rapprochement bancaire) ────────────
    @Column(name = "date_paiement")
    private LocalDate datePaiement;

    @Column(name = "mode_paiement_reel")
    private String modePaiementReel;

    @Column(name = "reference_virement")
    private String referenceVirement;

    @Column(name = "montant_paye")
    private Double montantPaye;

    @Column(name = "paiement_complet")
    private Boolean paiementComplet;

    @Column(name = "paiement_partiel")
    private Boolean paiementPartiel;

    @Column(name = "reste_a_payer")
    private Double resteAPayer;

    // ── Workflow :
    // NOUVEAU → ENREGISTRE → APPROUVE → PAYE
    //                      ↘ REJETE
    // (EN_ATTENTE_PAIEMENT supprimé : le client paie
    //  indépendamment, le comptable fait le
    //  rapprochement bancaire quand il reçoit l'avis)
    @Enumerated(EnumType.STRING)
    private StatutFacture statut;

    @Column(columnDefinition = "TEXT")
    private String alertes;

    @Column(columnDefinition = "TEXT")
    private String warnings;

    @Column(columnDefinition = "TEXT")
    private String itemsJson;

    @Column(columnDefinition = "TEXT")
    private String ecritureComptableJson;

    @Column(columnDefinition = "TEXT")
    private String montantsBrutsJson;

    @Column(columnDefinition = "TEXT")
    private String cpcJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (statut == null)
            statut = StatutFacture.NOUVEAU;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum StatutFacture {
        NOUVEAU,
        ENREGISTRE,
        APPROUVE,
        REJETE,
        PAIEMENT_PARTIEL,
        PAYE
    }
}