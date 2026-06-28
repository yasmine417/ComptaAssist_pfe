# modules/parser.py — extraction robuste multi-format
import re
from modules.logger_config import get_logger

log = get_logger("parser")


# ════════════════════════════════════════════════════════
# UTILS
# ════════════════════════════════════════════════════════

def parse_montant(texte: str) -> float:
    if not texte:
        return 0.0
    t = (texte.strip()
         .replace('\xa0', '')
         .replace(' ', '')
         .replace('€', '')
         .replace('$', '')
         .replace('dh', '')
         .replace('DH', '')
         .replace('mad', '')
         .replace('MAD', ''))
    # Format marocain/européen : 1.039,90 → 1039.90
    if re.search(r'\d[.,]\d{3}[.,]\d{2}', t):
        t = re.sub(r'[.,](?=\d{3}[.,])', '', t)
    if ',' in t and '.' in t:
        if t.rindex(',') > t.rindex('.'):
            t = t.replace('.', '').replace(',', '.')
        else:
            t = t.replace(',', '')
    elif ',' in t:
        t = t.replace(',', '.')
    try:
        v = float(re.sub(r'[^\d.]', '', t))
        return round(v, 2) if v < 10_000_000 else 0.0
    except Exception:
        return 0.0


def scorer_champ(valeur, poids: float,
                  scores: list) -> None:
    if valeur and valeur not in (0, 0.0, ''):
        scores.append(poids)


# ════════════════════════════════════════════════════════
# EXTRACTION STRUCTURÉE — analyse ligne par ligne
# ════════════════════════════════════════════════════════

def analyser_lignes(texte: str) -> dict:
    """
    Analyse chaque ligne pour extraire les montants
    avec leur contexte — plus robuste que regex global
    """
    lignes = texte.split('\n')
    resultats = {
        'ht':  [],   # (valeur, ligne_originale)
        'tva': [],
        'ttc': [],
    }

    for ligne in lignes:
        l = ligne.strip()
        if not l:
            continue
        l_low = l.lower()

        # ── Chercher montants dans la ligne ───────────
        montants = re.findall(
            r'[\d\s]{1,10}[.,]\d{2}', l)
        montants_vals = [
            parse_montant(m) for m in montants
            if parse_montant(m) > 0]

        if not montants_vals:
            continue

        # ── Classifier la ligne ───────────────────────
        # Ligne TTC — priorité haute
        if re.search(
                r'\bttc\b|total\s*ttc|'
                r'net\s*[àa]\s*payer|'
                r'toutes?\s*taxes?\s*compr',
                l_low):
            # Prendre le plus grand montant de la ligne
            resultats['ttc'].append(
                (max(montants_vals), l))

        # Ligne TVA — attention aux faux positifs
        elif re.search(
                r'\btva\b|\bt\.v\.a\b|'
                r'taxe\s*(?:sur\s*la\s*valeur)?',
                l_low):
            # Exclure si contient "HT" aussi
            # (ligne de tableau avec colonne TVA%)
            if not re.search(
                    r'\bht\b|hors\s*taxe', l_low):
                # Prendre le montant le plus grand
                # (évite de prendre le taux ex: 20%)
                grands = [v for v in montants_vals
                          if v > 5]
                if grands:
                    resultats['tva'].append(
                        (max(grands), l))

        # Ligne HT
        elif re.search(
                r'\bht\b|hors\s*taxe|'
                r'total\s*h\.?t\.?|'
                r'montant\s*h\.?t\.?|'
                r'base\s*(?:imposable|ht)',
                l_low):
            if not re.search(r'\bttc\b', l_low):
                resultats['ht'].append(
                    (max(montants_vals), l))

        # Ligne "Total" simple (sans TTC/HT/TVA)
        elif re.search(r'^\s*total\s*[:\-]?\s*',
                       l_low):
            if not any(
                    k in l_low
                    for k in ['ttc', 'tva', 'ht',
                               'taxe', 'port']):
                resultats['ht'].append(
                    (max(montants_vals), l))

    return resultats


# ════════════════════════════════════════════════════════
# FONCTIONS D'EXTRACTION
# ════════════════════════════════════════════════════════

