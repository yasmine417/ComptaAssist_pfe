package com.comptaassist.facture_service.service;

import com.comptaassist.facture_service.dto.tresorerie.*;
import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.entity.FactureCPC.StatutFacture;
import com.comptaassist.facture_service.repository.EcritureComptableRepository;
import com.comptaassist.facture_service.repository.FactureCPCRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
@Service
@Slf4j
@RequiredArgsConstructor
public class TresorerieService {

    private final FactureCPCRepository        repo;
    private final EcritureComptableRepository ecritureRepo;

    // ─────────────────────────────────────────────────
    // DASHBOARD PRINCIPAL — toutes les données d'un coup
    // ─────────────────────────────────────────────────
    public DashboardTresorerieDto getDashboard(UUID comptableId, UUID clientId) {

        // Filtrer par client si spécifié, sinon toutes les factures du comptable
        List<FactureCPC> toutes = clientId != null
                ? repo.findByClientIdOrderByDateFactureDesc(clientId)
                : repo.findByComptableIdOrderByCreatedAtDesc(comptableId);

        UUID cabinetId = toutes.stream()
                .map(FactureCPC::getCabinetId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(comptableId);

        double soldeBanqueReel = _soldeBanqueReel(cabinetId);

        return DashboardTresorerieDto.builder()
                .kpis(getKpisAvecSoldeReel(toutes, soldeBanqueReel))
                .evolutionCa(getEvolutionCa(toutes, 6))
                .evolutionTresorerie(getEvolutionTresorerie(toutes, 6))
                .agingCreances(getAgingCreances(toutes))
                .topClients(getTopClients(toutes, 5))
                .topFournisseurs(getTopFournisseurs(toutes, 5))
                .previsionsTresorerie(getPrevisions(toutes, 3))
                .derniersEncaissements(getDerniersEncaissements(toutes, 10))
                .facturesEnRetard(getFacturesEnRetard(toutes, 20))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // Surcharge sans clientId pour compatibilité
    public DashboardTresorerieDto getDashboard(UUID comptableId) {
        return getDashboard(comptableId, null);
    }

    /**
     * Calcule le solde réel du compte banque (5141) depuis
     * les écritures comptables — pas depuis les factures.
     * Débits 5141 = encaissements reçus
     * Crédits 5141 = décaissements effectués
     */
    private double _soldeBanqueReel(UUID cabinetId) {
        try {
            Double solde = ecritureRepo.findSoldeTresorerie(cabinetId);
            double result = solde != null ? solde : 0.0;
            log.info("Solde banque réel cabinet={} : {} MAD", cabinetId, result);
            return result;
        } catch (Exception e) {
            log.warn("Impossible de calculer le solde banque réel : {}", e.getMessage());
            return 0.0;
        }
    }

    // ─────────────────────────────────────────────────
    // KPIs
    // ─────────────────────────────────────────────────
    public KpisTresorerieDto getKpis(List<FactureCPC> toutes) {
        return getKpisAvecSoldeReel(toutes, 0.0);
    }

    public KpisTresorerieDto getKpisAvecSoldeReel(
            List<FactureCPC> toutes, double soldeBanqueReel) {

        LocalDate debut = YearMonth.now().atDay(1);
        LocalDate fin   = YearMonth.now().atEndOfMonth();
        LocalDate now   = LocalDate.now();

        // Factures client (produits / ventes)
        List<FactureCPC> ventes = toutes.stream()
                .filter(f -> isVente(f))
                .toList();

        // Factures fournisseur (charges)
        List<FactureCPC> achats = toutes.stream()
                .filter(f -> isAchat(f))
                .toList();

        // CA du mois (facturé)
        double caFactureMoisHt = ventes.stream()
                .filter(f -> f.getDateFacture() != null
                        && !f.getDateFacture().isBefore(debut)
                        && !f.getDateFacture().isAfter(fin))
                .mapToDouble(f -> nvl(f.getMontantHt()))
                .sum();

        double caFactureMoisTtc = ventes.stream()
                .filter(f -> f.getDateFacture() != null
                        && !f.getDateFacture().isBefore(debut)
                        && !f.getDateFacture().isAfter(fin))
                .mapToDouble(f -> nvl(f.getMontantTtc()))
                .sum();

        // CA encaissé (payé)
        double caEncaisseMois = ventes.stream()
                .filter(f -> StatutFacture.PAYE.equals(f.getStatut())
                        && f.getDatePaiement() != null
                        && !f.getDatePaiement().isBefore(debut)
                        && !f.getDatePaiement().isAfter(fin))
                .mapToDouble(f -> nvl(f.getMontantPaye()))
                .sum();

        // Décaissements du mois (achats payés)
        double decaissementsMois = achats.stream()
                .filter(f -> StatutFacture.PAYE.equals(f.getStatut())
                        && f.getDatePaiement() != null
                        && !f.getDatePaiement().isBefore(debut)
                        && !f.getDatePaiement().isAfter(fin))
                .mapToDouble(f -> nvl(f.getMontantPaye()))
                .sum();

        // Trésorerie nette du mois
        double tresorerieNette = caEncaisseMois - decaissementsMois;

        // Créances totales (ventes non payées approuvées)
        double creancesTotales = ventes.stream()
                .filter(f -> StatutFacture.APPROUVE.equals(f.getStatut())
                        || StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut()))
                .mapToDouble(f -> {
                    if (StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut()))
                        return nvl(f.getResteAPayer());
                    return nvl(f.getMontantTtc());
                })
                .sum();

        // Dettes totales (achats non payés approuvés)
        double dettesTotales = achats.stream()
                .filter(f -> StatutFacture.APPROUVE.equals(f.getStatut())
                        || StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut()))
                .mapToDouble(f -> {
                    if (StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut()))
                        return nvl(f.getResteAPayer());
                    return nvl(f.getMontantTtc());
                })
                .sum();

        // Factures en retard (échéance dépassée, pas payées)
        long facturesEnRetard = toutes.stream()
                .filter(f -> !StatutFacture.PAYE.equals(f.getStatut())
                        && !StatutFacture.REJETE.equals(f.getStatut())
                        && f.getEcheance() != null
                        && f.getEcheance().isBefore(now))
                .count();

        // Taux d'encaissement
        double tauxEncaissement = caFactureMoisTtc > 0
                ? (caEncaisseMois / caFactureMoisTtc) * 100
                : 0;

        // Trésorerie nette du mois (flux)
        double tresorerieNetteMois = caEncaisseMois - decaissementsMois;

        return KpisTresorerieDto.builder()
                .caFactureMoisHt(round(caFactureMoisHt))
                .caFactureMoisTtc(round(caFactureMoisTtc))
                .caEncaisseMois(round(caEncaisseMois))
                .decaissementsMois(round(decaissementsMois))
                // soldeBanqueReel = solde compte 5141 depuis journal (vérité comptable)
                // tresorerieNette = flux du mois (encaissements - décaissements)
                .tresorerieNette(soldeBanqueReel > 0
                        ? round(soldeBanqueReel)
                        : round(tresorerieNetteMois))
                .soldeBanqueReel(round(soldeBanqueReel))
                .creancesTotales(round(creancesTotales))
                .dettesTotales(round(dettesTotales))
                .facturesEnRetard((int) facturesEnRetard)
                .tauxEncaissement(round(tauxEncaissement))
                .mois(YearMonth.now().toString())
                .build();
    }

    // ─────────────────────────────────────────────────
    // ÉVOLUTION CA sur N mois
    // ─────────────────────────────────────────────────
    public List<EvolutionMoisDto> getEvolutionCa(
            List<FactureCPC> toutes, int nbMois) {

        List<EvolutionMoisDto> result = new ArrayList<>();
        List<FactureCPC> ventes = toutes.stream()
                .filter(this::isVente).toList();

        for (int i = nbMois - 1; i >= 0; i--) {
            YearMonth ym    = YearMonth.now().minusMonths(i);
            LocalDate debut = ym.atDay(1);
            LocalDate fin   = ym.atEndOfMonth();

            double facture = ventes.stream()
                    .filter(f -> f.getDateFacture() != null
                            && !f.getDateFacture().isBefore(debut)
                            && !f.getDateFacture().isAfter(fin))
                    .mapToDouble(f -> nvl(f.getMontantHt()))
                    .sum();

            double encaisse = ventes.stream()
                    .filter(f -> StatutFacture.PAYE.equals(f.getStatut())
                            && f.getDatePaiement() != null
                            && !f.getDatePaiement().isBefore(debut)
                            && !f.getDatePaiement().isAfter(fin))
                    .mapToDouble(f -> nvl(f.getMontantPaye()))
                    .sum();

            result.add(EvolutionMoisDto.builder()
                    .mois(ym.toString())
                    .moisLabel(labelMois(ym))
                    .caFacture(round(facture))
                    .caEncaisse(round(encaisse))
                    .build());
        }
        return result;
    }

    // ─────────────────────────────────────────────────
    // ÉVOLUTION TRÉSORERIE sur N mois
    // ─────────────────────────────────────────────────
    public List<TresorerieMoisDto> getEvolutionTresorerie(
            List<FactureCPC> toutes, int nbMois) {

        List<TresorerieMoisDto> result = new ArrayList<>();

        for (int i = nbMois - 1; i >= 0; i--) {
            YearMonth ym    = YearMonth.now().minusMonths(i);
            LocalDate debut = ym.atDay(1);
            LocalDate fin   = ym.atEndOfMonth();

            double encaissements = toutes.stream()
                    .filter(f -> isVente(f)
                            && StatutFacture.PAYE.equals(f.getStatut())
                            && f.getDatePaiement() != null
                            && !f.getDatePaiement().isBefore(debut)
                            && !f.getDatePaiement().isAfter(fin))
                    .mapToDouble(f -> nvl(f.getMontantPaye()))
                    .sum();

            double decaissements = toutes.stream()
                    .filter(f -> isAchat(f)
                            && StatutFacture.PAYE.equals(f.getStatut())
                            && f.getDatePaiement() != null
                            && !f.getDatePaiement().isBefore(debut)
                            && !f.getDatePaiement().isAfter(fin))
                    .mapToDouble(f -> nvl(f.getMontantPaye()))
                    .sum();

            result.add(TresorerieMoisDto.builder()
                    .mois(ym.toString())
                    .moisLabel(labelMois(ym))
                    .encaissements(round(encaissements))
                    .decaissements(round(decaissements))
                    .solde(round(encaissements - decaissements))
                    .build());
        }
        return result;
    }

    // ─────────────────────────────────────────────────
    // AGING CRÉANCES (0-30j / 31-60j / 61-90j / 90j+)
    // ─────────────────────────────────────────────────
    public AgingCreancesDto getAgingCreances(List<FactureCPC> toutes) {

        LocalDate now = LocalDate.now();

        List<FactureCPC> enRetard = toutes.stream()
                .filter(f -> isVente(f)
                        && !StatutFacture.PAYE.equals(f.getStatut())
                        && !StatutFacture.REJETE.equals(f.getStatut())
                        && f.getEcheance() != null
                        && f.getEcheance().isBefore(now))
                .toList();

        double tranche0_30  = 0, tranche31_60 = 0;
        double tranche61_90 = 0, tranche90plus = 0;
        int    nb0_30 = 0, nb31_60 = 0, nb61_90 = 0, nb90plus = 0;

        for (FactureCPC f : enRetard) {
            long jours = ChronoUnit.DAYS.between(f.getEcheance(), now);
            double montant = StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut())
                    ? nvl(f.getResteAPayer())
                    : nvl(f.getMontantTtc());

            if (jours <= 30)       { tranche0_30  += montant; nb0_30++; }
            else if (jours <= 60)  { tranche31_60 += montant; nb31_60++; }
            else if (jours <= 90)  { tranche61_90 += montant; nb61_90++; }
            else                   { tranche90plus += montant; nb90plus++; }
        }

        return AgingCreancesDto.builder()
                .tranche0_30(round(tranche0_30)).nb0_30(nb0_30)
                .tranche31_60(round(tranche31_60)).nb31_60(nb31_60)
                .tranche61_90(round(tranche61_90)).nb61_90(nb61_90)
                .tranche90plus(round(tranche90plus)).nb90plus(nb90plus)
                .totalEnRetard(round(tranche0_30 + tranche31_60
                        + tranche61_90 + tranche90plus))
                .build();
    }

    // ─────────────────────────────────────────────────
    // TOP CLIENTS (par CA facturé)
    // ─────────────────────────────────────────────────
    public List<TopTiersDto> getTopClients(
            List<FactureCPC> toutes, int n) {

        return toutes.stream()
                .filter(f -> isVente(f) && f.getClient() != null
                        && !f.getClient().isBlank())
                .collect(Collectors.groupingBy(
                        FactureCPC::getClient,
                        Collectors.toList()))
                .entrySet().stream()
                .map(e -> {
                    List<FactureCPC> fl = e.getValue();
                    double ca = fl.stream()
                            .mapToDouble(f -> nvl(f.getMontantHt())).sum();
                    double encaisse = fl.stream()
                            .filter(f -> StatutFacture.PAYE.equals(f.getStatut()))
                            .mapToDouble(f -> nvl(f.getMontantPaye())).sum();
                    return TopTiersDto.builder()
                            .nom(e.getKey())
                            .montantFacture(round(ca))
                            .montantEncaisse(round(encaisse))
                            .nbFactures(fl.size())
                            .build();
                })
                .sorted(Comparator.comparingDouble(
                        TopTiersDto::getMontantFacture).reversed())
                .limit(n)
                .toList();
    }

    // ─────────────────────────────────────────────────
    // TOP FOURNISSEURS (par montant achats)
    // ─────────────────────────────────────────────────
    public List<TopTiersDto> getTopFournisseurs(
            List<FactureCPC> toutes, int n) {

        return toutes.stream()
                .filter(f -> isAchat(f) && f.getFournisseur() != null
                        && !f.getFournisseur().isBlank())
                .collect(Collectors.groupingBy(
                        FactureCPC::getFournisseur,
                        Collectors.toList()))
                .entrySet().stream()
                .map(e -> {
                    List<FactureCPC> fl = e.getValue();
                    double total = fl.stream()
                            .mapToDouble(f -> nvl(f.getMontantHt())).sum();
                    double paye  = fl.stream()
                            .filter(f -> StatutFacture.PAYE.equals(f.getStatut()))
                            .mapToDouble(f -> nvl(f.getMontantPaye())).sum();
                    return TopTiersDto.builder()
                            .nom(e.getKey())
                            .montantFacture(round(total))
                            .montantEncaisse(round(paye))
                            .nbFactures(fl.size())
                            .build();
                })
                .sorted(Comparator.comparingDouble(
                        TopTiersDto::getMontantFacture).reversed())
                .limit(n)
                .toList();
    }

    // ─────────────────────────────────────────────────
    // PRÉVISIONS TRÉSORERIE (N prochains mois)
    // Basé sur les échéances des factures approuvées
    // ─────────────────────────────────────────────────
    public List<PrevisionMoisDto> getPrevisions(
            List<FactureCPC> toutes, int nbMois) {

        List<PrevisionMoisDto> result = new ArrayList<>();

        for (int i = 0; i < nbMois; i++) {
            YearMonth ym    = YearMonth.now().plusMonths(i);
            LocalDate debut = ym.atDay(1);
            LocalDate fin   = ym.atEndOfMonth();

            // Encaissements prévus : ventes approuvées avec échéance dans le mois
            double prevEncaissements = toutes.stream()
                    .filter(f -> isVente(f)
                            && (StatutFacture.APPROUVE.equals(f.getStatut())
                            || StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut()))
                            && f.getEcheance() != null
                            && !f.getEcheance().isBefore(debut)
                            && !f.getEcheance().isAfter(fin))
                    .mapToDouble(f -> StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut())
                            ? nvl(f.getResteAPayer())
                            : nvl(f.getMontantTtc()))
                    .sum();

            // Décaissements prévus : achats approuvés avec échéance dans le mois
            double prevDecaissements = toutes.stream()
                    .filter(f -> isAchat(f)
                            && (StatutFacture.APPROUVE.equals(f.getStatut())
                            || StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut()))
                            && f.getEcheance() != null
                            && !f.getEcheance().isBefore(debut)
                            && !f.getEcheance().isAfter(fin))
                    .mapToDouble(f -> StatutFacture.PAIEMENT_PARTIEL.equals(f.getStatut())
                            ? nvl(f.getResteAPayer())
                            : nvl(f.getMontantTtc()))
                    .sum();

            result.add(PrevisionMoisDto.builder()
                    .mois(ym.toString())
                    .moisLabel(labelMois(ym))
                    .encaissementsPrevu(round(prevEncaissements))
                    .decaissementsPrevu(round(prevDecaissements))
                    .soldePrevu(round(prevEncaissements - prevDecaissements))
                    .build());
        }
        return result;
    }

    // ─────────────────────────────────────────────────
    // DERNIERS ENCAISSEMENTS
    // ─────────────────────────────────────────────────
    public List<MouvementDto> getDerniersEncaissements(
            List<FactureCPC> toutes, int n) {

        return toutes.stream()
                .filter(f -> StatutFacture.PAYE.equals(f.getStatut())
                        && f.getDatePaiement() != null)
                .sorted(Comparator.comparing(
                        FactureCPC::getDatePaiement).reversed())
                .limit(n)
                .map(f -> MouvementDto.builder()
                        .id(f.getId().toString())
                        .date(f.getDatePaiement().toString())
                        .tiers(isVente(f) ? f.getClient() : f.getFournisseur())
                        .numeroFacture(f.getNumeroFacture())
                        .montant(nvl(f.getMontantPaye()))
                        .type(isVente(f) ? "ENCAISSEMENT" : "DECAISSEMENT")
                        .mode(f.getModePaiementReel())
                        .reference(f.getReferenceVirement())
                        .build())
                .toList();
    }

    // ─────────────────────────────────────────────────
    // FACTURES EN RETARD (triées par retard décroissant)
    // ─────────────────────────────────────────────────
    public List<FactureRetardDto> getFacturesEnRetard(
            List<FactureCPC> toutes, int n) {

        LocalDate now = LocalDate.now();

        return toutes.stream()
                .filter(f -> !StatutFacture.PAYE.equals(f.getStatut())
                        && !StatutFacture.REJETE.equals(f.getStatut())
                        && f.getEcheance() != null
                        && f.getEcheance().isBefore(now))
                .sorted(Comparator.comparing(FactureCPC::getEcheance))
                .limit(n)
                .map(f -> {
                    long jours = ChronoUnit.DAYS.between(f.getEcheance(), now);
                    double montantDu = StatutFacture.PAIEMENT_PARTIEL
                            .equals(f.getStatut())
                            ? nvl(f.getResteAPayer())
                            : nvl(f.getMontantTtc());
                    return FactureRetardDto.builder()
                            .id(f.getId().toString())
                            .tiers(isVente(f) ? f.getClient() : f.getFournisseur())
                            .numeroFacture(f.getNumeroFacture())
                            .echeance(f.getEcheance().toString())
                            .joursRetard((int) jours)
                            .montantDu(round(montantDu))
                            .devise(f.getDevise())
                            .type(isVente(f) ? "CREANCE" : "DETTE")
                            .statut(f.getStatut() != null
                                    ? f.getStatut().name() : "")
                            .build();
                })
                .toList();
    }

    // ─────────────────────────────────────────────────
    // Helpers privés
    // ─────────────────────────────────────────────────

    // Vente = facture client (type_operation contient VENTE ou PRESTATION)
    // Par défaut si non défini, on considère comme achat
    private boolean isVente(FactureCPC f) {
        if (f.getTypeOperation() == null) return false;
        String op = f.getTypeOperation().toUpperCase();
        return op.contains("VENTE")
                || op.contains("PRESTATION_CLIENT")
                || op.contains("AVOIR_CLIENT");
    }

    private boolean isAchat(FactureCPC f) {
        if (f.getTypeOperation() == null) return true; // par défaut
        String op = f.getTypeOperation().toUpperCase();
        return op.contains("ACHAT")
                || op.contains("FOURNISSEUR")
                || op.contains("CHARGE");
    }

    private double nvl(Double v) {
        return v != null ? v : 0.0;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String labelMois(YearMonth ym) {
        String[] mois = {
                "Jan","Fév","Mar","Avr","Mai","Jun",
                "Jul","Aoû","Sep","Oct","Nov","Déc"
        };
        return mois[ym.getMonthValue() - 1] + " " + ym.getYear();
    }
}