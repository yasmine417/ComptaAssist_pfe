package com.comptaassist.facture_service.service;

import com.comptaassist.facture_service.entity.EcritureComptable;
import com.comptaassist.facture_service.entity.EcritureComptable.StatutEcriture;
import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.repository.EcritureComptableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Service central du journal comptable.
 * Toutes les écritures passent par ici.
 *
 * Journaux supportés :
 *   VE = Ventes (factures clients)
 *   AC = Achats (factures fournisseurs)
 *   BQ = Banque (paiements par virement/chèque)
 *   CA = Caisse (paiements en espèces)
 *   OD = Opérations diverses (extournes, régularisations)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JournalComptableService {

    private final EcritureComptableRepository ecritureRepo;

    // ══════════════════════════════════════════════════
    // GÉNÉRATION ÉCRITURES DEPUIS FACTURE
    // ══════════════════════════════════════════════════

    /**
     * Point d'entrée principal.
     * Appelé automatiquement quand une facture est approuvée.
     * Détermine le sens (vente/achat) et génère les écritures.
     */
    @Transactional
    public List<EcritureComptable> genererEcrituresFacture(
            FactureCPC facture,
            Map<String, Object> donneesOcr) {

        boolean vente = _estVente(facture);

        List<EcritureComptable> ecritures = vente
                ? _genererEcrituresVente(facture, donneesOcr)
                : _genererEcrituresAchat(facture, donneesOcr);

        // Numéroter et sauvegarder
        String sequence = _prochainNumeroSequence(
                facture.getCabinetId(),
                vente ? "VE" : "AC",
                _exercice(facture.getDateFacture()));

        for (EcritureComptable e : ecritures) {
            e.setNumeroSequence(sequence);
        }

        List<EcritureComptable> saved =
                ecritureRepo.saveAll(ecritures);

        log.info("✅ {} écritures {} générées — pièce={} séq={}",
                saved.size(),
                vente ? "VENTE" : "ACHAT",
                facture.getNumeroFacture(),
                sequence);

        return saved;
    }

    /**
     * Génère les écritures en CHARGE (classe 6) pour une facture
     * initialement classée comme immobilisation.
     * Appelé par ReclassementService quand le comptable choisit "CHARGE".
     */
    @Transactional
    public List<EcritureComptable> genererEcrituresFactureCharge(
            FactureCPC facture,
            Map<String, Object> donneesOcr) {

        List<EcritureComptable> ecritures =
                _genererEcrituresAchat(facture, donneesOcr);

        String sequence = _prochainNumeroSequence(
                facture.getCabinetId(), "AC",
                _exercice(facture.getDateFacture()));

        for (EcritureComptable e : ecritures)
            e.setNumeroSequence(sequence);

        List<EcritureComptable> saved = ecritureRepo.saveAll(ecritures);
        log.info("✅ {} écritures CHARGE (reclassement) — pièce={} séq={}",
                saved.size(), facture.getNumeroFacture(), sequence);
        return saved;
    }
    //
    // Journal VE
    // DÉBIT  3421  Clients          TTC
    // CRÉDIT 7xxx  Ventes/Produits   HT
    // CRÉDIT 4455  TVA facturée      TVA
    // ─────────────────────────────────────────────────
    private List<EcritureComptable> _genererEcrituresVente(
            FactureCPC f,
            Map<String, Object> ocr) {

        double ht  = nvl(f.getMontantHt());
        double tva = nvl(f.getMontantTva());
        double ttc = nvl(f.getMontantTtc());
        double port= nvl(f.getFraisPortHt());

        LocalDate date    = f.getDateFacture() != null
                ? f.getDateFacture() : LocalDate.now();
        String exercice   = _exercice(date);
        String ref        = _ref(f.getNumeroFacture(), f.getClient());
        String compte7    = _compteVente(ocr, f);

        List<EcritureComptable> lignes = new ArrayList<>();
        int num = 1;

        // 1. DÉBIT 3421 Clients — TTC
        lignes.add(_ligne(f, "VE", date, exercice, ref,
                "FACTURE_VENTE", num++,
                "3421", "Clients",
                "Clients — " + ref,
                ttc, 0.0));

        // 2. CRÉDIT 7xxx Ventes — HT
        if (ht > 0) {
            String libCompte7 = _libelleCompte(compte7);
            lignes.add(_ligne(f, "VE", date, exercice, ref,
                    "FACTURE_VENTE", num++,
                    compte7, libCompte7,
                    "Ventes — " + ref,
                    0.0, ht));
        }

        // 3. CRÉDIT 7127 Port facturé — si présent
        if (port > 0) {
            lignes.add(_ligne(f, "VE", date, exercice, ref,
                    "FACTURE_VENTE", num++,
                    "7127", "Ports et frais accessoires facturés",
                    "Port facturé — " + ref,
                    0.0, port));
        }

        // 4. CRÉDIT 4455 TVA facturée — TVA
        if (tva > 0) {
            lignes.add(_ligne(f, "VE", date, exercice, ref,
                    "FACTURE_VENTE", num++,
                    "4455", "État — TVA facturée",
                    "TVA facturée — " + ref,
                    0.0, tva));
        }

        _verifierEquilibre(lignes, ref);
        return lignes;
    }

    // ─────────────────────────────────────────────────
    // ÉCRITURE ACHAT
    //
    // Journal AC
    // DÉBIT  6xxx  Charges           HT
    // DÉBIT  3455  TVA récupérable   TVA
    // CRÉDIT 4411  Fournisseurs      TTC
    // ─────────────────────────────────────────────────
    private List<EcritureComptable> _genererEcrituresAchat(
            FactureCPC f,
            Map<String, Object> ocr) {

        double ht     = nvl(f.getMontantHt());
        double tva    = nvl(f.getMontantTva());
        double ttc    = nvl(f.getMontantTtc());
        double portHt = nvl(f.getFraisPortHt());

        LocalDate date  = f.getDateFacture() != null
                ? f.getDateFacture() : LocalDate.now();
        String exercice = _exercice(date);
        String ref      = _ref(f.getNumeroFacture(), f.getFournisseur());
        String compte6  = _compteCharge(ocr, f);

        List<EcritureComptable> lignes = new ArrayList<>();
        int num = 1;

        // 1. DÉBIT 6xxx Charge — HT
        if (ht > 0) {
            String libCompte6 = _libelleCompte(compte6);
            lignes.add(_ligne(f, "AC", date, exercice, ref,
                    "FACTURE_ACHAT", num++,
                    compte6, libCompte6,
                    libCompte6 + " — " + ref,
                    ht, 0.0));
        }

        // 2. DÉBIT 6147 Frais de port — si présent
        if (portHt > 0) {
            lignes.add(_ligne(f, "AC", date, exercice, ref,
                    "FACTURE_ACHAT", num++,
                    "6147", "Transports sur achats",
                    "Port — " + ref,
                    portHt, 0.0));
        }

        // 3. DÉBIT 3455 TVA récupérable
        if (tva > 0) {
            lignes.add(_ligne(f, "AC", date, exercice, ref,
                    "FACTURE_ACHAT", num++,
                    "3455", "État — TVA récupérable sur charges",
                    "TVA récup. — " + ref,
                    tva, 0.0));
        }

        // 4. CRÉDIT 4411 Fournisseurs — TTC
        lignes.add(_ligne(f, "AC", date, exercice, ref,
                "FACTURE_ACHAT", num++,
                "4411", "Fournisseurs",
                "Fournisseurs — " + ref,
                0.0, ttc));

        _verifierEquilibre(lignes, ref);
        return lignes;
    }

    // ══════════════════════════════════════════════════
    // ÉCRITURE DE PAIEMENT (journal BQ ou CA)
    //
    // Quand le comptable fait le rapprochement bancaire :
    //
    // Pour une vente encaissée (Journal BQ) :
    //   DÉBIT  5141  Banque     montant reçu
    //   CRÉDIT 3421  Clients    montant reçu
    //
    // Pour un achat payé (Journal BQ) :
    //   DÉBIT  4411  Fournisseurs   montant payé
    //   CRÉDIT 5141  Banque         montant payé
    // ══════════════════════════════════════════════════
    @Transactional
    public List<EcritureComptable> genererEcriturePaiement(
            FactureCPC facture,
            double montantPaye,
            String modePaiement,
            String referenceVirement,
            LocalDate datePaiement) {

        boolean vente   = _estVente(facture);
        String journal  = _journalPaiement(modePaiement);
        String compteTresorerie = _compteTresorerie(modePaiement);
        String exercice = _exercice(datePaiement);
        String ref      = referenceVirement != null && !referenceVirement.isBlank()
                ? referenceVirement
                : "PAY-" + facture.getNumeroFacture();
        String tiers    = vente ? facture.getClient() : facture.getFournisseur();

        List<EcritureComptable> lignes = new ArrayList<>();

        if (vente) {
            // Encaissement vente
            // DÉBIT  5141/5161 Banque/Caisse
            // CRÉDIT 3421      Clients
            lignes.add(_ligne(facture, journal, datePaiement, exercice, ref,
                    "VIREMENT", 1,
                    compteTresorerie, _libelleCompte(compteTresorerie),
                    "Encaissement — " + ref + " — " + tiers,
                    montantPaye, 0.0));

            lignes.add(_ligne(facture, journal, datePaiement, exercice, ref,
                    "VIREMENT", 2,
                    "3421", "Clients",
                    "Règlement client — " + ref + " — " + tiers,
                    0.0, montantPaye));
        } else {
            // Paiement fournisseur
            // DÉBIT  4411      Fournisseurs
            // CRÉDIT 5141/5161 Banque/Caisse
            lignes.add(_ligne(facture, journal, datePaiement, exercice, ref,
                    "VIREMENT", 1,
                    "4411", "Fournisseurs",
                    "Règlement fournisseur — " + ref + " — " + tiers,
                    montantPaye, 0.0));

            lignes.add(_ligne(facture, journal, datePaiement, exercice, ref,
                    "VIREMENT", 2,
                    compteTresorerie, _libelleCompte(compteTresorerie),
                    "Paiement — " + ref + " — " + tiers,
                    0.0, montantPaye));
        }

        String sequence = _prochainNumeroSequence(
                facture.getCabinetId(), journal, exercice);
        lignes.forEach(e -> e.setNumeroSequence(sequence));

        // Lettrer automatiquement les écritures de la facture
        _lettrerAutomatiquement(facture, montantPaye);

        List<EcritureComptable> saved = ecritureRepo.saveAll(lignes);
        log.info("✅ Écriture paiement {} — {} MAD — ref={}",
                vente ? "ENCAISSEMENT" : "DECAISSEMENT",
                montantPaye, ref);

        return saved;
    }

    // ══════════════════════════════════════════════════
    // GÉNÉRATION CPC DEPUIS LES ÉCRITURES
    // ══════════════════════════════════════════════════

    /**
     * Calcule les totaux par compte (classe 6 et 7)
     * pour générer le CPC d'une période donnée.
     * Conforme PCM : charges = débits 6xxx, produits = crédits 7xxx.
     */
    public Map<String, java.math.BigDecimal> calculerTotauxPourCpc(
            UUID cabinetId,
            LocalDate debut,
            LocalDate fin) {

        List<Object[]> rows = ecritureRepo
                .findTotauxParComptePourCpc(cabinetId, debut, fin);

        Map<String, java.math.BigDecimal> totaux = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String compte      = (String) row[0];
            double totalDebit  = row[1] != null
                    ? ((Number) row[1]).doubleValue() : 0.0;
            double totalCredit = row[2] != null
                    ? ((Number) row[2]).doubleValue() : 0.0;

            if (compte.startsWith("6")) {
                double net = totalDebit - totalCredit;
                if (net > 0) totaux.put(compte, java.math.BigDecimal.valueOf(round(net)));
            } else if (compte.startsWith("7")) {
                double net = totalCredit - totalDebit;
                if (net > 0) totaux.put(compte, java.math.BigDecimal.valueOf(round(net)));
            }
        }

        log.info("CPC calculé : {} comptes — période {} → {}",
                totaux.size(), debut, fin);
        return totaux;
    }

    public Map<String, java.math.BigDecimal> calculerTotauxPourCpcExercice(
            UUID cabinetId, String exercice) {

        List<Object[]> rows = ecritureRepo
                .findTotauxParComptePourCpcExercice(cabinetId, exercice);
        return _mapperTotaux(rows);
    }

    public Map<String, java.math.BigDecimal> calculerTotauxPourCpcExerciceEtClient(
            UUID cabinetId, String exercice, UUID clientId) {
        List<Object[]> rows = ecritureRepo
                .findTotauxParComptePourCpcExerciceEtClient(cabinetId, exercice, clientId);
        return _mapperTotaux(rows);
    }

    public Map<String, java.math.BigDecimal> calculerTotauxPourCpcEtClient(
            UUID cabinetId, LocalDate debut, LocalDate fin, UUID clientId) {
        List<Object[]> rows = ecritureRepo
                .findTotauxParComptePourCpcEtClient(cabinetId, debut, fin, clientId);
        return _mapperTotaux(rows);
    }

    private Map<String, java.math.BigDecimal> _mapperTotaux(List<Object[]> rows) {
        Map<String, java.math.BigDecimal> totaux = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String compte      = (String) row[0];
            double totalDebit  = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            double totalCredit = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            if (compte.startsWith("6")) {
                double net = totalDebit - totalCredit;
                if (net > 0) totaux.put(compte, java.math.BigDecimal.valueOf(round(net)));
            } else if (compte.startsWith("7")) {
                double net = totalCredit - totalDebit;
                if (net > 0) totaux.put(compte, java.math.BigDecimal.valueOf(round(net)));
            }
        }
        return totaux;
    }

    public List<Map<String, Object>> balanceParClient(
            UUID cabinetId, UUID clientId,
            LocalDate debut, LocalDate fin) {
        List<Object[]> rows = ecritureRepo
                .findBalanceComptesParClient(cabinetId, clientId, debut, fin);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("compte",      row[0]);
            ligne.put("intitule",    row[1]);
            ligne.put("totalDebit",  row[2] != null ? round(((Number) row[2]).doubleValue()) : 0.0);
            ligne.put("totalCredit", row[3] != null ? round(((Number) row[3]).doubleValue()) : 0.0);
            ligne.put("solde",       row[4] != null ? round(((Number) row[4]).doubleValue()) : 0.0);
            result.add(ligne);
        }
        return result;
    }

    // ══════════════════════════════════════════════════
    // GRAND-LIVRE PAR COMPTE
    // ══════════════════════════════════════════════════

    // Dans JournalComptableService.java
