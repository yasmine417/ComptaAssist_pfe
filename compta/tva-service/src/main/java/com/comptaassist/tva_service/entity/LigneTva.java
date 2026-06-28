package com.comptaassist.tva_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ligne_tva")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LigneTva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaration_id", nullable = false)
    @ToString.Exclude
    private DeclarationTva declaration;

    // Taux TVA marocain : 20, 14, 10, 7
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taux;

    // Achats (TVA déductible)
    @Column(name = "base_ht_achats", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal baseHtAchats  = BigDecimal.ZERO;

    @Column(name = "tva_deductible", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal tvaDeductible = BigDecimal.ZERO;

    // Ventes (TVA collectée)
    @Column(name = "base_ht_ventes", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal baseHtVentes  = BigDecimal.ZERO;

    @Column(name = "tva_collectee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal tvaCollectee  = BigDecimal.ZERO;

    // Compteurs de factures sources
    @Column(name = "nb_factures_achat")
    @Builder.Default
    private Integer nbFacturesAchat = 0;

    @Column(name = "nb_factures_vente")
    @Builder.Default
    private Integer nbFacturesVente = 0;
}