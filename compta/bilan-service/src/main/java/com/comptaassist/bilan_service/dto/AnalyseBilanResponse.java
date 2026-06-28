package com.comptaassist.bilan_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class AnalyseBilanResponse {

    // ── Identifiants ──────────────────────────────────────────────
    private UUID    id;
    private UUID    clientId;
    private UUID    documentId;
    private UUID    cabinetId;
    private Integer exercice;

    // ── Masses du bilan ───────────────────────────────────────────
    private Double actifImmobilise;
    private Double actifCirculantHT;
    private Double tresorerieActif;
    private Double totalActif;
    private Double financementPermanent;
    private Double passifCirculantHT;
    private Double tresoreriePassif;
    private Double totalPassif;

    // ── Postes clés ───────────────────────────────────────────────
    private Double capitauxPropres;
    private Double resultatNet;
    private Double caOuPrimes;
    private Double dettesLt;
    private Double stocks;
    private Double creances;

    // ── Équilibre financier ───────────────────────────────────────
    private Double  frf;
    private Double  bfg;
    private Double  tn;
    private Double  tnMethode2;
    private Boolean coherence;

    // ── Interprétations FRF / BFG / TN ───────────────────────────
    private String interpretationFRF;
    private String interpretationBFG;
    private String interpretationTN;
    private String statutTN;

    // ── Ratios avec statut et texte ───────────────────────────────
    private Double  liquiditeGenerale;
    private String  liquiditeGeneraleStatut;
    private String  liquiditeGeneraleTexte;

    private Double  liquiditeImmediate;
    private String  liquiditeImmediateStatut;
    private String  liquiditeImmediateTexte;

    private Double  autonomieFinanciere;
    private String  autonomieFinanciereStatut;
    private String  autonomieFinanciereTexte;

    private Double  tauxEndettement;
    private String  tauxEndettementStatut;
    private String  tauxEndettementTexte;

    private Double  couvertureEmplois;
    private String  couvertureEmploisStatut;
    private String  couvertureEmploisTexte;

    private Double  rentabiliteCommerciale;
    private String  rentabiliteCommercialeStatut;
    private String  rentabiliteCommercialeTexte;

    private Double  rentabiliteFinanciere;
    private String  rentabiliteFinanciereStatut;
    private String  rentabiliteFinanciereTexte;

    // ── Anomalies ─────────────────────────────────────────────────
    private String anomalies;

    // ── Points forts ──────────────────────────────────────────────
    private String pointsForts;

    // ── Conclusion ────────────────────────────────────────────────
    private String conclusion;

    // ── Metadata ──────────────────────────────────────────────────
    private LocalDateTime createdAt;
}