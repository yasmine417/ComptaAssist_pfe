package com.comptaassist.facture_service.dto.tresorerie;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data @Builder
public class PrevisionMoisDto {
    private String mois;
    private String moisLabel;
    private double encaissementsPrevu;
    private double decaissementsPrevu;
    private double soldePrevu;
}