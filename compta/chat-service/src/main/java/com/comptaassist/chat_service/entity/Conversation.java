package com.comptaassist.chat_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ID du comptable
    @Column(name = "comptable_id", nullable = false)
    private UUID comptableId;

    @Column(name = "comptable_nom")
    private String comptableNom;

    @Column(name = "comptable_email")
    private String comptableEmail;

    // ID du client (entreprise)
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "client_nom")
    private String clientNom;

    @Column(name = "client_email")
    private String clientEmail;

    // Cabinet
    @Column(name = "cabinet_id")
    private UUID cabinetId;

    @Column(name = "dernier_message", length = 500)
    private String dernierMessage;

    @Column(name = "date_dernier_message")
    private LocalDateTime dateDernierMessage;

    // Nombre de messages non lus par le comptable
    @Column(name = "non_lus_comptable")
    @Builder.Default
    private int nonLusComptable = 0;

    // Nombre de messages non lus par le client
    @Column(name = "non_lus_client")
    @Builder.Default
    private int nonLusClient = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}