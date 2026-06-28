package com.comptaassist.facture_service.controller;

import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.repository.FactureCPCRepository;
import com.comptaassist.facture_service.service.JournalComptableService;
import com.comptaassist.facture_service.service.TresorerieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rapport")
@RequiredArgsConstructor
public class RapportController {

    private final JournalComptableService journalService;
    private final TresorerieService       tresorerieService;
    private final FactureCPCRepository    factureRepo;

    @GetMapping("/mensuel")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> rapportMensuel(
            @RequestParam UUID clientId,
            @RequestParam UUID cabinetId,
            @RequestParam(required = false) String mois,
            @AuthenticationPrincipal String comptableId) {

        YearMonth ym = (mois != null && !mois.isBlank())
                ? YearMonth.parse(mois)
                : YearMonth.now().minusMonths(1);

        LocalDate debut = ym.atDay(1);
        LocalDate fin   = ym.atEndOfMonth();

        Map<String, Object> rapport = new LinkedHashMap<>();

        // ── Métadonnées ──────────────────────────────────
        rapport.put("clientId",  clientId.toString());
        rapport.put("cabinetId", cabinetId.toString());
        rapport.put("mois",      ym.toString());
        rapport.put("moisLabel", labelMois(ym));
        rapport.put("dateDebut", debut.toString());
        rapport.put("dateFin",   fin.toString());
        rapport.put("genereA",   LocalDate.now().toString());

        // ── Toutes les factures du mois ──────────────────
        List<FactureCPC> toutesFactures = factureRepo
                .findByClientIdOrderByDateFactureDesc(clientId)
                .stream()
                .filter(f -> f.getDateFacture() != null
                        && !f.getDateFacture().isBefore(debut)
                        && !f.getDateFacture().isAfter(fin))
                .toList();

        // ── Factures de VENTE (revenus) ──────────────────
        List<FactureCPC> ventes = toutesFactures.stream()
                .filter(f -> f.getTypeOperation() != null
                        && f.getTypeOperation().startsWith("VENTE"))
                .toList();

        // ── Factures d'ACHAT (dépenses) ──────────────────
        List<FactureCPC> achats = toutesFactures.stream()
                .filter(f -> f.getTypeOperation() != null
                        && f.getTypeOperation().startsWith("ACHAT"))
                .toList();

        // ── KPIs résumé ───────────────────────────────────
        double chiffreAffaires = ventes.stream()
                .mapToDouble(f -> f.getMontantHt() != null ? f.getMontantHt() : 0.0)
                .sum();
        double totalDepenses = achats.stream()
                .mapToDouble(f -> f.getMontantHt() != null ? f.getMontantHt() : 0.0)
                .sum();
        double benefice = chiffreAffaires - totalDepenses;

        double tvaCollectee = ventes.stream()
                .mapToDouble(f -> f.getMontantTva() != null ? f.getMontantTva() : 0.0)
                .sum();
        double tvaRecuperable = achats.stream()
                .mapToDouble(f -> f.getMontantTva() != null ? f.getMontantTva() : 0.0)
                .sum();
        double tvaAPayer = tvaCollectee - tvaRecuperable;

        long nbEmises   = toutesFactures.size();
        long nbPayees   = toutesFactures.stream()
                .filter(f -> f.getStatut() != null
                        && "PAYE".equals(f.getStatut().name()))
                .count();
        long nbImpayes  = nbEmises - nbPayees;

        double montantPaye = toutesFactures.stream()
                .mapToDouble(f -> f.getMontantPaye() != null ? f.getMontantPaye() : 0.0)
                .sum();
        double resteAPayer = toutesFactures.stream()
                .mapToDouble(f -> f.getResteAPayer() != null ? f.getResteAPayer() : 0.0)
                .sum();

        rapport.put("resume", Map.of(
                "chiffreAffaires", round(chiffreAffaires),
                "totalDepenses",   round(totalDepenses),
                "benefice",        round(benefice),
                "tvaCollectee",    round(tvaCollectee),
                "tvaRecuperable",  round(tvaRecuperable),
                "tvaAPayer",       round(tvaAPayer),
                "nbEmises",        nbEmises,
                "nbPayees",        nbPayees,
                "nbImpayes",       nbImpayes,
                "resteAPayer",     round(resteAPayer)
        ));

        // ── Détail revenus (ventes) ───────────────────────
        List<Map<String, Object>> detailVentes = ventes.stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date",      f.getDateFacture() != null ? f.getDateFacture().toString() : "");
                    m.put("reference", f.getNumeroFacture() != null ? f.getNumeroFacture() : "");
                    m.put("client",    f.getClient() != null ? f.getClient() : "");
                    m.put("montantHt", f.getMontantHt() != null ? round(f.getMontantHt()) : 0.0);
                    m.put("tva",       f.getMontantTva() != null ? round(f.getMontantTva()) : 0.0);
                    m.put("montantTtc",f.getMontantTtc() != null ? round(f.getMontantTtc()) : 0.0);
                    m.put("statut",    f.getStatut() != null ? f.getStatut().name() : "");
                    return m;
                })
                .collect(Collectors.toList());

        // ── Détail dépenses (achats) ──────────────────────
        List<Map<String, Object>> detailAchats = achats.stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date",        f.getDateFacture() != null ? f.getDateFacture().toString() : "");
                    m.put("reference",   f.getNumeroFacture() != null ? f.getNumeroFacture() : "");
                    m.put("fournisseur", f.getFournisseur() != null ? f.getFournisseur() : "");
                    m.put("montantHt",   f.getMontantHt() != null ? round(f.getMontantHt()) : 0.0);
                    m.put("tva",         f.getMontantTva() != null ? round(f.getMontantTva()) : 0.0);
                    m.put("montantTtc",  f.getMontantTtc() != null ? round(f.getMontantTtc()) : 0.0);
                    m.put("statut",      f.getStatut() != null ? f.getStatut().name() : "");
                    return m;
                })
                .collect(Collectors.toList());

        rapport.put("revenus",  detailVentes);
        rapport.put("depenses", detailAchats);

        // ── Balance du mois ──────────────────────────────
        try {
            rapport.put("balance",
                    journalService.balanceParClient(
                            cabinetId, clientId, debut, fin));
        } catch (Exception e) {
            rapport.put("balance", List.of());
        }

        // ── Trésorerie ───────────────────────────────────
        try {
            var dashboard = tresorerieService
                    .getDashboard(UUID.fromString(comptableId), clientId);
            rapport.put("tresorerie", Map.of(
                    "soldeBanque", dashboard.getKpis().getSoldeBanqueReel(),
                    "caEncaisseMois",    dashboard.getKpis().getCaEncaisseMois(),
                    "decaissementsMois", dashboard.getKpis().getDecaissementsMois(),
                    "creancesTotales",   dashboard.getKpis().getCreancesTotales(),
                    "facturesEnRetard",  dashboard.getKpis().getFacturesEnRetard()
            ));
        } catch (Exception e) {
            rapport.put("tresorerie", Map.of(
                    "soldeBanque", 0.0,
                    "caEncaisseMois", 0.0,
                    "decaissementsMois", 0.0,
                    "creancesTotales", 0.0,
                    "facturesEnRetard", 0
            ));
        }

        return ResponseEntity.ok(rapport);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String labelMois(YearMonth ym) {
        String[] mois = {
                "Janvier","Février","Mars","Avril","Mai","Juin",
                "Juillet","Août","Septembre","Octobre","Novembre","Décembre"
        };
        return mois[ym.getMonthValue() - 1] + " " + ym.getYear();
    }
}