package com.comptaassist.facture_service.dto.tresorerie;


import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
public class KpisTresorerieDto {
    private double caFactureMoisHt;
    private double caFactureMoisTtc;
    private double caEncaisseMois;
    private double decaissementsMois;
    private double tresorerieNette;
    private double soldeBanqueReel;  // Solde réel compte 5141 depuis le journal
    private double creancesTotales;
    private double dettesTotales;
    private int    facturesEnRetard;
    private double tauxEncaissement;  // %
    private String mois;           // "2024-05"
}