def extraire_numero_facture(texte: str,
                             scores: list) -> str:
    patterns = [
        # Numéro seul ex: "552", "N° 552"
        (r'facture\s*(?:proforma\s*)?n[go°#]?\s*[:\-]?\s*([A-Z0-9/\-]{1,20})', 0.95),
        (r'fact(?:ure)?\s*n[o°#]?\s*[:\-]?\s*([A-Z0-9/\-]{1,20})', 0.9),
        (r'invoice\s*[#n°]?\s*[:\-]?\s*([A-Z0-9/\-]{1,20})', 0.85),
        (r'(?:^|\n)\s*n[o°#]\s*[:\-]?\s*([A-Z0-9/\-]{2,20})', 0.8),
        (r'ref(?:érence)?\s*[:\-]?\s*([A-Z0-9/\-]{3,20})', 0.7),
        (r'bon\s*(?:de\s*commande)?\s*n[o°]?\s*[:\-]?\s*([A-Z0-9/\-]{2,})', 0.7),
    ]
    for p, poids in patterns:
        m = re.search(p, texte, re.IGNORECASE | re.MULTILINE)
        if m:
            val = m.group(1).strip().rstrip('.,')
            if val and not val.lower() in [
                    'de', 'du', 'la', 'le']:
                scorer_champ(val, poids, scores)
                log.info(f"Numéro facture : {val}")
                return val.upper()
    return ''


def extraire_date(texte: str, scores: list) -> str:
    patterns = [
        r'(\d{2}[/\-\.]\d{2}[/\-\.]\d{4})',
        r'(\d{4}[/\-\.]\d{2}[/\-\.]\d{2})',
        r'(\d{2}[/\-\.]\d{2}[/\-\.]\d{2})',
        # Format US : 10/14/2021
        r'(\d{1,2}/\d{1,2}/\d{4})',
    ]
    mots_contexte = [
        'date', 'émission', 'facturation',
        'facture', 'invoice'
    ]
    # Chercher d'abord avec contexte
    for ligne in texte.split('\n'):
        if any(m in ligne.lower()
               for m in mots_contexte):
            for p in patterns:
                m = re.search(p, ligne)
                if m:
                    scorer_champ(m.group(1), 0.9, scores)
                    return m.group(1)
    # Fallback : première date trouvée
    for p in patterns:
        m = re.search(p, texte)
        if m:
            scorer_champ(m.group(1), 0.75, scores)
            return m.group(1)
    return ''


def extraire_ice(texte: str, scores: list) -> str:
    m = re.search(r'ice\s*[:\-]?\s*(\d{15})',
                  texte.lower())
    if m:
        scorer_champ(m.group(1), 0.95, scores)
        return m.group(1)
    return ''


def extraire_if(texte: str, scores: list) -> str:
    m = re.search(
        r'(?:i\.?f\.?|identifiant\s+fiscal|'
        r'id\.?\s*fiscal)\s*[:\-]?\s*(\d{5,})',
        texte.lower())
    if m:
        scorer_champ(m.group(1), 0.9, scores)
        return m.group(1)
    return ''


def extraire_rc(texte: str, scores: list) -> str:
    m = re.search(
        r'(?:r\.?c\.?|reg(?:istre)?\s*(?:du\s*)?'
        r'commerce)\s*[:\-]?\s*(\d+)',
        texte.lower())
    if m:
        scorer_champ(m.group(1), 0.85, scores)
        return m.group(1)
    return ''


def extraire_montants(texte: str,
                       scores: list) -> tuple:
    """
    Extraction robuste HT / TVA / TTC
    Utilise analyse ligne par ligne
    """
    analyse = analyser_lignes(texte)

    ht  = 0.0
    tva = 0.0
    ttc = 0.0

    # TTC — prendre le plus grand (dernier total)
    if analyse['ttc']:
        ttc = max(v for v, _ in analyse['ttc'])
        scorer_champ(ttc, 0.9, scores)
        log.info(f"TTC trouvé : {ttc}")

    # TVA — prendre la valeur la plus cohérente
    if analyse['tva']:
        # Trier par valeur décroissante
        vals_tva = sorted(
            [v for v, _ in analyse['tva']],
            reverse=True)
        # Prendre la première valeur significative
        tva = vals_tva[0]
        scorer_champ(tva, 0.85, scores)
        log.info(f"TVA trouvée : {tva}")

    # HT — prendre la valeur correspondante
    if analyse['ht']:
        vals_ht = sorted(
            [v for v, _ in analyse['ht']],
            reverse=True)
        ht = vals_ht[0]
        scorer_champ(ht, 0.9, scores)
        log.info(f"HT trouvé : {ht}")

    return ht, tva, ttc


