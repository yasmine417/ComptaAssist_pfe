package com.comptaassist.facture_service.dto.tresorerie;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data @Builder
public class TresorerieMoisDto {
    private String mois;
    private String moisLabel;
    private double encaissements;
    private double decaissements;
    private double solde;
}
