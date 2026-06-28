package com.comptaassist.tva_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Configuration TVA d'un client :
 * - régime (mensuel ou trimestriel)
 * Référence le clientId du cabinet-service.
 */
@Entity
@Table(name = "client_tva_config")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClientTvaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false, unique = true)
    private UUID clientId;

    @Column(name = "cabinet_id", nullable = false)
    private UUID cabinetId;

    @Column(name = "comptable_id", nullable = false)
    private UUID comptableId;

    @Column(nullable = false, columnDefinition = "regime_tva")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RegimeTva regime = RegimeTva.MENSUEL;

    @Builder.Default
    private Boolean actif = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RegimeTva { MENSUEL, TRIMESTRIEL }
}