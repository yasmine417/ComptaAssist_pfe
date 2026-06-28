package com.comptaassist.facture_service.dto;

import com.comptaassist.facture_service.entity
        .FactureCPC.StatutFacture;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class FactureCPCResponse {

    private UUID   id;
    private UUID   documentId;
    private String minioObject;
    private String nomOriginal;
    private UUID   clientId;
    private UUID   cabinetId;
    private UUID   comptableId;

    // Données facture
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

    // Type
    private String    typeFacture;
    private String    typeFactureDetecte;
    private String    typeOperation;
    private String    modePaiement;
    private LocalDate echeance;

    // Montants
    private Double montantHt;
    private Double tvaTaux;
    private Double montantTva;
    private Double remise;
    private Double montantTtc;

    // Frais de port V3
    private Double fraisPortHt;
    private Double fraisPortTva;
    private Double fraisPortTtc;
    private Double autresFrais;
    private Double montantTvaMerch;
    private Double montantTtcHorsPort;

    // PCG
    private String journal;
    private String compteCharge;
    private String compteTva;
    private String compteTiers;
    private String libelleCompte;

    // Validation V3
    private Boolean       valide;
    private Double        scoreConfiance;
    private Double        confianceMontants;
    private String        regleResolution;
    private Boolean       coherenceOk;
    private Double        ecartCoherence;
    private Boolean       tvaLueSurFacture;
    private Boolean       tvaEtaitTotale;
    private Boolean       ecritureEquilibree;

    // Listes
    private List<String>  alertes;
    private List<String>  warnings;
    private List<Object>  items;
    private List<Object>  ecritureComptable;
    private Map<String, Object> montantsBruts;
    private Map<String, Object> cpc;

    // Workflow
    private StatutFacture statut;
    private LocalDateTime createdAt;
    private String        urlTelechargement;

    // Ajouter à la fin du DTO
    private LocalDate  datePaiement;
    private String     modePaiementReel;
    private String     referenceVirement;
    private Double     montantPaye;
    private Boolean    paiementComplet;
    private Boolean    paiementPartiel;
    private Double     resteAPayer;

    // ── Confirmation immobilisation / charge ──────────────────
    // Renseigné quand Python détecte un achat potentiellement
    // immobilisable (prix unitaire ≥ 5 000 MAD)
    private Boolean confirmationRequise;     // true → afficher popup
    private String  typeEcriture;            // PRODUIT | CHARGE | IMMOBILISATION
    private String  compteChargeAlternatif;  // compte 6xxx si choix CHARGE
    private String  libelleAlternatif;       // libellé du compte 6xxx
    private String  confirmationMessage;     // texte de la popup
    private String  confirmationOptionALib;  // "Immobilisation → 2355 (Bilan)"
    private String  confirmationOptionBLib;  // "Charge → 6xxx (CPC)"
}