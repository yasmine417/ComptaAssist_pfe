package com.comptaassist.cabinet_service.entity;

// Client.java

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trello_card_id")
    private String trelloCardId;

    @Column(name = "cabinet_id", nullable = false)
    private UUID cabinetId;

    @Column(name = "comptable_id")
    private UUID comptableId;

    @Column(name = "nom_entreprise", nullable = false, length = 200)
    private String nomEntreprise;

    @Column(name = "numero_fiscal", nullable = false, unique = true, length = 50)
    private String numeroFiscal;

    @Column(length = 50)
    private String ice;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telephone;

    @Column(length = 300)
    private String adresse;

    @Column(length = 100)
    private String secteur;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "capital_social")
    private java.math.BigDecimal capitalSocial;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}