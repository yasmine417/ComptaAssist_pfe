package com.comptaassist.facture_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Génère le CPC (Compte de Produits et Charges)
 * conforme au Plan Comptable Marocain (PCM / CGNC).
 * Structure I → XIII.
 *
 * Alimenté par JournalComptableService qui calcule
 * les totaux par compte depuis ecritures_comptables.
 */
@Service
@Slf4j
public class CpcService {

    // ── Mapping comptes → rubriques CPC ──────────────
    private static final Map<String, String[]> PRODUITS = new LinkedHashMap<>();
    private static final Map<String, String[]> CHARGES  = new LinkedHashMap<>();

    static {
        // I — Produits d'exploitation
        PRODUITS.put("7111", new String[]{"I",    "Ventes de marchandises (en l'état)"});
        PRODUITS.put("7121", new String[]{"I",    "Ventes de biens produits"});
        PRODUITS.put("7122", new String[]{"I",    "Ventes de services produits"});
        PRODUITS.put("7124", new String[]{"I",    "Variation de stocks de produits finis"});
        PRODUITS.put("7131", new String[]{"I",    "Travaux, études et prestations"});
        PRODUITS.put("7141", new String[]{"I",    "Subventions d'exploitation"});
        PRODUITS.put("7161", new String[]{"I",    "Autres produits d'exploitation"});
        PRODUITS.put("7127", new String[]{"I",    "Ports et frais accessoires facturés"});
        // IV — Produits financiers
        PRODUITS.put("7311", new String[]{"IV",   "Revenus des titres de participation"});
        PRODUITS.put("7381", new String[]{"IV",   "Intérêts et produits assimilés"});
        PRODUITS.put("7385", new String[]{"IV",   "Gains de change"});
        // VIII — Produits non courants
        PRODUITS.put("7511", new String[]{"VIII", "Produits de cession d'immobilisations"});
        PRODUITS.put("7581", new String[]{"VIII", "Produits non courants divers"});

        // II — Charges d'exploitation
        CHARGES.put("6111",  new String[]{"II",  "Achats revendus de marchandises"});
        CHARGES.put("6112",  new String[]{"II",  "Achats de matières et fournitures"});
        CHARGES.put("6121",  new String[]{"II",  "Achats consommés de matières premières"});
        CHARGES.put("6125",  new String[]{"II",  "Achats d'eau, gaz et électricité"});
        CHARGES.put("6131",  new String[]{"II",  "Achats de travaux et prestations"});
        CHARGES.put("6141",  new String[]{"II",  "Locations et charges locatives"});
        CHARGES.put("6142",  new String[]{"II",  "Redevances de crédit-bail"});
        CHARGES.put("6143",  new String[]{"II",  "Entretien et réparations"});
        CHARGES.put("6144",  new String[]{"II",  "Primes d'assurances"});
        CHARGES.put("6145",  new String[]{"II",  "Frais de télécommunications"});
        CHARGES.put("6147",  new String[]{"II",  "Transports sur achats"});
        CHARGES.put("6148",  new String[]{"II",  "Autres charges externes"});
        CHARGES.put("61263", new String[]{"II",  "Prestations de services"});
        CHARGES.put("61365", new String[]{"II",  "Honoraires"});
        CHARGES.put("6161",  new String[]{"II",  "Impôts et taxes"});
        CHARGES.put("6171",  new String[]{"II",  "Rémunérations du personnel"});
        CHARGES.put("6174",  new String[]{"II",  "Charges sociales patronales"});
        CHARGES.put("6175",  new String[]{"II",  "Charges sociales (CNSS / AMO)"});
        CHARGES.put("6182",  new String[]{"II",  "Dotations aux amortissements"});
        CHARGES.put("6194",  new String[]{"II",  "Dotations aux provisions"});
        // V — Charges financières
        CHARGES.put("6311",  new String[]{"V",   "Charges d'intérêts"});
        CHARGES.put("6385",  new String[]{"V",   "Pertes de change"});
        // IX — Charges non courantes
        CHARGES.put("6511",  new String[]{"IX",  "VNA des immobilisations cédées"});
        CHARGES.put("6581",  new String[]{"IX",  "Charges non courantes diverses"});
        // XII — Impôts sur résultats
        CHARGES.put("6701",  new String[]{"XII", "Impôt sur les sociétés (IS)"});
        CHARGES.put("6702",  new String[]{"XII", "Contribution sociale de solidarité"});
    }

