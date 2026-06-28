// TendanceFinanciere.java
package com.comptaassist.bilan_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tendance_financiere")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TendanceFinanciere {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "cabinet_id", nullable = false)
    private UUID cabinetId;

    @Column(nullable = false, length = 100)
    private String indicateur;

    @Column(name = "valeur_actuelle", nullable = false)
    private Double valeurActuelle;

    @Column(name = "valeur_precedente", nullable = false)
    private Double valeurPrecedente;

    @Column(name = "type_alerte", nullable = false, length = 50)
    private String typeAlerte;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "est_traite", nullable = false)
    private boolean estTraite = false;

    @CreationTimestamp
    @Column(name = "date_detection", updatable = false)
    private LocalDateTime dateDetection;
}