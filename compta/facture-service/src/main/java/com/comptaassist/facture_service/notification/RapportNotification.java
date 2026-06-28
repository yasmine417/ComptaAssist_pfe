package com.comptaassist.facture_service.notification;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rapport_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RapportNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String comptableId;
    private String clientId;
    private String nomEntreprise;
    private String moisLabel;
    private String message;
    private boolean lu;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}