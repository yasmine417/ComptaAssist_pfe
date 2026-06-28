package com.comptaassist.bilan_service.service;

import com.comptaassist.bilan_service.dto.TendanceResponse;
import com.comptaassist.bilan_service.entity.AnalyseBilan;
import com.comptaassist.bilan_service.entity.TendanceFinanciere;
import com.comptaassist.bilan_service.repository.AnalyseBilanRepository;
import com.comptaassist.bilan_service.repository.TendanceFinanciereRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TendanceService {

    private final AnalyseBilanRepository       analyseRepository;
    private final TendanceFinanciereRepository tendanceRepository;

    @Transactional
    public List<TendanceFinanciere> detecterEtSauvegarder(
            UUID clientId, UUID cabinetId) {

        List<AnalyseBilan> historique = analyseRepository
                .findTop3ByClientIdOrderByExerciceDesc(clientId);

        if (historique.size() < 2) {
            log.info("Pas assez d'historique pour détecter tendances "
                    + "— client : {}", clientId);
            return new ArrayList<>();
        }

        AnalyseBilan actuelle   = historique.get(0);
        AnalyseBilan precedente = historique.get(1);

        List<TendanceFinanciere> tendances = new ArrayList<>();

        // Règle 1 — FRF en baisse
        if (actuelle.getFrf() != null && precedente.getFrf() != null
                && actuelle.getFrf() < precedente.getFrf()) {
            tendances.add(creerTendance(
                    clientId, cabinetId,
                    "FRF",
                    actuelle.getFrf(),
                    precedente.getFrf(),
                    "FRF_BAISSE",
                    "Le Fonds de Roulement Fonctionnel est en baisse"
                            + " — les ressources stables diminuent"
            ));
        }

        // Règle 2 — TN devient négative
        if (actuelle.getTn() != null && precedente.getTn() != null
                && actuelle.getTn() < 0
                && precedente.getTn() >= 0) {
            tendances.add(creerTendance(
                    clientId, cabinetId,
                    "TN",
                    actuelle.getTn(),
                    precedente.getTn(),
                    "TN_NEGATIVE",
                    "CRITIQUE — Trésorerie nette négative pour"
                            + " la première fois — recours aux crédits bancaires"
            ));
        }

        // Règle 3 — Liquidité générale sous 1
        if (actuelle.getLiquiditeGenerale() != null
                && actuelle.getLiquiditeGenerale() < 1.0) {
            tendances.add(creerTendance(
                    clientId, cabinetId,
                    "LIQUIDITE_GENERALE",
                    actuelle.getLiquiditeGenerale(),
                    precedente.getLiquiditeGenerale() != null
                            ? precedente.getLiquiditeGenerale() : 0.0,
                    "LIQUIDITE_FAIBLE",
                    "Liquidité générale insuffisante"
                            + " — risque de ne pas honorer les dettes à court terme"
            ));
        }

        // Règle 4 — Rentabilité commerciale en baisse 3 ans consécutifs
        if (historique.size() == 3) {
            AnalyseBilan ancienne = historique.get(2);
            if (actuelle.getRentabiliteCommerciale() != null
                    && precedente.getRentabiliteCommerciale() != null
                    && ancienne.getRentabiliteCommerciale() != null
                    && actuelle.getRentabiliteCommerciale()
                    < precedente.getRentabiliteCommerciale()
                    && precedente.getRentabiliteCommerciale()
                    < ancienne.getRentabiliteCommerciale()) {
                tendances.add(creerTendance(
                        clientId, cabinetId,
                        "RENTABILITE_COMMERCIALE",
                        actuelle.getRentabiliteCommerciale(),
                        precedente.getRentabiliteCommerciale(),
                        "RENTABILITE_BAISSE",
                        "Rentabilité commerciale en baisse"
                                + " 3 années consécutives — activité en déclin"
                ));
            }
        }

        // Règle 5 — Autonomie financière sous 0.33
        if (actuelle.getAutonomieFinanciere() != null
                && actuelle.getAutonomieFinanciere() < 0.33) {
            tendances.add(creerTendance(
                    clientId, cabinetId,
                    "AUTONOMIE_FINANCIERE",
                    actuelle.getAutonomieFinanciere(),
                    precedente.getAutonomieFinanciere() != null
                            ? precedente.getAutonomieFinanciere() : 0.0,
                    "LIQUIDITE_FAIBLE",
                    "Autonomie financière faible"
                            + " — dépendance excessive aux dettes"
            ));
        }

        if (!tendances.isEmpty()) {
            tendanceRepository.saveAll(tendances);
            log.info("{} tendances détectées pour client : {}",
                    tendances.size(), clientId);
        }

        return tendances;
    }

    private TendanceFinanciere creerTendance(
            UUID clientId, UUID cabinetId,
            String indicateur, Double valActuelle,
            Double valPrecedente, String typeAlerte,
            String message) {
        return TendanceFinanciere.builder()
                .clientId(clientId)
                .cabinetId(cabinetId)
                .indicateur(indicateur)
                .valeurActuelle(valActuelle)
                .valeurPrecedente(valPrecedente)
                .typeAlerte(typeAlerte)
                .message(message)
                .estTraite(false)
                .build();
    }

    public List<TendanceResponse> getTendancesClient(UUID clientId) {
        return tendanceRepository
                .findAllByClientIdOrderByDateDetectionDesc(clientId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<TendanceResponse> getAlertesNonTraitees(UUID clientId) {
        return tendanceRepository
                .findAllByClientIdAndEstTraiteFalse(clientId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<TendanceResponse> getAlertesNonTraiteesCabinet(
            UUID cabinetId) {
        return tendanceRepository
                .findAllByCabinetIdAndEstTraiteFalse(cabinetId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void marquerTraite(UUID id) {
        TendanceFinanciere t = tendanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Tendance introuvable"));
        t.setEstTraite(true);
        tendanceRepository.save(t);
    }

    private TendanceResponse toResponse(TendanceFinanciere t) {
        return TendanceResponse.builder()
                .id(t.getId())
                .clientId(t.getClientId())
                .indicateur(t.getIndicateur())
                .valeurActuelle(t.getValeurActuelle())
                .valeurPrecedente(t.getValeurPrecedente())
                .typeAlerte(t.getTypeAlerte())
                .message(t.getMessage())
                .estTraite(t.isEstTraite())
                .dateDetection(t.getDateDetection())
                .build();
    }
}