def extraire_taux_tva(texte: str,
                       scores: list) -> float:
    """
    Détecte le taux TVA dans le texte
    """
    # Chercher pattern "TVA(20%)" ou "TVA 20%" ou "20%"
    patterns = [
        r'tva\s*\(?(\d+)\s*%',
        r'taux\s*(?:de\s*)?tva\s*[:\-]?\s*(\d+)\s*%',
        r't\.v\.a\s*\(?(\d+)\s*%',
    ]
    for p in patterns:
        m = re.search(p, texte, re.IGNORECASE)
        if m:
            taux = int(m.group(1))
            if taux in [0, 7, 10, 14, 20]:
                scorer_champ(taux, 0.95, scores)
                return float(taux)

    # Chercher taux dans colonnes tableau
    for taux in [20, 14, 10, 7]:
        if (f'{taux}%' in texte
                or f'{taux} %' in texte):
            scorer_champ(taux, 0.9, scores)
            return float(taux)

    return 20.0  # Par défaut


def extraire_fournisseur(texte: str,
                          scores: list) -> str:
    """
    Détection intelligente du fournisseur
    Stratégie : le fournisseur est l'ÉMETTEUR
    de la facture, pas le destinataire
    """
    lignes = [l.strip() for l in texte.split('\n')
              if l.strip()]

    # Mots qui indiquent le destinataire
    # → ignorer tout ce qui suit
    mots_destinataire = [
        'destinataire', 'client', 'adressé à',
        'facturé à', 'bill to', 'ship to',
        'vendu à', 'livré à'
    ]

    # Mots qui indiquent l'émetteur
    mots_emetteur = [
        'émis par', 'émetteur', 'fournisseur',
        'vendeur', 'from', 'issued by',
        'notre entreprise', 'mon entreprise'
    ]

    # Mots à exclure complètement
    mots_exclus = [
        'facture', 'invoice', 'date', 'échéance',
        'délai', 'mode', 'modalité', 'livraison',
        'paiement', 'signature', 'total', 'tva',
        'ht', 'ttc', 'tel', 'fax', 'email',
        'adresse', 'siret', 'tva', 'ice', 'rc',
        'page', 'ref', 'objet', 'désignation',
        'quantité', 'prix', 'offre', 'valable',
        '[nom', '[adresse', 'prénom', 'cp ville',
        'france', 'maroc', 'contact', 'logiciel'
    ]

    # Indicateurs nom d'entreprise
    indicateurs_entreprise = [
        'sarl', 'sa ', 'sas', 'sasu', 'eurl',
        'snc', 'scs', 'gie', 'sarlau', 'ets ',
        'ste ', 'société', 'entreprise', 'group',
        'consulting', 'services', 'industries',
        'trading', 'office', 'agence', 'cabinet',
        'studio', 'solutions', 'technologies',
        'system', 'digital', 'maroc', 'corp'
    ]

    # Chercher d'abord avec contexte émetteur
    for i, ligne in enumerate(lignes):
        l_low = ligne.lower()
        if any(m in l_low for m in mots_emetteur):
            # Ligne suivante = nom fournisseur
            if i + 1 < len(lignes):
                candidat = lignes[i + 1].strip()
                if (len(candidat) > 3
                        and not any(
                            e in candidat.lower()
                            for e in mots_exclus)):
                    scorer_champ(candidat, 0.85, scores)
                    log.info(
                        f"Fournisseur (contexte) : "
                        f"{candidat}")
                    return candidat
            # Ou extraire de la même ligne
            m = re.search(
                r'(?:émis par|fournisseur)\s*[:\-]?\s*(.+)',
                ligne, re.IGNORECASE)
            if m:
                candidat = m.group(1).strip()
                if len(candidat) > 3:
                    scorer_champ(candidat, 0.8, scores)
                    return candidat

    # Chercher par indicateurs entreprise dans
    # les 20 premières lignes
    candidats = []
    zone_destinataire = False

    for i, ligne in enumerate(lignes[:25]):
        l_low = ligne.lower()

        # Détecter début zone destinataire → arrêter
        if any(m in l_low
               for m in mots_destinataire):
            zone_destinataire = True

        if zone_destinataire:
            continue

        # Ignorer lignes exclues
        if any(e in l_low for e in mots_exclus):
            continue

        # Ignorer lignes trop courtes ou numériques
        if len(ligne) < 4 or re.match(r'^\d', ligne):
            continue

        score = 0

        # Indicateur fort : mot-clé entreprise
        if any(ind in l_low
               for ind in indicateurs_entreprise):
            score += 4

        # Position dans le document
        if i < 3:
            score += 3  # Tout début = fort
        elif i < 8:
            score += 1

        # Majuscules = souvent nom entreprise
        if re.search(r'[A-Z]{3,}', ligne):
            score += 1

        # Longueur raisonnable
        if 5 < len(ligne) < 60:
            score += 1

        # Contient slash ou tiret (logo/nom)
        if '/' in ligne or ' - ' in ligne:
            score -= 1  # Moins probable

        if score >= 2:
            candidats.append((score, ligne))

    if candidats:
        candidats.sort(reverse=True)
        val = candidats[0][1]
        scorer_champ(val, 0.7, scores)
        log.info(f"Fournisseur (heuristique) : {val}")
        return val

    return ''