// Remplacer la méthode grandLivre() existante par celle-ci :

    public List<EcritureComptable> grandLivre(
            UUID cabinetId, String compte,
            LocalDate debut, LocalDate fin,
            UUID clientId) {
        if (clientId != null) {
            return ecritureRepo
                    .findByCabinetIdAndClientIdAndCompteAndDateEcritureBetweenOrderByDateEcriture(
                            cabinetId, clientId, compte, debut, fin);
        }
        return ecritureRepo
                .findByCabinetIdAndCompteAndDateEcritureBetweenOrderByDateEcriture(
                        cabinetId, compte, debut, fin);
    }

    // ══════════════════════════════════════════════════
    // BALANCE DES COMPTES
    // ══════════════════════════════════════════════════

    public List<Map<String, Object>> balance(
            UUID cabinetId, LocalDate debut, LocalDate fin) {

        List<Object[]> rows = ecritureRepo
                .findBalanceComptes(cabinetId, debut, fin);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("compte",         row[0]);
            ligne.put("intitule",       row[1]);
            ligne.put("totalDebit",     row[2] != null ? round(((Number) row[2]).doubleValue()) : 0.0);
            ligne.put("totalCredit",    row[3] != null ? round(((Number) row[3]).doubleValue()) : 0.0);
            ligne.put("solde",          row[4] != null ? round(((Number) row[4]).doubleValue()) : 0.0);
            result.add(ligne);
        }
        return result;
    }

    // ══════════════════════════════════════════════════
    // ÉCRITURES D'UNE FACTURE
    // ══════════════════════════════════════════════════

    public List<EcritureComptable> ecrituresFacture(UUID factureId) {
        return ecritureRepo.findByFactureIdOrderByNumLigne(factureId);
    }

    // ══════════════════════════════════════════════════
    // VALIDATION DES ÉCRITURES
    // ══════════════════════════════════════════════════

    @Transactional
    public void validerEcritures(UUID factureId) {
        List<EcritureComptable> ecritures =
                ecritureRepo.findByFactureIdOrderByNumLigne(factureId);
        ecritures.forEach(e ->
                e.setStatutEcriture(StatutEcriture.VALIDEE));
        ecritureRepo.saveAll(ecritures);
        log.info("✅ {} écritures validées pour facture {}",
                ecritures.size(), factureId);
    }

    // ══════════════════════════════════════════════════
    // HELPERS PRIVÉS
    // ══════════════════════════════════════════════════

    private boolean _estVente(FactureCPC f) {
        String op = (f.getTypeOperation() != null
                ? f.getTypeOperation() : "").toUpperCase();
        return op.contains("VENTE")
                || op.contains("PRESTATION_CLIENT")
                || op.contains("AVOIR_CLIENT");
    }

    private EcritureComptable _ligne(
            FactureCPC facture,
            String journal, LocalDate date,
            String exercice, String refPiece,
            String typePiece, int numLigne,
            String compte, String intitule,
            String libelle,
            double debit, double credit) {

        return EcritureComptable.builder()
                .cabinetId(facture.getCabinetId())
                .clientId(facture.getClientId())
                .comptableId(facture.getComptableId())
                .factureId(facture.getId())
                .journal(journal)
                .dateEcriture(date)
                .exercice(exercice)
                .referencePiece(refPiece)
                .typePiece(typePiece)
                .numLigne(numLigne)
                .compte(compte)
                .intituleCompte(intitule)
                .libelle(libelle)
                .debit(java.math.BigDecimal.valueOf(round(debit)))
                .credit(java.math.BigDecimal.valueOf(round(credit)))
                .devise(facture.getDevise() != null
                        ? facture.getDevise() : "MAD")
                .tiersNom(_estVente(facture)
                        ? facture.getClient()
                        : facture.getFournisseur())
                .tiersIce(facture.getIce())
                .statutEcriture(StatutEcriture.PROVISOIRE)
                .build();
    }

    private void _lettrerAutomatiquement(
            FactureCPC facture, double montantPaye) {
        // Lettrage simplifié : on marque les écritures 3421/4411
        // de la facture comme lettrées
        List<EcritureComptable> ecritures =
                ecritureRepo.findByFactureIdOrderByNumLigne(
                        facture.getId());

        boolean vente = _estVente(facture);
        String compteTiers = vente ? "3421" : "4411";
        String codeLettrage = "L" + System.currentTimeMillis() % 10000;

        ecritures.stream()
                .filter(e -> e.getCompte().equals(compteTiers))
                .forEach(e -> {
                    e.setLettrage(codeLettrage);
                    e.setDateLettrage(LocalDate.now());
                    e.setStatutEcriture(StatutEcriture.LETTREE);
                });
        ecritureRepo.saveAll(ecritures);
    }

    private String _prochainNumeroSequence(
            UUID cabinetId, String journal, String exercice) {
        Optional<String> maxSeq = ecritureRepo
                .findMaxSequence(cabinetId, journal, exercice);

        int prochain = 1;
        if (maxSeq.isPresent() && maxSeq.get() != null) {
            try {
                String[] parts = maxSeq.get().split("-");
                prochain = Integer.parseInt(parts[parts.length - 1]) + 1;
            } catch (Exception ignored) {}
        }
        return String.format("%s-%s-%04d", journal, exercice, prochain);
    }

    private String _exercice(LocalDate date) {
        if (date == null) return String.valueOf(
                LocalDate.now().getYear());
        return String.valueOf(date.getYear());
    }

    private String _ref(String numero, String tiers) {
        if (numero != null && !numero.isBlank())
            return numero;
        if (tiers != null && !tiers.isBlank())
            return tiers.substring(0, Math.min(20, tiers.length()));
        return "PIECE-" + System.currentTimeMillis() % 100000;
    }

    private String _journalPaiement(String mode) {
        if (mode == null) return "BQ";
        return switch (mode.toUpperCase()) {
            case "ESPECES" -> "CA";
            default        -> "BQ";
        };
    }

    private String _compteTresorerie(String mode) {
        if (mode == null) return "5141";
        return switch (mode.toUpperCase()) {
            case "ESPECES" -> "5161";
            default        -> "5141";
        };
    }

    /**
     * Détecte le compte produit (7xxx) depuis les données OCR.
     */
    @SuppressWarnings("unchecked")
    private String _compteVente(
            Map<String, Object> ocr, FactureCPC f) {
        // Utiliser le compte déjà calculé par comptable.py (Python)
        if (f.getCompteCharge() != null && !f.getCompteCharge().isEmpty())
            return f.getCompteCharge();
        // Fallback si absent
        String op = (f.getTypeOperation() != null
                ? f.getTypeOperation() : "").toUpperCase();
        if (op.contains("VENTE_MARCHANDISE")) return "7111";
        if (op.contains("VENTE_TRAVAUX"))     return "71241";
        return "71243";
    }

    @SuppressWarnings("unchecked")
    private String _compteCharge(
            Map<String, Object> ocr, FactureCPC f) {
        // Utiliser le compte déjà calculé par comptable.py (Python)
        if (f.getCompteCharge() != null && !f.getCompteCharge().isEmpty())
            return f.getCompteCharge();
        // Fallback si absent
        String op = (f.getTypeOperation() != null
                ? f.getTypeOperation() : "").toUpperCase();
        if (op.contains("ACHAT_TELEPHONE"))  return "61455";
        if (op.contains("ACHAT_ENERGIE"))    return "61251";
        if (op.contains("ACHAT_HONORAIRES")) return "61365";
        if (op.contains("ACHAT_LOYER"))      return "6131";
        if (op.contains("ACHAT_ASSURANCE"))  return "6134";
        if (op.contains("ACHAT_TRANSPORT"))  return "61425";
        if (op.contains("ACHAT_MARCHANDISE"))return "6111";
        if (op.contains("ACHAT_SERVICE"))    return "61263";
        return "6148";
    }

    private String _libelleCompte(String compte) {
        return switch (compte) {
            case "3421"  -> "Clients";
            case "3455"  -> "État — TVA récupérable sur charges";
            case "34551" -> "État — TVA récupérable sur immobilisations";
            case "34552" -> "État — TVA récupérable sur charges";
            case "4411"  -> "Fournisseurs";
            case "4455"  -> "État — TVA facturée";
            case "5141"  -> "Banques";
            case "5161"  -> "Caisse";
            // Classe 6
            case "6111"  -> "Achats de marchandises";
            case "6122"  -> "Achats de matières consommables";
            case "61227" -> "Achats de fournitures de bureau";
            case "61251" -> "Eau, électricité, gaz";
            case "61253" -> "Petit outillage et équipement";
            case "61261" -> "Achats de travaux";
            case "61263" -> "Achats de prestations de service";
            case "6131"  -> "Locations et charges locatives";
            case "61315" -> "Locations de matériel informatique";
            case "61332" -> "Entretien et réparations";
            case "61335" -> "Maintenance";
            case "6134"  -> "Primes d'assurances";
            case "61365" -> "Honoraires";
            case "61425" -> "Transports sur achats";
            case "61455" -> "Frais de téléphone et internet";
            case "6144"  -> "Publicité et relations publiques";
            case "6147"  -> "Transports sur achats";
            case "6148"  -> "Autres charges externes";
            case "6161"  -> "Impôts et taxes";
            case "6171"  -> "Rémunérations du personnel";
            case "6174"  -> "Charges sociales";
            case "6311"  -> "Intérêts des emprunts";
            case "6701"  -> "Impôts sur les bénéfices";
            // Classe 7
            case "7111"  -> "Ventes de marchandises au Maroc";
            case "71241" -> "Travaux";
            case "71242" -> "Études";
            case "71243" -> "Prestations de services";
            case "71271" -> "Locations diverses reçues";
            case "71276" -> "Ports et frais accessoires facturés";
            case "7122"  -> "Ventes de biens produits";
            case "7127"  -> "Produits accessoires";
            // Classe 2
            case "2285"  -> "Autres immobilisations incorporelles";
            case "2321"  -> "Bâtiments";
            case "2332"  -> "Matériel et outillage";
            case "2340"  -> "Matériel de transport";
            case "2351"  -> "Mobilier de bureau";
            case "2352"  -> "Matériel de bureau";
            case "2355"  -> "Matériel informatique";
            case "2358"  -> "Autres immobilisations";
            default      -> "Compte " + compte;
        };
    }

    private void _verifierEquilibre(
            List<EcritureComptable> lignes, String ref) {
        double td = lignes.stream()
                .mapToDouble(e -> e.getDebit() != null
                        ? e.getDebit().doubleValue() : 0.0).sum();
        double tc = lignes.stream()
                .mapToDouble(e -> e.getCredit() != null
                        ? e.getCredit().doubleValue() : 0.0).sum();
        double ecart = Math.abs(td - tc);
        if (ecart > 0.01) {
            log.warn("⚠️ Écriture déséquilibrée : pièce={} "
                            + "Σdébits={} Σcrédits={} écart={}",
                    ref, td, tc, ecart);
        } else {
            log.info("✅ Écriture équilibrée : pièce={} Σ={}", ref, td);
        }
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double nvl(Double v) {
        return v != null ? v : 0.0;
    }
    public List<EcritureComptable> ecrituresPeriode(
            UUID cabinetId, LocalDate debut, LocalDate fin, UUID clientId) {
        if (clientId != null)
            return ecritureRepo
                    .findByCabinetIdAndClientIdAndDateEcritureBetweenOrderByDateEcritureAsc(
                            cabinetId, clientId, debut, fin);
        return ecritureRepo
                .findByCabinetIdAndDateEcritureBetweenOrderByDateEcritureAsc(
                        cabinetId, debut, fin);
    }

}