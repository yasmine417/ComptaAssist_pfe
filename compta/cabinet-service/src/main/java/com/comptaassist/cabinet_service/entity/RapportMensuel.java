package com.comptaassist.cabinet_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rapports_mensuels")
@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class RapportMensuel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID clientId;
    private UUID cabinetId;
    private UUID comptableId;

    private String nomEntreprise;
    private String moisLabel;

    @Column(columnDefinition = "TEXT")
    private String contenuHtml;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}