    // ── Ordre des rubriques CPC ───────────────────────
    private record Rubrique(String code, String type, String titre) {}

    private static final List<Rubrique> RUBRIQUES = List.of(
            new Rubrique("I",    "produit", "Produits d'exploitation"),
            new Rubrique("II",   "charge",  "Charges d'exploitation"),
            new Rubrique("III",  "result",  "Résultat d'exploitation (I − II)"),
            new Rubrique("IV",   "produit", "Produits financiers"),
            new Rubrique("V",    "charge",  "Charges financières"),
            new Rubrique("VI",   "result",  "Résultat financier (IV − V)"),
            new Rubrique("VII",  "result",  "Résultat courant (III + VI)"),
            new Rubrique("VIII", "produit", "Produits non courants"),
            new Rubrique("IX",   "charge",  "Charges non courantes"),
            new Rubrique("X",    "result",  "Résultat non courant (VIII − IX)"),
            new Rubrique("XI",   "result",  "Résultat avant impôts (VII + X)"),
            new Rubrique("XII",  "charge",  "Impôts sur les résultats"),
            new Rubrique("XIII", "result",  "Résultat net (XI − XII)")
    );

    // ══════════════════════════════════════════════════
    // POINT D'ENTRÉE
    // ══════════════════════════════════════════════════

