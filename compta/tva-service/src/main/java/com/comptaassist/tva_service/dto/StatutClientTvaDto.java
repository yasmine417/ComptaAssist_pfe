package com.comptaassist.tva_service.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Statut TVA d'un client pour une période donnée.
 * Utilisé dans la liste clients du dashboard TVA.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatutClientTvaDto {
    private UUID          clientId;
    private String        nomClient;      // enrichi depuis cabinet-service
    private String        ice;            // ICE du client
    private String        regime;         // MENSUEL | TRIMESTRIEL
    private String        periodeLabel;   // "Mai 2026" / "T2 2026"
    private String        statut;         // BROUILLON | SOUMISE | EN_RETARD | VALIDEE
    private BigDecimal    tvaNette;
    private BigDecimal    creditTvaReporte;
    private LocalDate     dateLimite;
    private LocalDateTime dateSoumission;
}