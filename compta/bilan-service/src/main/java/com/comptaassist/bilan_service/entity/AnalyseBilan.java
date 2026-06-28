package com.comptaassist.bilan_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "analyse_bilan")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalyseBilan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "cabinet_id", nullable = false)
    private UUID cabinetId;

    @Column(name = "analyse_par", nullable = false)
    private UUID analysePar;

    @Column(nullable = false)
    private Integer exercice;

    // ── Masses du bilan ───────────────────────────────────────────
    @Column(name = "actif_immobilise")
    private Double actifImmobilise;

    @Column(name = "actif_circulant_ht")
    private Double actifCirculantHT;

    @Column(name = "tresorerie_actif")
    private Double tresorerieActif;

    @Column(name = "total_actif")
    private Double totalActif;

    @Column(name = "financement_permanent")
    private Double financementPermanent;

    @Column(name = "passif_circulant_ht")
    private Double passifCirculantHT;

    @Column(name = "tresorerie_passif")
    private Double tresoreriePassif;

    @Column(name = "total_passif")
    private Double totalPassif;

    // ── Postes clés ───────────────────────────────────────────────
    @Column(name = "capitaux_propres")
    private Double capitauxPropres;

    @Column(name = "resultat_net")
    private Double resultatNet;

    @Column(name = "ca_ou_primes")
    private Double caOuPrimes;

    @Column(name = "dettes_lt")
    private Double dettesLt;

    private Double stocks;
    private Double creances;

    // ── Équilibre ─────────────────────────────────────────────────
    private Double frf;
    private Double bfg;
    private Double tn;

    @Column(name = "tn_methode2")
    private Double tnMethode2;

    private Boolean coherence;

    // ── Interprétations ───────────────────────────────────────────
    @Column(name = "interpretation_frf", columnDefinition = "TEXT")
    private String interpretationFRF;

    @Column(name = "interpretation_bgf", columnDefinition = "TEXT")
    private String interpretationBGF;

    @Column(name = "interpretation_tn", columnDefinition = "TEXT")
    private String interpretationTN;

    @Column(name = "statut_tn")
    private String statutTN;

    // ── Ratios ────────────────────────────────────────────────────
    @Column(name = "liquidite_generale")
    private Double liquiditeGenerale;

    @Column(name = "liquidite_generale_statut")
    private String liquiditeGeneraleStatut;

    @Column(name = "liquidite_generale_texte", columnDefinition = "TEXT")
    private String liquiditeGeneraleTexte;

    @Column(name = "liquidite_immediate")
    private Double liquiditeImmediate;

    @Column(name = "liquidite_immediate_statut")
    private String liquiditeImmediateStatut;

    @Column(name = "liquidite_immediate_texte", columnDefinition = "TEXT")
    private String liquiditeImmediateTexte;

    @Column(name = "autonomie_financiere")
    private Double autonomieFinanciere;

    @Column(name = "autonomie_financiere_statut")
    private String autonomieFinanciereStatut;

    @Column(name = "autonomie_financiere_texte", columnDefinition = "TEXT")
    private String autonomieFinanciereTexte;

    @Column(name = "taux_endettement")
    private Double tauxEndettement;

    @Column(name = "taux_endettement_statut")
    private String tauxEndettementStatut;

    @Column(name = "taux_endettement_texte", columnDefinition = "TEXT")
    private String tauxEndettementTexte;

    @Column(name = "couverture_emplois")
    private Double couvertureEmplois;

    @Column(name = "couverture_emplois_statut")
    private String couvertureEmploisStatut;

    @Column(name = "couverture_emplois_texte", columnDefinition = "TEXT")
    private String couvertureEmploisTexte;

    @Column(name = "rentabilite_commerciale")
    private Double rentabiliteCommerciale;

    @Column(name = "rentabilite_commerciale_statut")
    private String rentabiliteCommercialeStatut;

    @Column(name = "rentabilite_commerciale_texte", columnDefinition = "TEXT")
    private String rentabiliteCommercialeTexte;

    @Column(name = "rentabilite_financiere")
    private Double rentabiliteFinanciere;

    @Column(name = "rentabilite_financiere_statut")
    private String rentabiliteFinanciereStatut;

    @Column(name = "rentabilite_financiere_texte", columnDefinition = "TEXT")
    private String rentabiliteFinanciereTexte;

    // ── Anomalies / Points forts / Conclusion ─────────────────────
    @Column(columnDefinition = "TEXT")
    private String anomalies;

    @Column(name = "points_forts", columnDefinition = "TEXT")
    private String pointsForts;

    @Column(columnDefinition = "TEXT")
    private String conclusion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}