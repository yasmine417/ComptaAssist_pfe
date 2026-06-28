package com.comptaassist.facture_service.dto.tresorerie;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data @Builder
public class AgingCreancesDto {
    private double tranche0_30;   private int nb0_30;
    private double tranche31_60;  private int nb31_60;
    private double tranche61_90;  private int nb61_90;
    private double tranche90plus; private int nb90plus;
    private double totalEnRetard;
}