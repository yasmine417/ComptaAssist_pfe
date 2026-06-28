package com.comptaassist.tva_service.kafka;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Événement envoyé sur Kafka quand une déclaration TVA
 * est en retard (date limite dépassée et non soumise).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TvaRetardEvent {
    private UUID      declarationId;
    private UUID      clientId;
    private UUID      cabinetId;
    private UUID      comptableId;
    private String    periodeLabel;   // "Mai 2026"
    private LocalDate dateLimite;     // date limite dépassée
    private int       joursRetard;    // nombre de jours de retard
    private String    statut;         // EN_RETARD
    private String    type;           // TVA_DECLARATION_EN_RETARD
}