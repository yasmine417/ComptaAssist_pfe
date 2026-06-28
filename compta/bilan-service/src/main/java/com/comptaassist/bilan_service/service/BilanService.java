package com.comptaassist.bilan_service.service;

import com.comptaassist.bilan_service.dto.AnalyseBilanResponse;
import com.comptaassist.bilan_service.dto.DemandeAnalyseRequest;
import com.comptaassist.bilan_service.entity.AnalyseBilan;
import com.comptaassist.bilan_service.exception.BilanException;
import com.comptaassist.bilan_service.repository.AnalyseBilanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BilanService {

    private final AnalyseBilanRepository analyseRepository;
    private final PythonAiService        pythonAiService;
    private final TendanceService        tendanceService;

    @Transactional
    public AnalyseBilanResponse analyser(
            DemandeAnalyseRequest request, UUID analysePar) {

        if (analyseRepository.existsByClientIdAndExercice(
                request.getClientId(), request.getExercice())) {
            throw new BilanException(
                    "Une analyse existe déjà pour l'exercice "
                            + request.getExercice());
        }

        Map<String, Object> r =
                pythonAiService.analyserBilan(request.getCheminPdf());

        // ── Extraire postes ───────────────────────────────────────────
        Map<String, Object> postes   = extraireMap(r, "postes");
        Map<String, Object> equilibre = extraireMap(r, "equilibre");
        List<Map<String, Object>> ratios = extraireList(r, "ratios");

        // ── Extraire interprétations ──────────────────────────────────
        String interpFRF  = String.valueOf(r.getOrDefault(
                "interpretation_FRF", ""));
        String interpBFG  = String.valueOf(r.getOrDefault(
                "interpretation_BFG", ""));
        String interpTN   = String.valueOf(r.getOrDefault(
                "interpretation_TN", ""));
        String statutTN   = String.valueOf(r.getOrDefault(
                "statut_TN", "BON"));
        String pointsForts = extrairePointsForts(r);
        String anomalies  = extraireAnomalies(r);

        // ── Extraire ratios avec statut et texte ──────────────────────
        String liqGenStatut  = extraireRatioStatut(ratios, "Liquidité générale");
        String liqGenTexte   = extraireRatioTexte(ratios,  "Liquidité générale");
        String liqImmStatut  = extraireRatioStatut(ratios, "Liquidité immédiate");
        String liqImmTexte   = extraireRatioTexte(ratios,  "Liquidité immédiate");
        String autoFinStatut = extraireRatioStatut(ratios, "Autonomie financière");
        String autoFinTexte  = extraireRatioTexte(ratios,  "Autonomie financière");
        String tauxEndStatut = extraireRatioStatut(ratios, "Taux endettement");
        String tauxEndTexte  = extraireRatioTexte(ratios,  "Taux endettement");
        String couverStatut  = extraireRatioStatut(ratios, "Couverture emplois stables");
        String couverTexte   = extraireRatioTexte(ratios,  "Couverture emplois stables");
        String rentComStatut = extraireRatioStatut(ratios, "Rentabilité commerciale");
        String rentComTexte  = extraireRatioTexte(ratios,  "Rentabilité commerciale");
        String rentFinStatut = extraireRatioStatut(ratios, "Rentabilité financière");
        String rentFinTexte  = extraireRatioTexte(ratios,  "Rentabilité financière");

        // ── Construire l'entité ───────────────────────────────────────
        AnalyseBilan analyse = AnalyseBilan.builder()
                .clientId(request.getClientId())
                .documentId(request.getDocumentId())
                .cabinetId(request.getCabinetId())
                .analysePar(analysePar)
                .exercice(request.getExercice())
                // Masses
                .actifImmobilise(toDouble(postes, "actif_immobilise"))
                .actifCirculantHT(toDouble(postes, "actif_circulant_ht"))
                .tresorerieActif(toDouble(postes, "tresorerie_actif"))
                .totalActif(toDouble(postes, "total_actif"))
                .financementPermanent(toDouble(postes, "financement_permanent"))
                .passifCirculantHT(toDouble(postes, "passif_circulant_ht"))
                .tresoreriePassif(toDouble(postes, "tresorerie_passif"))
                .totalPassif(toDouble(postes, "total_passif"))
                // Postes clés
                .capitauxPropres(toDouble(postes, "capitaux_propres"))
                .resultatNet(toDouble(postes, "resultat_net"))
                .caOuPrimes(toDouble(postes, "ca_ou_primes"))
                .dettesLt(toDouble(postes, "dettes_lt"))
                .stocks(toDouble(postes, "stocks"))
                .creances(toDouble(postes, "creances"))
                // Équilibre
                .frf(toDouble(equilibre, "FRF"))
                .bfg(toDouble(equilibre, "BFG"))
                .tn(toDouble(equilibre, "TN_methode1"))
                .tnMethode2(toDouble(equilibre, "TN_methode2"))
                .coherence(Boolean.TRUE.equals(equilibre.get("coherence")))
                // Interprétations
                .interpretationFRF(interpFRF)
                .interpretationBGF(interpBFG)
                .interpretationTN(interpTN)
                .statutTN(statutTN)
                // Ratios
                .liquiditeGenerale(extraireRatioValeur(ratios, "Liquidité générale"))
                .liquiditeGeneraleStatut(liqGenStatut)
                .liquiditeGeneraleTexte(liqGenTexte)
                .liquiditeImmediate(extraireRatioValeur(ratios, "Liquidité immédiate"))
                .liquiditeImmediateStatut(liqImmStatut)
                .liquiditeImmediateTexte(liqImmTexte)
                .autonomieFinanciere(extraireRatioValeur(ratios, "Autonomie financière"))
                .autonomieFinanciereStatut(autoFinStatut)
                .autonomieFinanciereTexte(autoFinTexte)
                .tauxEndettement(extraireRatioValeur(ratios, "Taux endettement"))
                .tauxEndettementStatut(tauxEndStatut)
                .tauxEndettementTexte(tauxEndTexte)
                .couvertureEmplois(extraireRatioValeur(ratios, "Couverture emplois stables"))
                .couvertureEmploisStatut(couverStatut)
                .couvertureEmploisTexte(couverTexte)
                .rentabiliteCommerciale(extraireRatioValeur(ratios, "Rentabilité commerciale"))
                .rentabiliteCommercialeStatut(rentComStatut)
                .rentabiliteCommercialeTexte(rentComTexte)
                .rentabiliteFinanciere(extraireRatioValeur(ratios, "Rentabilité financière"))
                .rentabiliteFinanciereStatut(rentFinStatut)
                .rentabiliteFinanciereTexte(rentFinTexte)
                // Résumé
                .anomalies(anomalies)
                .pointsForts(pointsForts)
                .conclusion(String.valueOf(r.getOrDefault("conclusion", "")))
                .build();

        analyse = analyseRepository.save(analyse);
        tendanceService.detecterEtSauvegarder(
                request.getClientId(), request.getCabinetId());

        return toResponse(analyse);
    }






    public List<AnalyseBilanResponse> getHistoriqueClient(
            UUID clientId) {
        return analyseRepository
                .findAllByClientIdOrderByExerciceDesc(clientId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AnalyseBilanResponse getById(UUID id) {
        return toResponse(analyseRepository.findById(id)
                .orElseThrow(() ->
                        new BilanException("Analyse introuvable")));
    }

    public List<AnalyseBilanResponse> getByCabinet(UUID cabinetId) {
        return analyseRepository.findAllByCabinetId(cabinetId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void supprimer(UUID id) {
        AnalyseBilan a = analyseRepository.findById(id)
                .orElseThrow(() ->
                        new BilanException("Analyse introuvable"));
        analyseRepository.delete(a);
    }

    // ── Méthodes utilitaires ──────────────────────────────────────

    private Map<String, Object> extraireMap(
            Map<String, Object> source, String cle) {
        Object val = source.get(cle);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return Map.of();
    }

    private List<Map<String, Object>> extraireList(
            Map<String, Object> source, String cle) {
        Object val = source.get(cle);
        if (val instanceof List) {
            return (List<Map<String, Object>>) val;
        }
        return List.of();
    }

    private Double toDouble(Map<String, Object> map, String cle) {
        if (map == null || !map.containsKey(cle)) return null;
        Object val = map.get(cle);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Double extraireRatio(
            List<Map<String, Object>> ratios, String nom) {
        if (ratios == null) return null;
        return ratios.stream()
                .filter(r -> nom.equals(r.get("nom")))
                .findFirst()
                .map(r -> {
                    Object val = r.get("valeur");
                    if (val instanceof Number)
                        return ((Number) val).doubleValue();
                    return null;
                })
                .orElse(null);
    }

    private String extraireAnomalies(Map<String, Object> resultat) {
        Object anomalies = resultat.get("anomalies");
        if (anomalies instanceof List) {
            return anomalies.toString();
        }
        return "";
    }

    private AnalyseBilanResponse toResponse(AnalyseBilan a) {
        return AnalyseBilanResponse.builder()
                .id(a.getId())
                .clientId(a.getClientId())
                .documentId(a.getDocumentId())
                .cabinetId(a.getCabinetId())
                .exercice(a.getExercice())
                // Masses
                .actifImmobilise(a.getActifImmobilise())
                .actifCirculantHT(a.getActifCirculantHT())
                .tresorerieActif(a.getTresorerieActif())
                .totalActif(a.getTotalActif())
                .financementPermanent(a.getFinancementPermanent())
                .passifCirculantHT(a.getPassifCirculantHT())
                .tresoreriePassif(a.getTresoreriePassif())
                .totalPassif(a.getTotalPassif())
                // Postes clés
                .capitauxPropres(a.getCapitauxPropres())
                .resultatNet(a.getResultatNet())
                .caOuPrimes(a.getCaOuPrimes())
                .dettesLt(a.getDettesLt())
                .stocks(a.getStocks())
                .creances(a.getCreances())
                // Équilibre
                .frf(a.getFrf())
                .bfg(a.getBfg())
                .tn(a.getTn())
                .tnMethode2(a.getTnMethode2())
                .coherence(a.getCoherence())
                // Interprétations
                .interpretationFRF(a.getInterpretationFRF())
                .interpretationBFG(a.getInterpretationBGF())
                .interpretationTN(a.getInterpretationTN())
                .statutTN(a.getStatutTN())
                // Ratios
                .liquiditeGenerale(a.getLiquiditeGenerale())
                .liquiditeGeneraleStatut(a.getLiquiditeGeneraleStatut())
                .liquiditeGeneraleTexte(a.getLiquiditeGeneraleTexte())
                .liquiditeImmediate(a.getLiquiditeImmediate())
                .liquiditeImmediateStatut(a.getLiquiditeImmediateStatut())
                .liquiditeImmediateTexte(a.getLiquiditeImmediateTexte())
                .autonomieFinanciere(a.getAutonomieFinanciere())
                .autonomieFinanciereStatut(a.getAutonomieFinanciereStatut())
                .autonomieFinanciereTexte(a.getAutonomieFinanciereTexte())
                .tauxEndettement(a.getTauxEndettement())
                .tauxEndettementStatut(a.getTauxEndettementStatut())
                .tauxEndettementTexte(a.getTauxEndettementTexte())
                .couvertureEmplois(a.getCouvertureEmplois())
                .couvertureEmploisStatut(a.getCouvertureEmploisStatut())
                .couvertureEmploisTexte(a.getCouvertureEmploisTexte())
                .rentabiliteCommerciale(a.getRentabiliteCommerciale())
                .rentabiliteCommercialeStatut(a.getRentabiliteCommercialeStatut())
                .rentabiliteCommercialeTexte(a.getRentabiliteCommercialeTexte())
                .rentabiliteFinanciere(a.getRentabiliteFinanciere())
                .rentabiliteFinanciereStatut(a.getRentabiliteFinanciereStatut())
                .rentabiliteFinanciereTexte(a.getRentabiliteFinanciereTexte())
                // Résumé
                .anomalies(a.getAnomalies())
                .pointsForts(a.getPointsForts())
                .conclusion(a.getConclusion())
                .createdAt(a.getCreatedAt())
                .build();
    }


    private String extraireRatioStatut(
            List<Map<String, Object>> ratios, String nom) {
        return ratios.stream()
                .filter(r -> nom.equals(r.get("nom")))
                .findFirst()
                .map(r -> String.valueOf(r.getOrDefault("statut", "?")))
                .orElse("?");
    }

    private String extraireRatioTexte(
            List<Map<String, Object>> ratios, String nom) {
        return ratios.stream()
                .filter(r -> nom.equals(r.get("nom")))
                .findFirst()
                .map(r -> String.valueOf(r.getOrDefault("texte", "")))
                .orElse("");
    }

    private Double extraireRatioValeur(
            List<Map<String, Object>> ratios, String nom) {
        return ratios.stream()
                .filter(r -> nom.equals(r.get("nom")))
                .findFirst()
                .map(r -> {
                    Object val = r.get("valeur");
                    if (val instanceof Number)
                        return ((Number) val).doubleValue();
                    return null;
                })
                .orElse(null);
    }

    private String extrairePointsForts(Map<String, Object> r) {
        Object pts = r.get("points_forts");
        if (pts instanceof List) return pts.toString();
        return "";
    }
}