    /**
     * Génère le CPC complet depuis les totaux par compte.
     * totauxComptes : { "7122" → 19000.0, "6148" → 5000.0, ... }
     */
    public Map<String, Object> genererCpc(
            Map<String, BigDecimal> totauxComptes,
            String periodeDebut,
            String periodeFin) {

        // Agréger par rubrique
        Map<String, Double>           totP   = new LinkedHashMap<>();
        Map<String, Double>           totC   = new LinkedHashMap<>();
        Map<String, List<Map<String,Object>>> detail = new LinkedHashMap<>();

        for (Map.Entry<String, BigDecimal> entry : totauxComptes.entrySet()) {
            String compte = entry.getKey();
            BigDecimal montantBd = entry.getValue();

            if (montantBd == null || montantBd.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            double montant = montantBd.doubleValue();

            if (PRODUITS.containsKey(compte)) {
                String[] rb = PRODUITS.get(compte);
                totP.merge(rb[0], montant, Double::sum);
                detail.computeIfAbsent(rb[0], k -> new ArrayList<>())
                        .add(Map.of(
                                "compte", compte,
                                "libelle", rb[1],
                                "montant", round(montant)
                        ));

            } else if (CHARGES.containsKey(compte)) {
                String[] rb = CHARGES.get(compte);
                totC.merge(rb[0], montant, Double::sum);
                detail.computeIfAbsent(rb[0], k -> new ArrayList<>())
                        .add(Map.of(
                                "compte", compte,
                                "libelle", rb[1],
                                "montant", round(montant)
                        ));

            } else {
                _mapperParPrefixe(compte, montant, totP, totC, detail);
            }
        }





        // Arrondir les totaux
        totP.replaceAll((k, v) -> round(v));
        totC.replaceAll((k, v) -> round(v));

        // Construire les rubriques
        List<Map<String, Object>> rubriques = new ArrayList<>();
        for (Rubrique r : RUBRIQUES) {
            Map<String, Object> rubMap = new LinkedHashMap<>();
            rubMap.put("rubrique", r.code());
            rubMap.put("titre",    r.titre());
            rubMap.put("type",     r.type());

            if ("result".equals(r.type())) {
                double total = _calculerResultat(r.code(), totP, totC);
                rubMap.put("lignes", new ArrayList<>());
                rubMap.put("total",  round(total));
            } else {
                Map<String, Double> src = "produit".equals(r.type()) ? totP : totC;
                double total = src.getOrDefault(r.code(), 0.0);
                // new ArrayList pour pouvoir trier (List.of() est immutable)
                List<Map<String,Object>> lignes = new ArrayList<>(
                        detail.getOrDefault(r.code(), new ArrayList<>()));
                lignes.sort(Comparator.comparing(
                        l -> l.get("compte").toString()));
                rubMap.put("lignes", lignes);
                rubMap.put("total",  round(total));
            }
            rubriques.add(rubMap);
        }

        // Résultats clés
        Map<String, Double> resultats = new LinkedHashMap<>();
        resultats.put("exploitation", _getTotal(rubriques, "III"));
        resultats.put("financier",    _getTotal(rubriques, "VI"));
        resultats.put("courant",      _getTotal(rubriques, "VII"));
        resultats.put("non_courant",  _getTotal(rubriques, "X"));
        resultats.put("avant_impots", _getTotal(rubriques, "XI"));
        resultats.put("net",          _getTotal(rubriques, "XIII"));

        log.info("✅ CPC généré : résultat net={}", resultats.get("net"));

        Map<String, Object> cpc = new LinkedHashMap<>();
        cpc.put("periode",        Map.of("debut", periodeDebut, "fin", periodeFin));
        cpc.put("rubriques",      rubriques);
        cpc.put("resultats",      resultats);
        cpc.put("totaux_comptes", totauxComptes);
        return cpc;
    }

    // ── Helpers privés ────────────────────────────────

    private double _calculerResultat(
            String code,
            Map<String, Double> totP,
            Map<String, Double> totC) {
        double I    = totP.getOrDefault("I", 0.0);
        double II   = totC.getOrDefault("II", 0.0);
        double IV   = totP.getOrDefault("IV", 0.0);
        double V    = totC.getOrDefault("V", 0.0);
        double VIII = totP.getOrDefault("VIII", 0.0);
        double IX   = totC.getOrDefault("IX", 0.0);
        double XII  = totC.getOrDefault("XII", 0.0);

        return switch (code) {
            case "III"  -> I   - II;
            case "VI"   -> IV  - V;
            case "VII"  -> (I  - II) + (IV - V);
            case "X"    -> VIII - IX;
            case "XI"   -> (I - II) + (IV - V) + (VIII - IX);
            case "XIII" -> (I - II) + (IV - V) + (VIII - IX) - XII;
            default     -> 0.0;
        };
    }

    private double _getTotal(
            List<Map<String, Object>> rubriques, String code) {
        return rubriques.stream()
                .filter(r -> code.equals(r.get("rubrique")))
                .mapToDouble(r -> ((Number) r.get("total")).doubleValue())
                .findFirst().orElse(0.0);
    }

    private void _mapperParPrefixe(
            String compte, double montant,
            Map<String, Double> totP,
            Map<String, Double> totC,
            Map<String, List<Map<String,Object>>> detail) {

        String p3 = compte.length() >= 3
                ? compte.substring(0, 3) : compte;

        Map<String, String[]> prefixesCharge = Map.of(
                "611", new String[]{"II",  "Achats"},
                "612", new String[]{"II",  "Achats consommés"},
                "613", new String[]{"II",  "Achats de travaux"},
                "614", new String[]{"II",  "Autres charges externes"},
                "617", new String[]{"II",  "Charges de personnel"},
                "619", new String[]{"II",  "Dotations d'exploitation"},
                "670", new String[]{"XII", "Impôts sur les résultats"}
        );
        Map<String, String[]> prefixesProduit = Map.of(
                "711", new String[]{"I",   "Ventes de marchandises"},
                "712", new String[]{"I",   "Ventes de produits"},
                "713", new String[]{"I",   "Ventes de services"},
                "731", new String[]{"IV",  "Produits financiers"},
                "751", new String[]{"VIII","Produits non courants"}
        );

        if (compte.startsWith("6") && prefixesCharge.containsKey(p3)) {
            String[] rb = prefixesCharge.get(p3);
            totC.merge(rb[0], montant, Double::sum);
            detail.computeIfAbsent(rb[0], k -> new ArrayList<>())
                    .add(Map.of("compte", compte, "libelle", rb[1],
                            "montant", round(montant)));
        } else if (compte.startsWith("7") && prefixesProduit.containsKey(p3)) {
            String[] rb = prefixesProduit.get(p3);
            totP.merge(rb[0], montant, Double::sum);
            detail.computeIfAbsent(rb[0], k -> new ArrayList<>())
                    .add(Map.of("compte", compte, "libelle", rb[1],
                            "montant", round(montant)));
        } else {
            log.warn("Compte {} non mappé dans le CPC", compte);
        }
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}