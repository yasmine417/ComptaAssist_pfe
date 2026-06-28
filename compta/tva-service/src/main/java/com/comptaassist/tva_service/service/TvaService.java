package com.comptaassist.tva_service.service;

import com.comptaassist.tva_service.kafka.TvaKafkaProducer;
import com.comptaassist.tva_service.kafka.TvaRetardEvent;
import com.comptaassist.tva_service.client.FactureServiceClient;
import com.comptaassist.tva_service.dto.*;
import com.comptaassist.tva_service.entity.ClientTvaConfig;
import com.comptaassist.tva_service.entity.ClientTvaConfig.RegimeTva;
import com.comptaassist.tva_service.entity.DeclarationTva;
import com.comptaassist.tva_service.entity.DeclarationTva.StatutDeclaration;
import com.comptaassist.tva_service.entity.LigneTva;
import com.comptaassist.tva_service.repository.ClientTvaConfigRepository;
import com.comptaassist.tva_service.repository.DeclarationTvaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TvaService {

    private final DeclarationTvaRepository  repo;
    private final ClientTvaConfigRepository configRepo;
    private final FactureServiceClient      factureClient;
    private final TvaKafkaProducer          kafkaProducer;

    private static final List<BigDecimal> TAUX_TVA = List.of(
            new BigDecimal("20"), new BigDecimal("14"),
            new BigDecimal("10"), new BigDecimal("7"));

    // ══════════════════════════════════════════════════════
    // DASHBOARD
    // ══════════════════════════════════════════════════════
    public DashboardTvaDto getDashboard(UUID cabinetId) {
        List<DeclarationTva> toutes = repo.findByCabinetId(cabinetId);
        return DashboardTvaDto.builder()
                .clientsActifs((int) toutes.stream()
                        .map(DeclarationTva::getClientId).distinct().count())
                .declareesCeMois((int) toutes.stream()
                        .filter(d -> d.getStatut() == StatutDeclaration.SOUMISE
                                || d.getStatut() == StatutDeclaration.VALIDEE)
                        .count())
                .enAttente((int) toutes.stream()
                        .filter(d -> d.getStatut() == StatutDeclaration.BROUILLON)
                        .count())
                .enRetard((int) toutes.stream()
                        .filter(d -> d.getStatut() == StatutDeclaration.EN_RETARD)
                        .count())
                .build();
    }

    // ══════════════════════════════════════════════════════
    // PROCHAINE PÉRIODE À DÉCLARER
    // Logique réaliste cabinet marocain :
    // - Régime mensuel  → déclarer chaque mois avant le 20
    // - Régime trimestriel → déclarer chaque trimestre avant le 20
    // Le système propose automatiquement la prochaine période
    // non encore déclarée.
    // ══════════════════════════════════════════════════════
    public ProchainePeriodeDto calculerProchainePeriode(
            UUID clientId) {

        // 1. Récupérer la config du client (régime)
        // Si pas de config → MENSUEL par défaut (ne pas bloquer)
        ClientTvaConfig config = configRepo.findByClientId(clientId)
                .orElse(null);
        RegimeTva regime = (config != null)
                ? config.getRegime() : RegimeTva.MENSUEL;

        // 2. Trouver la dernière déclaration soumise ou validée
        List<DeclarationTva> historique =
                repo.findByClientIdOrderByDateDebutDesc(clientId);

        LocalDate prochaineDebut;
        LocalDate prochaineFin;

        if (historique.isEmpty()) {
            // Aucune déclaration → proposer la période courante
            prochaineDebut = debutPeriodeCourante(regime);
            prochaineFin   = finPeriode(prochaineDebut, regime);
        } else {
            // Trouver la dernière période déclarée (soumise ou validée)
            Optional<DeclarationTva> derniere = historique.stream()
                    .filter(d -> d.getStatut() == StatutDeclaration.SOUMISE
                            || d.getStatut() == StatutDeclaration.VALIDEE)
                    .findFirst();

            if (derniere.isPresent()) {
                // Prochaine période = période suivant la dernière soumise
                LocalDate candidat = periodesSuivante(
                        derniere.get().getDateFin(), regime);
                LocalDate periodeCourante = debutPeriodeCourante(regime);
                // Ne pas proposer une période dans le futur
                prochaineDebut = candidat.isAfter(periodeCourante)
                        ? periodeCourante : candidat;
                prochaineFin   = finPeriode(prochaineDebut, regime);
            } else {
                // Il y a des brouillons mais rien de soumis
                // → proposer la période courante
                prochaineDebut = debutPeriodeCourante(regime);
                prochaineFin   = finPeriode(prochaineDebut, regime);
            }
        }

        // 3. Vérifier si cette période a déjà un brouillon
        final LocalDate debutFinal = prochaineDebut;
        final LocalDate finFinal   = prochaineFin;

        Optional<DeclarationTva> brouillonExistant = historique.stream()
                .filter(d -> d.getDateDebut().equals(debutFinal))
                .findFirst();

        String periodeLabel = buildPeriodeLabel(prochaineDebut, regime);
        LocalDate dateLimite = calculerDateLimite(prochaineFin);
        boolean enRetard = LocalDate.now().isAfter(dateLimite);

        return ProchainePeriodeDto.builder()
                .clientId(clientId)
                .regime(regime.name())
                .periodeLabel(periodeLabel)
                .dateDebut(prochaineDebut)
                .dateFin(prochaineFin)
                .dateLimite(dateLimite)
                .enRetard(enRetard)
                .declarationExistanteId(
                        brouillonExistant.map(d -> d.getId()).orElse(null))
                .declarationExistanteStatut(
                        brouillonExistant.map(d -> d.getStatut().name()).orElse(null))
                .build();
    }

    // ══════════════════════════════════════════════════════
    // CONFIGURER RÉGIME TVA D'UN CLIENT
    // C'est le comptable qui configure une seule fois
    // le régime du client (mensuel ou trimestriel)
    // ══════════════════════════════════════════════════════
    @Transactional
    public ClientTvaConfigDto configurerRegime(
            UUID clientId, UUID cabinetId,
            UUID comptableId, String regime) {
        RegimeTva r = RegimeTva.valueOf(regime.toUpperCase());
        ClientTvaConfig config = configRepo.findByClientId(clientId)
                .orElseGet(() -> ClientTvaConfig.builder()
                        .clientId(clientId)
                        .cabinetId(cabinetId)
                        .comptableId(comptableId)
                        .build());
        config.setRegime(r);
        configRepo.save(config);
        log.info("Régime TVA configuré : client={} regime={}", clientId, r);
        return ClientTvaConfigDto.builder()
                .clientId(clientId)
                .regime(r.name())
                .build();
    }

    public Optional<ClientTvaConfigDto> getConfigClient(UUID clientId) {
        return configRepo.findByClientId(clientId)
                .map(c -> ClientTvaConfigDto.builder()
                        .clientId(c.getClientId())
                        .regime(c.getRegime().name())
                        .build());
    }

    // ══════════════════════════════════════════════════════
    // CALCULER TVA DEPUIS LES FACTURES IA
    // Le comptable confirme la période proposée (ou l'ajuste)
    // Le système calcule automatiquement depuis les factures
    // ══════════════════════════════════════════════════════
    @Transactional
    public DeclarationTvaDto calculerDepuisFactures(
            UUID clientId, UUID cabinetId, UUID comptableId,
            LocalDate dateDebut, LocalDate dateFin,
            String jwtToken) {

        // Récupérer le régime depuis la config du client
        // Si pas de config → MENSUEL par défaut
        ClientTvaConfig config = configRepo.findByClientId(clientId)
                .orElse(null);
        RegimeTva regime = (config != null)
                ? config.getRegime() : RegimeTva.MENSUEL;
        String periodeLabel = buildPeriodeLabel(dateDebut, regime);

        // 1. Récupérer les factures depuis facture-service
        List<FactureDto> factures =
                factureClient.getFacturesClient(clientId, jwtToken);

        // 2. Filtrer par période
        final LocalDate debutFinal = dateDebut;
        final LocalDate finFinal   = dateFin;

        List<FactureDto> facturesPeriode = factures.stream()
                .filter(f -> f.getDateFacture() != null
                        && !f.getDateFacture().isBefore(debutFinal)
                        && !f.getDateFacture().isAfter(finFinal))
                .toList();

        log.info("TVA client={} période={} → {} factures sur {}",
                clientId, periodeLabel,
                facturesPeriode.size(), factures.size());

        // 3. Accumuler par taux — boucle for
        Map<BigDecimal, LigneTvaCalc> parTaux = new LinkedHashMap<>();
        for (BigDecimal t : TAUX_TVA) {
            parTaux.put(t, new LigneTvaCalc(t));
        }
        for (FactureDto f : facturesPeriode) {
            BigDecimal taux   = resoudreTaux(f.getTvaTaux());
            LigneTvaCalc calc = parTaux.get(taux);
            if (calc == null) continue;
            BigDecimal ht  = nvl(f.getMontantHt());
            BigDecimal tva = nvl(f.getMontantTva());
            if (isVente(f)) {
                calc.baseHtVentes  = calc.baseHtVentes.add(ht);
                calc.tvaCollectee  = calc.tvaCollectee.add(tva);
                calc.nbVentes++;
            } else {
                calc.baseHtAchats  = calc.baseHtAchats.add(ht);
                calc.tvaDeductible = calc.tvaDeductible.add(tva);
                calc.nbAchats++;
            }
        }

        // 4. Construire ou mettre à jour la déclaration
        final Integer moisVal      = (regime == RegimeTva.MENSUEL)
                ? dateDebut.getMonthValue() : null;
        final Integer trimestreVal = (regime == RegimeTva.TRIMESTRIEL)
                ? getTrimestre(dateDebut) : null;
        final LocalDate dateLimiteVal = calculerDateLimite(dateFin);

        DeclarationTva decl = repo
                .findByClientIdAndAnneeMoisTrimestre(
                        clientId, dateDebut.getYear(),
                        moisVal, trimestreVal)
                .orElseGet(() -> DeclarationTva.builder()
                        .clientId(clientId)
                        .cabinetId(cabinetId)
                        .comptableId(comptableId)
                        .annee(dateDebut.getYear())
                        .mois(moisVal)
                        .trimestre(trimestreVal)
                        .periodeLabel(periodeLabel)
                        .dateDebut(dateDebut)
                        .dateFin(dateFin)
                        .dateLimite(dateLimiteVal)
                        .build());

        // 5. Reconstruire les lignes
        decl.getLignes().clear();
        BigDecimal runCollectee  = BigDecimal.ZERO;
        BigDecimal runDeductible = BigDecimal.ZERO;

        for (LigneTvaCalc calc : parTaux.values()) {
            LigneTva ligne = LigneTva.builder()
                    .declaration(decl)
                    .taux(calc.taux)
                    .baseHtAchats(round(calc.baseHtAchats))
                    .tvaDeductible(round(calc.tvaDeductible))
                    .baseHtVentes(round(calc.baseHtVentes))
                    .tvaCollectee(round(calc.tvaCollectee))
                    .nbFacturesAchat(calc.nbAchats)
                    .nbFacturesVente(calc.nbVentes)
                    .build();
            decl.getLignes().add(ligne);
            runCollectee  = runCollectee.add(calc.tvaCollectee);
            runDeductible = runDeductible.add(calc.tvaDeductible);
        }

        // 6. TVA nette
        BigDecimal nette  = runCollectee.subtract(runDeductible);
        BigDecimal credit = BigDecimal.ZERO;
        if (nette.compareTo(BigDecimal.ZERO) < 0) {
            credit = nette.negate();
            nette  = BigDecimal.ZERO;
        }
        decl.setTvaCollecteeTotal(round(runCollectee));
        decl.setTvaDeductibleTotal(round(runDeductible));
        decl.setTvaNette(round(nette));
        decl.setCreditTvaReporte(round(credit));

        // 7. Statut automatique + alerte Kafka si en retard
        if (decl.getStatut() == StatutDeclaration.BROUILLON
                && LocalDate.now().isAfter(decl.getDateLimite())) {
            decl.setStatut(StatutDeclaration.EN_RETARD);
        }

        DeclarationTva saved = repo.save(decl);

        // Envoyer alerte Kafka si en retard
        if (saved.getStatut() == StatutDeclaration.EN_RETARD) {
            long joursRetard = java.time.temporal.ChronoUnit.DAYS
                    .between(saved.getDateLimite(), LocalDate.now());
            kafkaProducer.envoyerAlertRetard(
                    com.comptaassist.tva_service.kafka.TvaRetardEvent.builder()
                            .declarationId(saved.getId())
                            .clientId(saved.getClientId())
                            .cabinetId(saved.getCabinetId())
                            .comptableId(saved.getComptableId())
                            .periodeLabel(saved.getPeriodeLabel())
                            .dateLimite(saved.getDateLimite())
                            .joursRetard((int) joursRetard)
                            .statut(StatutDeclaration.EN_RETARD.name())
                            .type("TVA_DECLARATION_EN_RETARD")
                            .build());
        }
        log.info("✅ TVA {} collectée={} déductible={} nette={}",
                periodeLabel, runCollectee, runDeductible, saved.getTvaNette());
        return toDto(saved);
    }

    // ══════════════════════════════════════════════════════
    // SOUMETTRE
    // ══════════════════════════════════════════════════════
    @Transactional
    public DeclarationTvaDto soumettre(UUID id, UUID comptableId) {
        DeclarationTva d = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Déclaration introuvable"));
        d.setStatut(StatutDeclaration.SOUMISE);
        d.setDateSoumission(LocalDateTime.now());
        d.setSoumispar(comptableId);
        return toDto(repo.save(d));
    }

    // ══════════════════════════════════════════════════════
    // LISTER
    // ══════════════════════════════════════════════════════
    public List<DeclarationTvaDto> listerParClient(UUID clientId) {
        List<DeclarationTva> liste =
                repo.findByClientIdOrderByDateDebutDesc(clientId);
        List<DeclarationTvaDto> result = new ArrayList<>();
        for (DeclarationTva d : liste) result.add(toDto(d));
        return result;
    }

    public DeclarationTvaDto getById(UUID id) {
        return toDto(repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Introuvable")));
    }

    // ══════════════════════════════════════════════════════
    // LOGIQUE PÉRIODES FISCALES MAROCAINES
    // ══════════════════════════════════════════════════════

    /**
     * Début de la période fiscale courante selon le régime.
     * Logique : on propose le mois courant si on est encore
     * avant le 20 (date limite), sinon le mois précédent.
     * En pratique on cherche la période qui a des factures non encore déclarées.
     */
    private LocalDate debutPeriodeCourante(RegimeTva regime) {
        LocalDate today = LocalDate.now();
        if (regime == RegimeTva.MENSUEL) {
            // Si on est avant le 20 du mois → période = mois courant
            // Si on est après le 20 → période = mois courant aussi
            // (le comptable déclare le mois en cours ou le mois passé)
            // On propose toujours le mois courant en premier
            return today.withDayOfMonth(1);
        } else {
            // Trimestre courant
            int trimestre = getTrimestre(today);
            return debutTrimestre(today.getYear(), trimestre);
        }
    }

    /**
     * Fin d'une période selon son début et le régime.
     */
    private LocalDate finPeriode(LocalDate debut, RegimeTva regime) {
        if (regime == RegimeTva.MENSUEL) {
            return debut.withDayOfMonth(debut.lengthOfMonth());
        } else {
            // Fin du trimestre = fin du 3ème mois du trimestre
            return debut.plusMonths(2)
                    .withDayOfMonth(debut.plusMonths(2).lengthOfMonth());
        }
    }

    /**
     * Période suivante après une date de fin.
     */
    private LocalDate periodesSuivante(LocalDate dateFin, RegimeTva regime) {
        if (regime == RegimeTva.MENSUEL) {
            return dateFin.plusDays(1).withDayOfMonth(1);
        } else {
            return dateFin.plusDays(1).withDayOfMonth(1);
        }
    }

    private LocalDate debutTrimestre(int annee, int trimestre) {
        int moisDebut = (trimestre - 1) * 3 + 1;
        return LocalDate.of(annee, moisDebut, 1);
    }

    private int getTrimestre(LocalDate d) {
        return (d.getMonthValue() - 1) / 3 + 1;
    }

    /**
     * Date limite de dépôt — Maroc :
     * avant le 20 du mois suivant la fin de période.
     */
    private LocalDate calculerDateLimite(LocalDate dateFin) {
        return dateFin.withDayOfMonth(1).plusMonths(1).withDayOfMonth(20);
    }

    /**
     * Label lisible de la période.
     * Mensuel      : "Mai 2026"
     * Trimestriel  : "T2 2026 (Avr — Jun)"
     */
    private String buildPeriodeLabel(LocalDate debut, RegimeTva regime) {
        if (regime == RegimeTva.MENSUEL) {
            String nomMois = Month.of(debut.getMonthValue())
                    .getDisplayName(TextStyle.FULL, Locale.FRENCH);
            // Capitaliser
            nomMois = Character.toUpperCase(nomMois.charAt(0))
                    + nomMois.substring(1);
            return nomMois + " " + debut.getYear();
        } else {
            int t = getTrimestre(debut);
            LocalDate fin = finPeriode(debut, regime);
            String moisD = Month.of(debut.getMonthValue())
                    .getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            String moisF = Month.of(fin.getMonthValue())
                    .getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            moisD = Character.toUpperCase(moisD.charAt(0)) + moisD.substring(1);
            moisF = Character.toUpperCase(moisF.charAt(0)) + moisF.substring(1);
            return "T" + t + " " + debut.getYear()
                    + " (" + moisD + " — " + moisF + ")";
        }
    }

    // ══════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════
    private boolean isVente(FactureDto f) {
        if (f.getTypeOperation() == null) return false;
        String op = f.getTypeOperation().toUpperCase();
        return op.contains("VENTE") || op.contains("PRESTATION_CLIENT");
    }

    private BigDecimal resoudreTaux(Double taux) {
        if (taux == null) return new BigDecimal("20");
        BigDecimal raw = BigDecimal.valueOf(taux);
        final BigDecimal normalise = (raw.compareTo(BigDecimal.ONE) <= 0)
                ? raw.multiply(BigDecimal.valueOf(100)) : raw;
        return TAUX_TVA.stream()
                .min(Comparator.comparingDouble(
                        x -> Math.abs(x.doubleValue() - normalise.doubleValue())))
                .orElse(new BigDecimal("20"));
    }

    private BigDecimal nvl(Double v) {
        return v != null ? BigDecimal.valueOf(v) : BigDecimal.ZERO;
    }

    private BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private DeclarationTvaDto toDto(DeclarationTva d) {
        List<LigneTvaDto> lignes = new ArrayList<>();
        for (LigneTva l : d.getLignes()) {
            lignes.add(LigneTvaDto.builder()
                    .taux(l.getTaux())
                    .baseHtAchats(l.getBaseHtAchats())
                    .tvaDeductible(l.getTvaDeductible())
                    .baseHtVentes(l.getBaseHtVentes())
                    .tvaCollectee(l.getTvaCollectee())
                    .nbFacturesAchat(l.getNbFacturesAchat())
                    .nbFacturesVente(l.getNbFacturesVente())
                    .build());
        }
        lignes.sort(Comparator.comparing(LigneTvaDto::getTaux).reversed());

        return DeclarationTvaDto.builder()
                .id(d.getId())
                .clientId(d.getClientId())
                .cabinetId(d.getCabinetId())
                .periodeLabel(d.getPeriodeLabel())
                .annee(d.getAnnee())
                .mois(d.getMois())
                .trimestre(d.getTrimestre())
                .dateDebut(d.getDateDebut())
                .dateFin(d.getDateFin())
                .dateLimite(d.getDateLimite())
                .statut(d.getStatut().name())
                .tvaCollecteeTotal(d.getTvaCollecteeTotal())
                .tvaDeductibleTotal(d.getTvaDeductibleTotal())
                .tvaNette(d.getTvaNette())
                .creditTvaReporte(d.getCreditTvaReporte())
                .dateSoumission(d.getDateSoumission())
                .lignes(lignes)
                .build();
    }
    public List<DeclarationTvaDto> getDeclarationsEnRetard(
            UUID cabinetId) {
        List<DeclarationTva> enRetard = repo
                .findByCabinetId(cabinetId)
                .stream()
                .filter(d -> d.getStatut() == StatutDeclaration.EN_RETARD)
                .toList();
        List<DeclarationTvaDto> result = new ArrayList<>();
        for (DeclarationTva d : enRetard) result.add(toDto(d));
        return result;
    }

    private static class LigneTvaCalc {
        final BigDecimal taux;
        BigDecimal baseHtAchats  = BigDecimal.ZERO;
        BigDecimal tvaDeductible = BigDecimal.ZERO;
        BigDecimal baseHtVentes  = BigDecimal.ZERO;
        BigDecimal tvaCollectee  = BigDecimal.ZERO;
        int nbAchats = 0;
        int nbVentes = 0;
        LigneTvaCalc(BigDecimal taux) { this.taux = taux; }
    }
}