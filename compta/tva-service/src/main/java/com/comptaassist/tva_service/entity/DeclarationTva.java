package com.comptaassist.tva_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "declaration_tva")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeclarationTva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id",    nullable = false)
    private UUID clientId;

    @Column(name = "cabinet_id",   nullable = false)
    private UUID cabinetId;

    @Column(name = "comptable_id", nullable = false)
    private UUID comptableId;

    // ── Période ────────────────────────────────────
    @Column(nullable = false)
    private Integer annee;

    private Integer mois;       // null si trimestriel
    private Integer trimestre;  // 1-4 si trimestriel

    @Column(name = "periode_label", length = 50)
    private String periodeLabel; // "Jan 2026" / "T1 2026"

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "date_limite", nullable = false)
    private LocalDate dateLimite;

    // ── Statut ─────────────────────────────────────
    @Column(nullable = false, columnDefinition = "statut_declaration")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutDeclaration statut = StatutDeclaration.BROUILLON;

    @Column(name = "date_soumission")
    private LocalDateTime dateSoumission;

    @Column(name = "soumis_par")
    private UUID soumispar;

    // ── Montants ───────────────────────────────────
    @Column(name = "tva_collectee_total", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal tvaCollecteeTotal  = BigDecimal.ZERO;

    @Column(name = "tva_deductible_total", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal tvaDeductibleTotal = BigDecimal.ZERO;

    @Column(name = "tva_nette", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal tvaNette           = BigDecimal.ZERO;

    @Column(name = "credit_tva_reporte", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal creditTvaReporte   = BigDecimal.ZERO;

    private String notes;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Lignes par taux ───────────────────────────
    @OneToMany(mappedBy = "declaration",
            cascade  = CascadeType.ALL,
            orphanRemoval = true,
            fetch    = FetchType.EAGER)
    @Builder.Default
    private List<LigneTva> lignes = new ArrayList<>();

    // ── Enums ─────────────────────────────────────
    public enum StatutDeclaration {
        BROUILLON, SOUMISE, EN_RETARD, VALIDEE
    }
}