def detecter_type_operation(texte: str) -> str:
    t = texte.lower()

    # Score par catégorie
    scores_op = {}
    regles = {
        "ACHAT_LOYER":      ['loyer', 'location',
                             'bail', 'locatif'],
        "ACHAT_ASSURANCE":  ['assurance', 'prime',
                             'couverture'],
        "ACHAT_HONORAIRES": ['honoraire', 'avocat',
                             'notaire', 'expert',
                             'consultant', 'audit'],
        "ACHAT_ENERGIE":    ['électricité', 'electricite',
                             'redal', 'lydec', 'amendis'],
        "ACHAT_TELEPHONE":  ['téléphone', 'telephone',
                             'internet', 'iam', 'inwi',
                             'orange', 'maroc telecom',
                             'abonnement'],
        "ACHAT_TRANSPORT":  ['transport', 'livraison',
                             'fret', 'dhl', 'fedex',
                             'frais de port'],
        "ACHAT_SERVICE":    ['prestation', 'service',
                             'mission', 'maintenance',
                             'réparation', 'formation'],
        "ACHAT_MATIERE":    ['matière première',
                             'matériau', 'matiere'],
        "ACHAT_FOURNITURE": ['fourniture', 'papeterie',
                             'bureau', 'consommable'],
        "VENTE_MARCHANDISE": ['vente', 'avoir client'],
        "VENTE_SERVICE":     ['vente de service',
                              'prestation fournie'],
    }
    for type_op, mots in regles.items():
        s = sum(1 for m in mots if m in t)
        if s > 0:
            scores_op[type_op] = s

    if scores_op:
        best = max(scores_op, key=scores_op.get)
        log.info(
            f"Type opération : {best} "
            f"(score={scores_op[best]})")
        return best

    # Par défaut
    return "ACHAT_MARCHANDISE"


# ════════════════════════════════════════════════════════
# POINT D'ENTRÉE
# ════════════════════════════════════════════════════════

def extraire_donnees(texte: str) -> dict:
    scores = []

    # Montants via analyse lignes
    ht, tva, ttc = extraire_montants(texte, scores)
    tva_taux = extraire_taux_tva(texte, scores)

    # Calculs croisés si données partielles
    if ht == 0 and ttc > 0 and tva > 0:
        ht = round(ttc - tva, 2)
        log.info(f"HT calculé = TTC - TVA : {ht}")

    if tva == 0 and ht > 0 and tva_taux > 0:
        tva = round(ht * tva_taux / 100, 2)
        log.info(f"TVA calculée : {tva}")

    if ttc == 0 and ht > 0:
        ttc = round(ht + tva, 2)
        log.info(f"TTC calculé : {ttc}")

    return {
        'numero_facture':  extraire_numero_facture(
            texte, scores),
        'date_facture':    extraire_date(
            texte, scores),
        'ice':             extraire_ice(texte, scores),
        'if_fournisseur':  extraire_if(texte, scores),
        'rc':              extraire_rc(texte, scores),
        'fournisseur':     extraire_fournisseur(
            texte, scores),
        'montant_ht':      ht,
        'tva_taux':        tva_taux,
        'montant_tva':     tva,
        'montant_ttc':     ttc,
        'type_operation':  detecter_type_operation(texte),
        '_scores':         scores,
    }