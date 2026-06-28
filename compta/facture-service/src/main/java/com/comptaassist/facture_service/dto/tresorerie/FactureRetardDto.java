package com.comptaassist.facture_service.dto.tresorerie;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data @Builder
public class FactureRetardDto {
    private String id;
    private String tiers;
    private String numeroFacture;
    private String echeance;
    private int    joursRetard;
    private double montantDu;
    private String devise;
    private String type;    // CREANCE | DETTE
    private String statut;
}