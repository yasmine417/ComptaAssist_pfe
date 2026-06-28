package com.comptaassist.tva_service.dto;
import lombok.*;
import java.math.BigDecimal;
import lombok.*;
import java.math.BigDecimal;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LigneTvaDto {
    private BigDecimal taux;
    private BigDecimal baseHtAchats;
    private BigDecimal tvaDeductible;
    private BigDecimal baseHtVentes;
    private BigDecimal tvaCollectee;
    private Integer    nbFacturesAchat;
    private Integer    nbFacturesVente;
}