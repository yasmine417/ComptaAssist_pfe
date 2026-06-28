package com.comptaassist.tva_service.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeclarationTvaDto {
    private UUID         id;
    private UUID         clientId;
    private UUID         cabinetId;
    private String       periodeLabel;
    private Integer      annee;
    private Integer      mois;
    private Integer      trimestre;
    private LocalDate    dateDebut;
    private LocalDate    dateFin;
    private LocalDate    dateLimite;
    private String       statut;
    private BigDecimal   tvaCollecteeTotal;
    private BigDecimal   tvaDeductibleTotal;
    private BigDecimal   tvaNette;
    private BigDecimal   creditTvaReporte;
    private LocalDateTime dateSoumission;
    private List<LigneTvaDto> lignes;
}