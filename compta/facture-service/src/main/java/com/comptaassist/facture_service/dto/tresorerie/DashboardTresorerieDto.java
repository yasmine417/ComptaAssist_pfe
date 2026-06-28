package com.comptaassist.facture_service.dto.tresorerie;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class DashboardTresorerieDto {
    private KpisTresorerieDto         kpis;
    private List<EvolutionMoisDto>    evolutionCa;
    private List<TresorerieMoisDto>   evolutionTresorerie;
    private AgingCreancesDto          agingCreances;
    private List<TopTiersDto>         topClients;
    private List<TopTiersDto>         topFournisseurs;
    private List<PrevisionMoisDto>    previsionsTresorerie;
    private List<MouvementDto>        derniersEncaissements;
    private List<FactureRetardDto>    facturesEnRetard;
    private LocalDateTime             generatedAt;
}