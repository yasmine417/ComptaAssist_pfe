package com.comptaassist.tva_service.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProchainePeriodeDto {
    private UUID      clientId;
    private String    regime;           // MENSUEL | TRIMESTRIEL
    private String    periodeLabel;     // "Mai 2026" ou "T2 2026 (Avr — Jun)"
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private LocalDate dateLimite;       // date limite légale dépôt (avant le 20)
    private boolean   enRetard;         // true si date limite dépassée
    private UUID      declarationExistanteId;     // si brouillon existe déjà
    private String    declarationExistanteStatut; // statut du brouillon existant
}