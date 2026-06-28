package com.comptaassist.facture_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "liens_client_upload")
@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class LienClientUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Token unique pour le client
    @Column(unique = true, nullable = false)
    private String token;

    private UUID clientId;
    private UUID cabinetId;
    private UUID comptableId;
    private String nomClient;
    private String emailClient;

    // Expiration 30 jours
    private LocalDateTime expiresAt;
    private Boolean actif;
    private Integer nbFichiersUploades;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (actif == null) actif = true;
        if (nbFichiersUploades == null)
            nbFichiersUploades = 0;
        if (expiresAt == null)
            expiresAt = LocalDateTime.now()
                    .plusDays(30);
    }
}