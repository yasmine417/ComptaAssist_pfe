package com.comptaassist.cabinet_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentLegalService {

    @Value("${gotenberg.url:http://localhost:3001}")
    private String gotenbergUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final CabinetService cabinetService;
    private static final DateTimeFormatter FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] genererLettreMission(Map<String, String> data) {
        return convertirHtmlEnPdf(htmlLettreMission(data));
    }

    public byte[] genererMandatTva(Map<String, String> data) {
        return convertirHtmlEnPdf(htmlMandatTva(data));
    }

    public String genererHtmlLettreMission(Map<String, String> data) {
        return htmlLettreMission(data);
    }

    public String genererHtmlMandatTva(Map<String, String> data) {
        return htmlMandatTva(data);
    }

    // ══════════════════════════════════════════════════════════
    // HTML — LETTRE DE MISSION
    // ══════════════════════════════════════════════════════════
    private String htmlLettreMission(Map<String, String> d) {
        String date     = LocalDate.now().format(FR);
        String ref      = "LM-" + LocalDate.now().getYear()
                + "-" + System.currentTimeMillis() % 10000;
        String services = buildServicesHtml(d);

        // ── Articles modifiables ───────────────────────────
        String art1 = v(d, "article1_contenu",
                "Dans le cadre de la présente lettre de mission, " +
                        "le Prestataire s'engage à assurer pour le compte " +
                        "du Client les prestations comptables et fiscales suivantes :");

        String art2texte = v(d, "article2_contenu",
                "Les honoraires sont payables le <strong>" +
                        v(d, "jourPaiement", "5") +
                        "</strong> de chaque mois par virement bancaire. " +
                        "Toute modification fera l'objet d'un avenant " +
                        "signé par les deux parties.");

        String art3 = v(d, "article3_contenu",
                "La présente lettre de mission est conclue pour " +
                        "une durée d'<strong>un (1) an</strong> à compter du " +
                        v(d, "dateDebut", date) +
                        ", renouvelable tacitement par périodes annuelles. " +
                        "Chaque partie peut y mettre fin moyennant un préavis " +
                        "de <strong>30 jours</strong> par lettre recommandée " +
                        "avec accusé de réception.");

        String art4 = v(d, "article4_contenu",
                "Le Client s'engage à fournir au Prestataire tous " +
                        "les documents comptables et justificatifs nécessaires " +
                        "dans les délais convenus. Le Prestataire s'engage à " +
                        "respecter le secret professionnel et à traiter les " +
                        "informations du Client avec la plus stricte " +
                        "confidentialité conformément aux dispositions " +
                        "légales en vigueur.");

        // ── Articles supplémentaires ───────────────────────
        StringBuilder articlesSupp = new StringBuilder();
        int idx = 5;
        while (d.containsKey("article" + idx + "_titre")) {
            String titre   = d.get("article" + idx + "_titre");
            String contenu = v(d, "article" + idx + "_contenu", "");
            if (titre != null && !titre.isBlank()) {
                articlesSupp.append(
                        "<div class='section'>" +
                                "<h2>Article " + idx + " — " + titre + "</h2>" +
                                "<p>" + contenu + "</p>" +
                                "</div>");
            }
            idx++;
        }

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>" +
                "* { margin:0; padding:0; box-sizing:border-box; }" +
                "body { font-family:'Times New Roman',serif; padding:40px 55px;" +
                "  color:#000; font-size:11px; line-height:1.6; }" +

                // Header
                ".header { display:flex; justify-content:space-between;" +
                "  align-items:flex-start; border-bottom:2px solid #000;" +
                "  padding-bottom:12px; margin-bottom:16px; }" +
                ".cabinet-name { font-size:17px; font-weight:bold;" +
                "  letter-spacing:1px; text-transform:uppercase; }" +
                ".cabinet-sub { font-size:9px; color:#333; margin-top:2px;" +
                "  text-transform:uppercase; letter-spacing:0.5px; }" +
                ".doc-ref { text-align:right; font-size:10px; line-height:1.7; }" +

                // Titre
                "h1 { font-size:14px; font-weight:bold; text-align:center;" +
                "  text-transform:uppercase; letter-spacing:2px;" +
                "  margin:14px 0 4px; border-top:1px solid #000;" +
                "  border-bottom:1px solid #000; padding:7px 0; }" +
                ".souligne { text-align:center; font-size:10px; color:#333;" +
                "  margin-bottom:14px; }" +

                // Introduction
                ".intro { margin-bottom:14px; padding:10px 14px;" +
                "  border-left:3px solid #000; background:#fafafa; }" +
                ".intro p { font-size:11px; margin-bottom:6px;" +
                "  text-align:justify; line-height:1.6; }" +
                ".intro p:last-child { margin-bottom:0; }" +

                // Parties
                ".parties { display:grid; grid-template-columns:1fr 1fr;" +
                "  gap:14px; margin-bottom:14px; }" +
                ".partie { border:1px solid #000; padding:9px 12px; }" +
                ".partie h3 { font-size:10px; font-weight:bold;" +
                "  text-transform:uppercase; border-bottom:1px solid #000;" +
                "  padding-bottom:3px; margin-bottom:6px; }" +
                ".partie p { font-size:10px; margin:2px 0; }" +
                ".partie .nom { font-weight:bold; font-size:11px; }" +

                // Sections
                ".section { margin-bottom:12px; }" +
                ".section h2 { font-size:11px; font-weight:bold;" +
                "  text-transform:uppercase; border-bottom:1px solid #000;" +
                "  padding-bottom:2px; margin-bottom:6px; letter-spacing:0.5px; }" +
                ".section p { font-size:11px; margin-bottom:5px;" +
                "  text-align:justify; line-height:1.6; }" +

                // Services
                ".services-list { list-style:none; padding:0; margin:6px 0; }" +
                ".services-list li { padding:2px 0 2px 16px; position:relative;" +
                "  font-size:11px; }" +
                ".services-list li:before { content:'—'; position:absolute;" +
                "  left:0; font-weight:bold; }" +

                // Honoraires
                ".honoraires-box { border:1px solid #000; padding:10px;" +
                "  text-align:center; margin:8px 0; }" +
                ".honoraires-box .montant { font-size:17px; font-weight:bold; }" +
                ".honoraires-box .label { font-size:10px; color:#333; margin-top:3px; }" +

                // Mention
                ".mention { border:1px solid #000; padding:6px 10px;" +
                "  font-size:10px; font-style:italic; margin:12px 0;" +
                "  text-align:center; }" +

                // Signatures
                ".signatures-zone { display:grid; grid-template-columns:1fr 1fr;" +
                "  gap:40px; margin-top:28px; }" +
                ".sig-box { text-align:center; }" +
                ".sig-label { font-size:10px; font-weight:bold;" +
                "  text-transform:uppercase; margin-bottom:4px;" +
                "  border-bottom:1px solid #000; padding-bottom:3px; }" +
                ".sig-area { height:60px; margin-bottom:5px;" +
                "  border-bottom:1px solid #000; }" +
                ".sig-nom { font-weight:bold; font-size:10px; }" +
                ".sig-titre { font-size:9px; color:#444; }" +

                // Footer
                ".footer { margin-top:20px; padding-top:8px;" +
                "  border-top:1px solid #000; font-size:8px;" +
                "  color:#555; text-align:center; }" +

                "</style></head><body>" +

                // ── En-tête ──────────────────────────────────────
                "<div class='header'>" +
                "  <div>" +
                "     <div class='cabinet-name'>" + v(d, "cabinetNom") + "</div>" +
                "    <div class='cabinet-sub'>Cabinet Comptable Agréé</div>" +
                "  </div>" +
                "  <div class='doc-ref'>" +
                "    <div><strong>Référence :</strong> " + ref + "</div>" +
                "    <div><strong>Date :</strong> " + date + "</div>" +
                "  </div>" +
                "</div>" +

                // ── Titre ─────────────────────────────────────────
                "<h1>Lettre de Mission</h1>" +
                "<div class='souligne'>Convention de prestations comptables et fiscales</div>" +

                // ── Introduction ──────────────────────────────────
                "<div class='intro'>" +
                "  <p>À l'attention de la direction de l'entité</p>" +
                "  <p><strong>Madame, Monsieur,</strong></p>" +
                "  <p>Nous vous remercions de la confiance que vous nous avez " +
                "témoignée en envisageant de nous confier en notre qualité " +
                "d'expert-comptable de votre entreprise une mission de tenue " +
                "comptable et fiscale.</p>" +
                "  <p>Nous vous confirmons que nous respectons les critères " +
                "d'indépendance et d'absence de conflits d'intérêts qui " +
                "s'imposent à nous vis-à-vis de votre entreprise.</p>" +
                "  <p>La présente lettre de mission, établie en application des " +
                "dispositions des articles 7, 8 et 9 du Code des devoirs " +
                "professionnels édicté par l'Ordre des Experts-Comptables du " +
                "Maroc conformément à la loi n° 15-89 réglementant la " +
                "profession, a pour objet de vous confirmer par écrit les " +
                "termes et les objectifs de notre mission, ainsi que la nature, " +
                "le périmètre et les limites de notre intervention.</p>" +
                "</div>" +

                // ── Parties ───────────────────────────────────────
                "<div class='section'>" +
                "<p>Entre les soussignés, il a été convenu et arrêté ce qui suit :</p>" +
                "</div>" +

                "<div class='parties'>" +
                "  <div class='partie'><h3>Le Prestataire</h3>" +
                "    <p class='nom'>" + v(d, "cabinetNom") + "</p>" +
                "    <p>Représenté par : " + v(d, "comptableNom") + "</p>" +
                "    <p>Email : " + v(d, "cabinetEmail") + "</p></div>" +
                "  <div class='partie'><h3>Le Client</h3>" +
                "    <p class='nom'>" + v(d, "clientNom") + "</p>" +
                "    <p>ICE : " + v(d, "clientIce") + "</p>" +
                "    <p>Email : " + v(d, "clientEmail") + "</p></div>" +
                "</div>" +

                // ── Article 1 ─────────────────────────────────────
                "<div class='section'>" +
                "  <h2>Article 1 — Objet de la mission</h2>" +
                "  <p>" + art1 + "</p>" +
                "  <ul class='services-list'>" + services + "</ul>" +
                "</div>" +

                // ── Article 2 ─────────────────────────────────────
                "<div class='section'>" +
                "  <h2>Article 2 — Honoraires</h2>" +
                "  <div class='honoraires-box'>" +
                "    <div class='montant'>" + v(d, "honoraires") + " MAD HT / mois</div>" +
                "    <div class='label'>Soit " + calculerTtc(d.get("honoraires")) +
                " MAD TTC (TVA 20%)</div>" +
                "  </div>" +
                "  <p>" + art2texte + "</p>" +
                "</div>" +

                // ── Article 3 ─────────────────────────────────────
                "<div class='section'>" +
                "  <h2>Article 3 — Durée</h2>" +
                "  <p>" + art3 + "</p>" +
                "</div>" +

                // ── Article 4 ─────────────────────────────────────
                "<div class='section'>" +
                "  <h2>Article 4 — Obligations des parties</h2>" +
                "  <p>" + art4 + "</p>" +
                "</div>" +

                // ── Articles supplémentaires ──────────────────────
                articlesSupp.toString() +

                // ── Mention + Signatures ──────────────────────────
                "<div class='mention'>Lu et approuvé — Bon pour accord</div>" +

                "<div class='signatures-zone'>" +
                "  <div class='sig-box'>" +
                "    <div class='sig-label'>Pour le Cabinet</div>" +
                "    <div class='sig-area'></div>" +
                "    <div class='sig-nom'>" + v(d, "cabinetNom") + "</div>" +
                "    <div class='sig-titre'>Le Comptable responsable</div>" +
                "  </div>" +
                "  <div class='sig-box'>" +
                "    <div class='sig-label'>Pour le Client</div>" +
                "    <div class='sig-area'></div>" +
                "    <div class='sig-nom'>" + v(d, "clientNom") + "</div>" +
                "    <div class='sig-titre'>Le Représentant légal — Cachet</div>" +
                "  </div>" +
                "</div>" +

                "<div class='footer'>Document généré par ComptaAssist — " + date +
                " — Document confidentiel</div>" +
                "</body></html>";
    }
    // ══════════════════════════════════════════════════════════
    // HTML — MANDAT TVA
    // ══════════════════════════════════════════════════════════
    private String htmlMandatTva(Map<String, String> d) {
        String date        = LocalDate.now().format(FR);
        String ref         = "MTV-" + LocalDate.now().getYear()
                + "-" + System.currentTimeMillis() % 10000;
        String periodicite = "mensuelle".equals(d.get("periodicite"))
                ? "mensuelle" : "trimestrielle";
        String periodeLib  = "mensuelle".equals(d.get("periodicite"))
                ? "chaque mois avant le 20 du mois suivant"
                : "chaque trimestre avant le 20 du premier mois du trimestre suivant";

        // ── Articles modifiables ───────────────────────────
        String art1 = v(d, "article1_contenu",
                "Par la présente, le Mandant autorise expressément " +
                        "le Mandataire à :");

        String art2 = v(d, "article2_contenu",
                "Le Mandant s'engage à communiquer au Mandataire " +
                        "toutes les pièces justificatives (factures d'achat " +
                        "et de vente) dans un délai minimum de <strong>7 jours" +
                        "</strong> avant la date limite de dépôt. Le Mandataire " +
                        "s'engage à déposer les déclarations dans les délais " +
                        "légaux et à informer le Mandant de tout avis reçu " +
                        "de l'administration fiscale.");

        String art3 = v(d, "article3_contenu",
                "Le présent mandat est valable pour l'exercice fiscal " +
                        "<strong>" + LocalDate.now().getYear() + "</strong> " +
                        "et reconductible tacitement. Il peut être révoqué à " +
                        "tout moment par lettre recommandée avec accusé de " +
                        "réception adressée au Mandataire.");

        // ── Articles supplémentaires ───────────────────────
        StringBuilder articlesSupp = new StringBuilder();
        int idx = 4;
        while (d.containsKey("article" + idx + "_titre")) {
            String titre   = d.get("article" + idx + "_titre");
            String contenu = v(d, "article" + idx + "_contenu", "");
            if (titre != null && !titre.isBlank()) {
                articlesSupp.append(
                        "<div class='section'>" +
                                "<h2>Article " + idx + " — " + titre + "</h2>" +
                                "<p>" + contenu + "</p>" +
                                "</div>");
            }
            idx++;
        }

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>" +
                "* { margin:0; padding:0; box-sizing:border-box; }" +
                "body { font-family:'Times New Roman',serif; padding:50px 60px;" +
                "  color:#000; font-size:12px; line-height:1.7; }" +
                ".header { display:flex; justify-content:space-between;" +
                "  align-items:flex-start; border-bottom:2px solid #000;" +
                "  padding-bottom:16px; margin-bottom:28px; }" +
                ".cabinet-name { font-size:18px; font-weight:bold;" +
                "  letter-spacing:1px; text-transform:uppercase; }" +
                ".cabinet-sub { font-size:10px; color:#333; margin-top:3px;" +
                "  text-transform:uppercase; letter-spacing:0.5px; }" +
                ".doc-ref { text-align:right; font-size:11px; line-height:1.8; }" +
                "h1 { font-size:15px; font-weight:bold; text-align:center;" +
                "  text-transform:uppercase; letter-spacing:2px;" +
                "  margin:24px 0 6px; border-top:1px solid #000;" +
                "  border-bottom:1px solid #000; padding:10px 0; }" +
                ".souligne { text-align:center; font-size:11px; color:#333;" +
                "  margin-bottom:28px; }" +
                ".parties { display:grid; grid-template-columns:1fr 1fr;" +
                "  gap:20px; margin-bottom:24px; }" +
                ".partie { border:1px solid #000; padding:12px 14px; }" +
                ".partie h3 { font-size:11px; font-weight:bold;" +
                "  text-transform:uppercase; border-bottom:1px solid #000;" +
                "  padding-bottom:4px; margin-bottom:8px; }" +
                ".partie p { font-size:11px; margin:2px 0; }" +
                ".partie .nom { font-weight:bold; font-size:12px; }" +
                ".section { margin-bottom:20px; }" +
                ".section h2 { font-size:12px; font-weight:bold;" +
                "  text-transform:uppercase; border-bottom:1px solid #000;" +
                "  padding-bottom:3px; margin-bottom:8px; letter-spacing:0.5px; }" +
                ".section p { font-size:12px; margin-bottom:7px; text-align:justify; }" +
                ".services-list { list-style:none; padding:0; margin:8px 0; }" +
                ".services-list li { padding:4px 0 4px 18px; position:relative; font-size:12px; }" +
                ".services-list li:before { content:'—'; position:absolute; left:0; font-weight:bold; }" +
                ".periodicite-box { border:1px solid #000; padding:14px;" +
                "  text-align:center; margin:12px 0; }" +
                ".periodicite-box .val { font-size:16px; font-weight:bold;" +
                "  text-transform:uppercase; letter-spacing:1px; }" +
                ".periodicite-box .sub { font-size:11px; color:#333; margin-top:4px; }" +
                ".info-box { border:1px solid #000; padding:10px 14px;" +
                "  font-size:11px; margin:12px 0; font-style:italic; }" +
                ".mention { border:1px solid #000; padding:8px 12px;" +
                "  font-size:11px; font-style:italic; margin:16px 0; text-align:center; }" +
                ".signatures-zone { display:grid; grid-template-columns:1fr 1fr;" +
                "  gap:50px; margin-top:40px; }" +
                ".sig-box { text-align:center; }" +
                ".sig-label { font-size:11px; font-weight:bold; text-transform:uppercase;" +
                "  margin-bottom:6px; border-bottom:1px solid #000; padding-bottom:4px; }" +
                ".sig-area { height:70px; margin-bottom:6px; border-bottom:1px solid #000; }" +
                ".sig-nom { font-weight:bold; font-size:11px; }" +
                ".sig-titre { font-size:10px; color:#444; }" +
                ".footer { margin-top:28px; padding-top:10px; border-top:1px solid #000;" +
                "  font-size:9px; color:#555; text-align:center; }" +
                "</style></head><body>" +

                "<div class='header'>" +
                "  <div><div class='cabinet-name'>" + v(d, "cabinetNom") + "</div>" +
                "  <div class='cabinet-sub'>Cabinet Comptable Agréé</div></div>" +
                "  <div class='doc-ref'>" +
                "    <div><strong>Référence :</strong> " + ref + "</div>" +
                "    <div><strong>Date :</strong> " + date + "</div>" +
                "  </div>" +
                "</div>" +

                "<h1>Mandat de Représentation Fiscale</h1>" +
                "<div class='souligne'>Autorisation de dépôt des déclarations de TVA</div>" +

                "<div class='parties'>" +
                "  <div class='partie'><h3>Le Mandataire</h3>" +
                "    <p class='nom'>" + v(d, "cabinetNom") + "</p>" +
                "    <p>Représenté par : " + v(d, "comptableNom") + "</p>" +
                "    <p>Email : " + v(d, "cabinetEmail") + "</p></div>" +
                "  <div class='partie'><h3>Le Mandant (Client)</h3>" +
                "    <p class='nom'>" + v(d, "clientNom") + "</p>" +
                "    <p>ICE : " + v(d, "clientIce") + "</p>" +
                "    <p>IF : " + v(d, "clientIf", "—") + "</p>" +
                "    <p>Email : " + v(d, "clientEmail") + "</p></div>" +
                "</div>" +

                "<div class='section'>" +
                "  <h2>Objet du Mandat</h2>" +
                "  <p>" + art1 + "</p>" +
                "  <ul class='services-list'>" +
                "    <li>Établir et déposer les déclarations de TVA auprès de la Direction Générale des Impôts (DGI)</li>" +
                "    <li>Effectuer les télé-déclarations via le portail Simpl-TVA ou tout autre portail de la DGI</li>" +
                "    <li>Recevoir les avis et correspondances relatifs à la TVA</li>" +
                "    <li>Représenter le Mandant lors des vérifications fiscales liées à la TVA</li>" +
                "  </ul>" +
                "</div>" +

                "<div class='section'>" +
                "  <h2>Périodicité des déclarations</h2>" +
                "  <div class='periodicite-box'>" +
                "    <div class='val'>TVA " + periodicite + "</div>" +
                "    <div class='sub'>Dépôt " + periodeLib + "</div>" +
                "  </div>" +
                "</div>" +

                "<div class='section'>" +
                "  <h2>Engagements des parties</h2>" +
                "  <p>" + art2 + "</p>" +
                "</div>" +

                "<div class='section'>" +
                "  <h2>Durée et révocation</h2>" +
                "  <p>" + art3 + "</p>" +
                "</div>" +

                articlesSupp.toString() +

                "<div class='info-box'>Ce mandat est établi conformément aux dispositions du " +
                "Code Général des Impôts marocain et de la Loi n°15-97 formant Code de " +
                "recouvrement des créances publiques.</div>" +

                "<div class='mention'>Lu et approuvé — Bon pour accord</div>" +

                "<div class='signatures-zone'>" +
                "  <div class='sig-box'>" +
                "    <div class='sig-label'>Le Mandataire</div>" +
                "    <div class='sig-area'></div>" +
                "    <div class='sig-nom'>" + v(d, "cabinetNom") + "</div>" +
                "    <div class='sig-titre'>Cabinet comptable</div>" +
                "  </div>" +
                "  <div class='sig-box'>" +
                "    <div class='sig-label'>Le Mandant</div>" +
                "    <div class='sig-area'></div>" +
                "    <div class='sig-nom'>" + v(d, "clientNom") + "</div>" +
                "    <div class='sig-titre'>Cachet et signature</div>" +
                "  </div>" +
                "</div>" +

                "<div class='footer'>Document généré par ComptaAssist — " + date +
                " — Document confidentiel</div>" +
                "</body></html>";
    }


    // ══════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════

    private String v(Map<String, String> d, String key) {
        return v(d, key, "—");
    }

    private String v(Map<String, String> d, String key, String defaut) {
        String val = d.get(key);
        return (val != null && !val.isBlank()) ? val : defaut;
    }

    private String buildServicesHtml(Map<String, String> d) {
        StringBuilder sb = new StringBuilder();
        String[] services = {
                "tenue_comptable",  "Tenue de la comptabilité générale",
                "declaration_tva",  "Établissement et dépôt des déclarations de TVA",
                "declaration_is",   "Déclaration de l'Impôt sur les Sociétés (IS)",
                "declaration_ir",   "Déclaration de l'Impôt sur le Revenu (IR)",
                "bilan_annuel",     "Établissement du bilan et des états de synthèse annuels",
                "conseil_fiscal",   "Conseil et assistance fiscale",
                "audit_interne",    "Audit interne et contrôle de gestion"
        };
        for (int i = 0; i < services.length; i += 2) {
            if ("true".equals(d.get(services[i]))) {
                sb.append("<li>").append(services[i + 1]).append("</li>");
            }
        }
        String custom = d.get("services_custom");
        if (custom != null && !custom.isBlank()) {
            for (String service : custom.split("[,\n]+")) {
                String s = service.trim();
                if (!s.isEmpty()) sb.append("<li>").append(s).append("</li>");
            }
        }
        if (sb.isEmpty())
            sb.append("<li>Tenue de la comptabilité générale</li>");
        return sb.toString();
    }

    private String calculerTtc(String honorairesStr) {
        try {
            double ht  = Double.parseDouble(
                    honorairesStr.replace(",", ".").trim());
            return String.format("%.2f", ht * 1.20);
        } catch (Exception e) {
            return "—";
        }
    }

    // ══════════════════════════════════════════════════════════
    // CONVERTIR HTML → PDF via Gotenberg
    // ══════════════════════════════════════════════════════════
    private byte[] convertirHtmlEnPdf(String html) {
        try {
            String boundary = "BOUNDARY" + System.currentTimeMillis();
            String CRLF     = "\r\n";
            byte[] part1 = (
                    "--" + boundary + CRLF +
                            "Content-Disposition: form-data; name=\"files\";" +
                            " filename=\"index.html\"" + CRLF +
                            "Content-Type: text/html" + CRLF + CRLF
            ).getBytes("UTF-8");
            byte[] part2 = html.getBytes("UTF-8");
            byte[] part3 = (CRLF + "--" + boundary + "--" + CRLF)
                    .getBytes("UTF-8");
            byte[] body  = concat(part1, part2, part3);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type",
                    "multipart/form-data; boundary=" + boundary);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    gotenbergUrl + "/forms/chromium/convert/html",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    byte[].class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Erreur Gotenberg : {}", e.getMessage());
            throw new RuntimeException(
                    "Erreur génération PDF : " + e.getMessage());
        }
    }

    private byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] a : arrays) totalLen += a.length;
        byte[] result = new byte[totalLen];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}