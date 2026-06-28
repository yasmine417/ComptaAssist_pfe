package com.comptaassist.facture_service.dto.tresorerie;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data @Builder
public class TopTiersDto {
    private String nom;
    private double montantFacture;
    private double montantEncaisse;
    private int    nbFactures;
}