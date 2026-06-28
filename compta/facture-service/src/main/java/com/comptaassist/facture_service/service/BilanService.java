package com.comptaassist.facture_service.service;

import com.comptaassist.facture_service.entity.EcritureComptable;
import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.repository.EcritureComptableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class BilanService {

    private final EcritureComptableRepository ecritureRepo;

    // ══════════════════════════════════════════════════
    // AJOUTER IMMOBILISATION AU BILAN
    // ══════════════════════════════════════════════════

    @Transactional
    public void ajouterImmobilisation(FactureCPC facture) {
        // Les écritures 2xxx sont déjà en base
        // On log juste pour confirmer
        log.info("✅ Immobilisation confirmée au bilan : "
                        + "facture={} compte={} montant={}",
                facture.getId(),
                facture.getCompteCharge(),
                facture.getMontantHt());
        // Le bilan se calcule dynamiquement depuis
        // les écritures — pas besoin de table séparée
    }

    // ══════════════════════════════════════════════════
    // SUPPRIMER DU BILAN (reclassé en charge)
    // ══════════════════════════════════════════════════

    @Transactional
    public void supprimerDuBilan(UUID factureId) {
        // Les écritures 2xxx seront supprimées par
        // ReclassementService via ecritureRepo.deleteByFactureId
        // On log juste pour traçabilité
        log.info("🗑️ Immobilisation retirée du bilan "
                + "pour facture {}", factureId);
    }

    // ══════════════════════════════════════════════════
    // GÉNÉRER LE BILAN D'UN CLIENT
    // ══════════════════════════════════════════════════

    public Map<String, Object> getBilanClient(
            UUID clientId, Integer exercice ,  BigDecimal capitalSocial) {

        List<EcritureComptable> ecritures = exercice != null
                ? ecritureRepo.findByClientIdAndExercice(
                clientId, String.valueOf(exercice))
                : ecritureRepo.findByClientIdOrderByCompteAsc(
                clientId);

        Map<String, BigDecimal> soldes = calculerSoldes(ecritures);
// ── Injecter le capital social dans les soldes ──
        if (capitalSocial != null
                && capitalSocial.compareTo(BigDecimal.ZERO) > 0) {
            soldes.merge("11", capitalSocial, BigDecimal::add);
        }
        // ── ACTIF ──────────────────────────────────────
        Map<String, Object> actifImmobilise =
                construireActifImmobilise(soldes);
        Map<String, Object> actifCirculant  =
                construireActifCirculant(soldes);
        Map<String, Object> tresorerieActif =
                construireTresorerieActif(soldes);

        BigDecimal totalActif =
                (BigDecimal) actifImmobilise.get("total");
        totalActif = totalActif
                .add((BigDecimal) actifCirculant.get("total"))
                .add((BigDecimal) tresorerieActif.get("total"));

        // ── PASSIF ─────────────────────────────────────
        Map<String, Object> financementPermanent =
                construireFinancementPermanent(soldes);
        Map<String, Object> passifCirculant =
                construirePassifCirculant(soldes);
        Map<String, Object> tresoreriePassif =
                construireTresoreriePassif(soldes);

        BigDecimal totalPassif =
                (BigDecimal) financementPermanent.get("total");
        totalPassif = totalPassif
                .add((BigDecimal) passifCirculant.get("total"))
                .add((BigDecimal) tresoreriePassif.get("total"));

        // ── Résultat de l'exercice ─────────────────────
        BigDecimal produits = soldes.entrySet().stream()
                .filter(e -> e.getKey().startsWith("7"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal charges = soldes.entrySet().stream()
                .filter(e -> e.getKey().startsWith("6"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal resultat = produits.subtract(charges);

        totalPassif = totalPassif.add(
                resultat.compareTo(BigDecimal.ZERO) > 0
                        ? resultat : BigDecimal.ZERO);

        // ── Résultat final ─────────────────────────────
        Map<String, Object> bilan = new LinkedHashMap<>();
        bilan.put("clientId",   clientId.toString());
        bilan.put("exercice",   exercice);
        bilan.put("dateGenere",
                java.time.LocalDate.now().toString());

        Map<String, Object> actif = new LinkedHashMap<>();
        actif.put("actifImmobilise",  actifImmobilise);
        actif.put("actifCirculant",   actifCirculant);
        actif.put("tresorerieActif",  tresorerieActif);
        actif.put("totalActif",       round(totalActif));
        bilan.put("actif", actif);

        Map<String, Object> passif = new LinkedHashMap<>();
        passif.put("financementPermanent", financementPermanent);
        passif.put("passifCirculant",      passifCirculant);
        passif.put("tresoreriePassif",     tresoreriePassif);
        passif.put("resultatExercice",     round(resultat));
        passif.put("totalPassif",          round(totalPassif));
        bilan.put("passif", passif);

        bilan.put("equilibre",
                round(totalActif).compareTo(
                        round(totalPassif)) == 0);
        bilan.put("ecart",
                round(totalActif.subtract(totalPassif)).abs());
        bilan.put("capitalSocial", round(capitalSocial != null
                ? capitalSocial : BigDecimal.ZERO));
        return bilan;
    }

    // ══════════════════════════════════════════════════
    // ACTIF IMMOBILISÉ — Classe 2
    // ══════════════════════════════════════════════════

    private Map<String, Object> construireActifImmobilise(
            Map<String, BigDecimal> soldes) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        ajouterLignesBilan(soldes, lignes, "21",
                "Immobilisations en non-valeurs");
        ajouterLignesBilan(soldes, lignes, "22",
                "Immobilisations incorporelles");
        ajouterLignesBilan(soldes, lignes, "23",
                "Immobilisations corporelles");
        ajouterLignesBilan(soldes, lignes, "24",
                "Immobilisations corporelles en cours");
        ajouterLignesBilan(soldes, lignes, "25",
                "Prêts immobilisés");
        ajouterLignesBilan(soldes, lignes, "26",
                "Autres créances financières");
        ajouterLignesBilan(soldes, lignes, "27",
                "Titres de participation");
        BigDecimal total = lignes.stream()
                .map(l -> (BigDecimal) l.get("montant"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("libelle", "ACTIF IMMOBILISÉ");
        section.put("lignes",  lignes);
        section.put("total",   round(total));
        return section;
    }

    // ══════════════════════════════════════════════════
    // ACTIF CIRCULANT — Classe 3
    // ══════════════════════════════════════════════════

    private Map<String, Object> construireActifCirculant(
            Map<String, BigDecimal> soldes) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        ajouterLignesBilan(soldes, lignes, "31", "Marchandises");
        ajouterLignesBilan(soldes, lignes, "32",
                "Matières et fournitures consommables");
        ajouterLignesBilan(soldes, lignes, "33",
                "Produits en cours");
        ajouterLignesBilan(soldes, lignes, "35",
                "Produits finis");
        ajouterLignesBilan(soldes, lignes, "34",
                "Créances de l'actif circulant");
        ajouterLignesBilan(soldes, lignes, "36",
                "Titres et valeurs de placement");
        ajouterLignesBilan(soldes, lignes, "37",
                "Écarts de conversion - Actif");
        BigDecimal total = lignes.stream()
                .map(l -> (BigDecimal) l.get("montant"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("libelle", "ACTIF CIRCULANT");
        section.put("lignes",  lignes);
        section.put("total",   round(total));
        return section;
    }

    // ══════════════════════════════════════════════════
    // TRÉSORERIE ACTIF — Classe 5 soldes débiteurs
    // ══════════════════════════════════════════════════

    private Map<String, Object> construireTresorerieActif(
            Map<String, BigDecimal> soldes) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        ajouterLignesBilan(soldes, lignes, "51",
                "Banques, trésorerie générale");
        ajouterLignesBilan(soldes, lignes, "52",
                "Chèques postaux");
        ajouterLignesBilan(soldes, lignes, "53", "Caisses");
        ajouterLignesBilan(soldes, lignes, "54",
                "Régies d'avances");
        BigDecimal total = lignes.stream()
                .map(l -> (BigDecimal) l.get("montant"))
                .filter(m -> m.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("libelle", "TRÉSORERIE - ACTIF");
        section.put("lignes",  lignes);
        section.put("total",   round(total));
        return section;
    }

    // ══════════════════════════════════════════════════
    // FINANCEMENT PERMANENT — Classe 1
    // ══════════════════════════════════════════════════

    private Map<String, Object> construireFinancementPermanent(
            Map<String, BigDecimal> soldes) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        ajouterLignesBilan(soldes, lignes, "11",
                "Capital social ou personnel");
        ajouterLignesBilan(soldes, lignes, "12",
                "Primes d'émission, de fusion");
        ajouterLignesBilan(soldes, lignes, "13",
                "Écarts de réévaluation");
        ajouterLignesBilan(soldes, lignes, "14",
                "Réserves légales et statutaires");
        ajouterLignesBilan(soldes, lignes, "15",
                "Report à nouveau");
        ajouterLignesBilan(soldes, lignes, "16",
                "Dettes de financement");
        ajouterLignesBilan(soldes, lignes, "17",
                "Provisions durables pour risques");
        ajouterLignesBilan(soldes, lignes, "18",
                "Comptes de liaison");
        BigDecimal total = lignes.stream()
                .map(l -> (BigDecimal) l.get("montant"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("libelle", "FINANCEMENT PERMANENT");
        section.put("lignes",  lignes);
        section.put("total",   round(total));
        return section;
    }

    // ══════════════════════════════════════════════════
    // PASSIF CIRCULANT — Classe 4
    // ══════════════════════════════════════════════════

    private Map<String, Object> construirePassifCirculant(
            Map<String, BigDecimal> soldes) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        ajouterLignesBilan(soldes, lignes, "44",
                "Fournisseurs et comptes rattachés");
        ajouterLignesBilan(soldes, lignes, "45",
                "Personnel et organismes sociaux");
        ajouterLignesBilan(soldes, lignes, "46",
                "Comptes d'associés");
        ajouterLignesBilan(soldes, lignes, "47",
                "Autres créanciers");
        ajouterLignesBilan(soldes, lignes, "48",
                "Comptes de régularisation - Passif");
        BigDecimal total = lignes.stream()
                .map(l -> (BigDecimal) l.get("montant"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("libelle", "PASSIF CIRCULANT");
        section.put("lignes",  lignes);
        section.put("total",   round(total));
        return section;
    }

    // ══════════════════════════════════════════════════
    // TRÉSORERIE PASSIF — Classe 5 soldes créditeurs
    // ══════════════════════════════════════════════════

    private Map<String, Object> construireTresoreriePassif(
            Map<String, BigDecimal> soldes) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        ajouterLignesBilan(soldes, lignes, "55",
                "Crédits d'escompte");
        ajouterLignesBilan(soldes, lignes, "56",
                "Crédits de trésorerie");
        ajouterLignesBilan(soldes, lignes, "57",
                "Banques (soldes créditeurs)");
        BigDecimal total = lignes.stream()
                .map(l -> (BigDecimal) l.get("montant"))
                .filter(m -> m.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("libelle", "TRÉSORERIE - PASSIF");
        section.put("lignes",  lignes);
        section.put("total",   round(total));
        return section;
    }

    // ══════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════

    private Map<String, BigDecimal> calculerSoldes(
            List<EcritureComptable> ecritures) {
        Map<String, BigDecimal> soldes = new LinkedHashMap<>();
        for (EcritureComptable e : ecritures) {
            String compte = e.getCompte();
            if (compte == null || compte.isEmpty()) continue;
            BigDecimal debit  = e.getDebit()  != null
                    ? e.getDebit()  : BigDecimal.ZERO;
            BigDecimal credit = e.getCredit() != null
                    ? e.getCredit() : BigDecimal.ZERO;
            BigDecimal solde;
            String premier = compte.substring(0, 1);
            if ("1".equals(premier) || "4".equals(premier)) {
                solde = credit.subtract(debit);
            } else {
                solde = debit.subtract(credit);
            }
            String cle = compte.length() >= 2
                    ? compte.substring(0, 2) : compte;
            soldes.merge(cle, solde, BigDecimal::add);
        }
        return soldes;
    }

    private void ajouterLignesBilan(
            Map<String, BigDecimal> soldes,
            List<Map<String, Object>> lignes,
            String prefixe,
            String libelle) {
        BigDecimal montant = soldes.getOrDefault(
                prefixe, BigDecimal.ZERO);
        if (montant.compareTo(BigDecimal.ZERO) != 0) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("compte",  prefixe);
            ligne.put("libelle", libelle);
            ligne.put("montant", round(montant));
            lignes.add(ligne);
        }
    }

    private BigDecimal round(BigDecimal val) {
        if (val == null) return BigDecimal.ZERO;
        return val.setScale(2, RoundingMode.HALF_UP);
    }


    public byte[] exporterExcel(
            Map<String, Object> bilanN,
            Map<String, Object> bilanN1,
            String clientId,
            int exercice,
            java.time.LocalDate dateCloture) throws Exception {

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            org.apache.poi.xssf.usermodel.XSSFSheet sheet =
                    wb.createSheet("Bilan Comptable");

            // ── Styles ───────────────────────────────────────
            org.apache.poi.ss.usermodel.CellStyle titleStyle =
                    createStyle(wb, true, (short)16, null,
                            org.apache.poi.ss.usermodel
                                    .HorizontalAlignment.CENTER, false);

            org.apache.poi.ss.usermodel.CellStyle subStyle =
                    createStyle(wb, false, (short)11, null,
                            org.apache.poi.ss.usermodel
                                    .HorizontalAlignment.CENTER, true);

            byte[] bleu = {(byte)0x1e,(byte)0x40,(byte)0xaf};
            byte[] bleuClair = {(byte)0xdb,(byte)0xe9,(byte)0xff};
            byte[] vert = {(byte)0x14,(byte)0x53,(byte)0x2d};
            byte[] vertClair = {(byte)0xf0,(byte)0xfd,(byte)0xf4};

            org.apache.poi.ss.usermodel.CellStyle headerActifStyle =
                    createColorStyle(wb, bleu, true, true);
            org.apache.poi.ss.usermodel.CellStyle headerPassifStyle =
                    createColorStyle(wb, vert, true, true);
            org.apache.poi.ss.usermodel.CellStyle sectionActifStyle =
                    createColorStyle(wb, bleuClair, true, false);
            org.apache.poi.ss.usermodel.CellStyle sectionPassifStyle =
                    createColorStyle(wb, vertClair, true, false);
            org.apache.poi.ss.usermodel.CellStyle totalActifStyle =
                    createColorStyle(wb, bleuClair, true, false);
            org.apache.poi.ss.usermodel.CellStyle totalPassifStyle =
                    createColorStyle(wb, vertClair, true, false);
            org.apache.poi.ss.usermodel.CellStyle dataStyle =
                    createStyle(wb, false, (short)11, null,
                            org.apache.poi.ss.usermodel
                                    .HorizontalAlignment.LEFT, false);
            setBorders(dataStyle);

            org.apache.poi.ss.usermodel.DataFormat fmt =
                    wb.createDataFormat();
            org.apache.poi.ss.usermodel.CellStyle numStyle =
                    createStyle(wb, false, (short)11, null,
                            org.apache.poi.ss.usermodel
                                    .HorizontalAlignment.RIGHT, false);
            numStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            setBorders(numStyle);

            // ── Date de clôture réelle ──────────────────────
            java.time.format.DateTimeFormatter dtf =
                    java.time.format.DateTimeFormatter
                            .ofPattern("dd/MM/yyyy");
            String dateStr = dateCloture.format(dtf);

            // ── Titre ───────────────────────────────────────
            sheet.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            0,0,0,6));
            org.apache.poi.ss.usermodel.Row r0 =
                    sheet.createRow(0);
            r0.setHeightInPoints(30);
            setCell(r0, 0, "BILAN COMPTABLE", titleStyle);

            sheet.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            1,1,0,6));
            org.apache.poi.ss.usermodel.Row r1 =
                    sheet.createRow(1);

            sheet.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            2,2,0,6));
            org.apache.poi.ss.usermodel.Row r2 =
                    sheet.createRow(2);
            setCell(r2, 0,
                    "Montants en Dirhams (MAD)", subStyle);

            // ── En-têtes colonnes ───────────────────────────
            // Colonnes: A=Désignation, B=N, C=N-1 | D=vide |
            //           E=Désignation, F=N, G=N-1
            org.apache.poi.ss.usermodel.Row r4 =
                    sheet.createRow(4);
            r4.setHeightInPoints(20);
            sheet.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            4,4,0,2));
            sheet.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            4,4,4,6));
            setCell(r4, 0, "ACTIF", headerActifStyle);
            setCell(r4, 1, "", headerActifStyle);
            setCell(r4, 2, "", headerActifStyle);
            setCell(r4, 4, "PASSIF", headerPassifStyle);
            setCell(r4, 5, "", headerPassifStyle);
            setCell(r4, 6, "", headerPassifStyle);

            org.apache.poi.ss.usermodel.Row r5 =
                    sheet.createRow(5);
            r5.setHeightInPoints(18);
            setCell(r5, 0, "Désignation",   headerActifStyle);
            setCell(r5, 1, "Exercice " + exercice,
                    headerActifStyle);
            setCell(r5, 2, "Exercice " + (exercice-1),
                    headerActifStyle);
            setCell(r5, 4, "Désignation",   headerPassifStyle);
            setCell(r5, 5, "Exercice " + exercice,
                    headerPassifStyle);
            setCell(r5, 6, "Exercice " + (exercice-1),
                    headerPassifStyle);

            // ── Données ─────────────────────────────────────
            Map<String, Object> actifN  =
                    (Map<String, Object>) bilanN.get("actif");
            Map<String, Object> actifN1 =
                    (Map<String, Object>) bilanN1.get("actif");
            Map<String, Object> passifN  =
                    (Map<String, Object>) bilanN.get("passif");
            Map<String, Object> passifN1 =
                    (Map<String, Object>) bilanN1.get("passif");

            int rowA = 6;
            rowA = remplirSectionDouble(sheet, rowA,
                    (Map<String,Object>) actifN.get(
                            "actifImmobilise"),
                    (Map<String,Object>) actifN1.get(
                            "actifImmobilise"),
                    "ACTIF IMMOBILISÉ", 0,
                    sectionActifStyle, dataStyle,
                    numStyle, totalActifStyle);
            rowA++;
            rowA = remplirSectionDouble(sheet, rowA,
                    (Map<String,Object>) actifN.get(
                            "actifCirculant"),
                    (Map<String,Object>) actifN1.get(
                            "actifCirculant"),
                    "ACTIF CIRCULANT", 0,
                    sectionActifStyle, dataStyle,
                    numStyle, totalActifStyle);
            rowA++;
            rowA = remplirSectionDouble(sheet, rowA,
                    (Map<String,Object>) actifN.get(
                            "tresorerieActif"),
                    (Map<String,Object>) actifN1.get(
                            "tresorerieActif"),
                    "TRÉSORERIE - ACTIF", 0,
                    sectionActifStyle, dataStyle,
                    numStyle, totalActifStyle);
            rowA++;

            // Total Actif
            org.apache.poi.ss.usermodel.Row rTA =
                    getOrCreate(sheet, rowA++);
            rTA.setHeightInPoints(18);
            setCell(rTA, 0,
                    "TOTAL GÉNÉRAL ACTIF", totalActifStyle);
            setCellNum(rTA, 1,
                    toDouble(actifN.get("totalActif")),
                    totalActifStyle);
            setCellNum(rTA, 2,
                    toDouble(actifN1.get("totalActif")),
                    totalActifStyle);

            // PASSIF
            int rowP = 6;
            rowP = remplirSectionDouble(sheet, rowP,
                    (Map<String,Object>) passifN.get(
                            "financementPermanent"),
                    (Map<String,Object>) passifN1.get(
                            "financementPermanent"),
                    "CAPITAUX PROPRES", 4,
                    sectionPassifStyle, dataStyle,
                    numStyle, totalPassifStyle);
            rowP++;
            rowP = remplirSectionDouble(sheet, rowP,
                    (Map<String,Object>) passifN.get(
                            "passifCirculant"),
                    (Map<String,Object>) passifN1.get(
                            "passifCirculant"),
                    "DETTES", 4,
                    sectionPassifStyle, dataStyle,
                    numStyle, totalPassifStyle);
            rowP++;
            rowP = remplirSectionDouble(sheet, rowP,
                    (Map<String,Object>) passifN.get(
                            "tresoreriePassif"),
                    (Map<String,Object>) passifN1.get(
                            "tresoreriePassif"),
                    "TRÉSORERIE - PASSIF", 4,
                    sectionPassifStyle, dataStyle,
                    numStyle, totalPassifStyle);
            rowP++;

            // Total Passif
            int totRow = Math.max(rowA - 1, rowP);
            org.apache.poi.ss.usermodel.Row rTP =
                    getOrCreate(sheet, totRow);
            setCell(rTP, 4,
                    "TOTAL GÉNÉRAL PASSIF", totalPassifStyle);
            setCellNum(rTP, 5,
                    toDouble(passifN.get("totalPassif")),
                    totalPassifStyle);
            setCellNum(rTP, 6,
                    toDouble(passifN1.get("totalPassif")),
                    totalPassifStyle);

            // ── Notes ───────────────────────────────────────
            int noteRow = Math.max(rowA, rowP) + 2;
            org.apache.poi.ss.usermodel.Row rN =
                    sheet.createRow(noteRow);
            setCell(rN, 0, "Notes :", null);

            org.apache.poi.ss.usermodel.CellStyle noteBlue =
                    wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font noteFont =
                    wb.createFont();
            noteFont.setColor(org.apache.poi.ss.usermodel
                    .IndexedColors.BLUE.getIndex());
            noteFont.setItalic(true);
            noteBlue.setFont(noteFont);



            // ── Largeurs ─────────────────────────────────────
            sheet.setColumnWidth(0, 11000);
            sheet.setColumnWidth(1, 4500);
            sheet.setColumnWidth(2, 4500);
            sheet.setColumnWidth(3, 600);
            sheet.setColumnWidth(4, 11000);
            sheet.setColumnWidth(5, 4500);
            sheet.setColumnWidth(6, 4500);

            java.io.ByteArrayOutputStream out =
                    new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private int remplirSectionDouble(
            org.apache.poi.ss.usermodel.Sheet sheet,
            int rowIdx,
            Map<String, Object> sectionN,
            Map<String, Object> sectionN1,
            String titre,
            int col,
            org.apache.poi.ss.usermodel.CellStyle secStyle,
            org.apache.poi.ss.usermodel.CellStyle dataStyle,
            org.apache.poi.ss.usermodel.CellStyle numStyle,
            org.apache.poi.ss.usermodel.CellStyle totStyle) {

        org.apache.poi.ss.usermodel.Row rTitre =
                getOrCreate(sheet, rowIdx++);
        rTitre.setHeightInPoints(16);
        setCell(rTitre, col, titre, secStyle);
        setCell(rTitre, col+1, "", secStyle);
        setCell(rTitre, col+2, "", secStyle);

        // Fusionner les lignes des deux exercices par libellé
        List<Map<String,Object>> lignesN =
                (List<Map<String,Object>>) sectionN.get("lignes");
        List<Map<String,Object>> lignesN1 =
                (List<Map<String,Object>>) sectionN1.get("lignes");

        // Map compte → montant N-1
        Map<String, Double> mapN1 = new LinkedHashMap<>();
        for (Map<String,Object> l : lignesN1) {
            mapN1.put(l.get("compte").toString(),
                    toDouble(l.get("montant")));
        }

        for (Map<String,Object> l : lignesN) {
            org.apache.poi.ss.usermodel.Row r =
                    getOrCreate(sheet, rowIdx++);
            setCell(r, col,
                    l.get("libelle").toString(), dataStyle);
            setCellNum(r, col+1,
                    toDouble(l.get("montant")), numStyle);
            String cpt = l.get("compte").toString();
            setCellNum(r, col+2,
                    mapN1.getOrDefault(cpt, 0.0), numStyle);
        }

        // Total
        org.apache.poi.ss.usermodel.Row rTot =
                getOrCreate(sheet, rowIdx++);
        setCell(rTot, col, "TOTAL " + titre, totStyle);
        setCellNum(rTot, col+1,
                toDouble(sectionN.get("total")), totStyle);
        setCellNum(rTot, col+2,
                toDouble(sectionN1.get("total")), totStyle);

        return rowIdx;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        return ((Number) val).doubleValue();
    }

    private org.apache.poi.ss.usermodel.CellStyle createStyle(
            org.apache.poi.xssf.usermodel.XSSFWorkbook wb,
            boolean bold, short size, byte[] bgColor,
            org.apache.poi.ss.usermodel.HorizontalAlignment align,
            boolean italic) {
        org.apache.poi.ss.usermodel.CellStyle s =
                wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setBold(bold);
        f.setItalic(italic);
        f.setFontHeightInPoints(size);
        s.setFont(f);
        s.setAlignment(align);
        if (bgColor != null) {
            s.setFillForegroundColor(
                    new org.apache.poi.xssf.usermodel.XSSFColor(
                            bgColor, null));
            s.setFillPattern(org.apache.poi.ss.usermodel
                    .FillPatternType.SOLID_FOREGROUND);
        }
        return s;
    }

    private org.apache.poi.ss.usermodel.CellStyle createColorStyle(
            org.apache.poi.xssf.usermodel.XSSFWorkbook wb,
            byte[] bg, boolean bold, boolean whiteText) {
        org.apache.poi.ss.usermodel.CellStyle s =
                wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setBold(bold);
        if (whiteText) f.setColor(
                org.apache.poi.ss.usermodel
                        .IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(
                new org.apache.poi.xssf.usermodel.XSSFColor(
                        bg, null));
        s.setFillPattern(org.apache.poi.ss.usermodel
                .FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(org.apache.poi.ss.usermodel
                .HorizontalAlignment.LEFT);
        setBorders(s);
        return s;
    }




    // ── Helpers Excel ─────────────────────────────────────────────
    private int remplirSection(
            org.apache.poi.ss.usermodel.Sheet sheet,
            int rowIdx,
            Map<String, Object> section,
            String titreOverride,
            int colOffset,
            org.apache.poi.ss.usermodel.CellStyle sectionStyle,
            org.apache.poi.ss.usermodel.CellStyle dataStyle,
            org.apache.poi.ss.usermodel.CellStyle numStyle,
            org.apache.poi.ss.usermodel.CellStyle totalStyle) {

        org.apache.poi.ss.usermodel.Row rTitle =
                getOrCreate(sheet, rowIdx++);
        rTitle.setHeightInPoints(16);
        setCell(rTitle, colOffset, titreOverride, sectionStyle);

        List<Map<String, Object>> lignes =
                (List<Map<String, Object>>) section.get("lignes");
        for (Map<String, Object> l : lignes) {
            org.apache.poi.ss.usermodel.Row r =
                    getOrCreate(sheet, rowIdx++);
            setCell(r, colOffset,
                    l.get("libelle").toString(), dataStyle);
            setCellNum(r, colOffset + 1,
                    ((Number) l.get("montant")).doubleValue(), numStyle);
            setCellNum(r, colOffset + 2, 0, numStyle);
        }

        org.apache.poi.ss.usermodel.Row rTot =
                getOrCreate(sheet, rowIdx++);
        setCell(rTot, colOffset,
                "TOTAL " + titreOverride, totalStyle);
        setCellNum(rTot, colOffset + 1,
                ((Number) section.get("total")).doubleValue(), totalStyle);
        setCellNum(rTot, colOffset + 2, 0, totalStyle);

        return rowIdx;
    }

    private org.apache.poi.ss.usermodel.Row getOrCreate(
            org.apache.poi.ss.usermodel.Sheet sheet, int i) {
        org.apache.poi.ss.usermodel.Row r = sheet.getRow(i);
        return r != null ? r : sheet.createRow(i);
    }

    private void setCell(
            org.apache.poi.ss.usermodel.Row row, int col,
            String val,
            org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell c = row.createCell(col);
        c.setCellValue(val);
        if (style != null) c.setCellStyle(style);
    }

    private void setCellNum(
            org.apache.poi.ss.usermodel.Row row, int col,
            double val,
            org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell c = row.createCell(col);
        c.setCellValue(val);
        if (style != null) c.setCellStyle(style);
    }

    private void setBorders(
            org.apache.poi.ss.usermodel.CellStyle style) {
        style.setBorderTop(
                org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderBottom(
                org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderLeft(
                org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderRight(
                org.apache.poi.ss.usermodel.BorderStyle.THIN);
    }
    public java.time.LocalDate getDateCloture(
            UUID clientId, int exercice) {
        // Cherche la dernière date d'écriture de l'exercice
        return ecritureRepo
                .findTopByClientIdAndExerciceOrderByDateEcritureDesc(
                        clientId, String.valueOf(exercice))
                .map(e -> e.getDateEcriture())
                .orElse(java.time.LocalDate.of(exercice, 12, 31));
    }
}