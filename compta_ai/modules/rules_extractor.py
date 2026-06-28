# modules/rules_extractor.py
# Extraction par règles — rapide et précis
# pour les champs structurés
import re
from modules.logger_config import get_logger

log = get_logger("rules")


def parse_montant(texte: str) -> float:
    if not texte:
        return 0.0
    t = (str(texte).strip()
         .replace('\xa0', ' ')
         .replace('\u202f', ' '))
    t = re.sub(
        r'[€$£\u20ac]', '', t,
        flags=re.IGNORECASE)
    t = re.sub(
        r'\b(mad|dh|eur|usd|gbp)\b', '', t,
        flags=re.IGNORECASE).strip()

    virgules = t.count(',')
    points   = t.count('.')

    if virgules == 1 and points == 1:
        if t.rindex(',') > t.rindex('.'):
            t = t.replace('.', '').replace(',', '.')
        else:
            t = t.replace(',', '')
    elif virgules == 1:
        parts = t.split(',')
        if len(parts) == 2 and len(parts[1]) <= 2:
            t = t.replace(',', '.')
        else:
            t = t.replace(',', '')
    elif points > 1:
        last = t.rindex('.')
        t = t[:last].replace('.', '') + t[last:]

    try:
        v = float(re.sub(r'[^\d.]', '', t))
        return round(v, 2) if 0 < v < 10_000_000 \
            else 0.0
    except Exception:
        return 0.0


def extraire_champs_fiscaux(
        texte: str) -> dict:
    """
    Extrait champs fiscaux structurés
    avec haute précision (regex ciblés)
    """
    t = texte.lower()
    resultats = {
        'ice': '', 'if_fournisseur': '',
        'rc': '', 'siret': '',
        'tva_intra': '', 'ein': '',
        'numero_facture': '',
        'date_facture': '',
        'echeance': '',   
    }
    confiances = {}

    # ── ICE Maroc — 15 chiffres ──────────────────────
    m = re.search(r'ice\s*[:\-]?\s*(\d{15})', t)
    if m:
        resultats['ice'] = m.group(1)
        confiances['ice'] = 0.99

    # ── IF Maroc ──────────────────────────────────────
    m = re.search(
        r'(?:i\.?f\.?|identifiant\s+fiscal)'
        r'\s*[:\-]?\s*(\d{5,12})', t)
    if m:
        resultats['if_fournisseur'] = m.group(1)
        confiances['if_fournisseur'] = 0.95

    # ── RC Maroc ──────────────────────────────────────
    m = re.search(
        r'(?:r\.?c\.?|registre\s*(?:du\s*)?'
        r'commerce)\s*[:\-]?\s*(\d+)', t)
    if m:
        resultats['rc'] = m.group(1)
        confiances['rc'] = 0.9

    # ── SIRET France — 14 chiffres ───────────────────
    m = re.search(r'siret\s*[:\-]?\s*(\d{14})', t)
    if m:
        resultats['siret'] = m.group(1)
        confiances['siret'] = 0.99

    # ── TVA intracommunautaire UE ─────────────────────
    m = re.search(
        r'(?:n[o°]?\s*tva|tva\s*intra)'
        r'\s*[:\-]?\s*([A-Z]{2}\d[\d\s]{8,})', texte)
    if m:
        resultats['tva_intra'] = m.group(1).strip()
        confiances['tva_intra'] = 0.95

    # ── Numéro facture ────────────────────────────────
    patterns_fact = [
        (r'facture\s*(?:proforma\s*|avoir\s*)?'
         r'n[go°#]?\s*[:\-]?\s*'
         r'([A-Z0-9][A-Z0-9/\-]{0,19})', 0.95),
        (r'invoice\s*(?:no\.?|n[o°#]?|#)\s*'
         r'[:\-]?\s*([A-Z0-9][A-Z0-9/\-]{0,19})',
         0.9),
        (r'(?:^|\n)\s*(?:n[o°#]|ref)\s*[:\-]?\s*'
         r'([A-Z0-9][A-Z0-9/\-]{1,19})', 0.8),
        (r'devis\s*n[o°]?\s*[:\-]?\s*'
         r'([A-Z0-9/\-]{2,20})', 0.75),
    ]
    for p, conf in patterns_fact:
        m = re.search(
            p, texte, re.IGNORECASE | re.MULTILINE)
        if m:
            val = m.group(1).strip().rstrip('.,')
            if val and len(val) >= 1:
                resultats['numero_facture'] = \
                    val.upper()
                confiances['numero_facture'] = conf
                break

    # ── Date facture ──────────────────────────────────
    mots_date = [
        'date', 'émission', 'facturation',
        'établi', 'invoice date', 'issued']
    patterns_date = [
    r'(\d{2}[/\-\.]\d{2}[/\-\.]\d{4})',
    r'(\d{4}[/\-\.]\d{2}[/\-\.]\d{2})',
    r'(\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{4})',  # ← accepte les points et 1 chiffre
]
    # ── Échéance ──────────────────────────────────────────
    m = re.search(
        r'(?:éch[eé]ance|due\s*date|date\s*limite|expiry)\s*[:\-]?\s*'
        r'(\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{4})',
        texte, re.IGNORECASE)

    if m:
        resultats['echeance'] = m.group(1)
        confiances['echeance'] = 0.95
    # Avec contexte date
    for ligne in texte.split('\n'):
        if any(m in ligne.lower()
               for m in mots_date):
            for p in patterns_date:
                m = re.search(p, ligne)
                if m:
                    resultats['date_facture'] = \
                        m.group(1)
                    confiances['date_facture'] = 0.9
                    break
        if resultats['date_facture']:
            break
    # Sans contexte
    if not resultats['date_facture']:
        for p in patterns_date:
            m = re.search(p, texte)
            if m:
                resultats['date_facture'] = m.group(1)
                confiances['date_facture'] = 0.7
                break

    return resultats, confiances


def extraire_items_facture(texte: str) -> list:
    """
    Extrait les lignes d'articles du tableau facture
    Retourne liste de dicts avec
    description/quantite/prix_unitaire/total
    """
    items = []
    lignes = texte.split('\n')

    # Patterns pour ligne d'article
    # Format : description | qté | prix | total
    pattern_ligne = re.compile(
        r'^(.{3,40}?)\s+'       # description
        r'(\d+(?:[.,]\d+)?)\s+' # quantité
        r'(\d[\d\s.,]*)\s+'     # prix unitaire
        r'(\d[\d\s.,]*)$',      # total
        re.IGNORECASE
    )

    # Détecter début/fin tableau
    in_tableau = False
    headers_tableau = [
        'désignation', 'description', 'article',
        'produit', 'libellé', 'quantité', 'qté',
        'prix', 'montant', 'total'
    ]

    for ligne in lignes:
        l = ligne.strip()
        l_low = l.lower()

        # Détecter header tableau
        nb_headers = sum(
            1 for h in headers_tableau
            if h in l_low)
        if nb_headers >= 2:
            in_tableau = True
            continue

        # Fin tableau
        if in_tableau and any(
                k in l_low
                for k in ['total ht', 'total ttc',
                           'sous-total', 'tva']):
            in_tableau = False
            continue

        if not in_tableau or not l:
            continue

        # Essayer d'extraire item
        m = pattern_ligne.match(l)
        if m:
            desc = m.group(1).strip()
            qte  = parse_montant(m.group(2))
            pu   = parse_montant(m.group(3))
            tot  = parse_montant(m.group(4))

            if desc and tot > 0:
                items.append({
                    'description':    desc,
                    'quantite':       qte,
                    'prix_unitaire':  pu,
                    'total_ligne':    tot,
                })

    log.info(f"{len(items)} items extraits")
    return items


def extraire_montants_rules(
        texte: str) -> tuple[float, float, float, float]:
    """
    Extraction montants par règles structurelles
    Retourne (ht, tva_montant, ttc, taux_tva)
    Analyse ligne par ligne avec scoring
    """
    lignes = texte.split('\n')

    # Labels par type
    L_TTC = [
        r'net\s*[àa]\s*payer',
        r'total\s*t\.?t\.?c\.?(?:\s|$|:)',
        r'total\s*toutes?\s*taxes?',
        r'montant\s*t\.?t\.?c\.?(?:\s|$)',
        r'grand\s*total',
        r'total\s*(?:général|global)',
        r'amount\s*due',
        r'total\s*due',
    ]
    L_HT = [
        r'total\s*h\.?t\.?(?:\s|$|:)',
        r'montant\s*h\.?t\.?(?:\s|$)',
        r'hors\s*taxe[s]?(?:\s|$)',
        r'base\s*(?:imposable|h\.?t\.?)',
        r'subtotal(?:\s|$)',
        r'total\s*hors\s*t\.?v\.?a\.?',
        r'amount\s*(?:excl|before\s*tax)',
    ]
    L_TVA = [
        r't\.?v\.?a\.?\s*(?:\(?\d+%?\)?)?(?:\s|$|:)',
        r'taxe\s*(?:sur\s*la\s*valeur)?',
        r'montant\s*(?:de\s*la\s*)?t\.?v\.?a',
        r'vat\s*(?:amount|\d+%|\()',
        r'tax\s*amount',
    ]

    def montants_ligne(l: str) -> list:
        """Extrait montants valides d'une ligne"""
        vals = re.findall(
            r'(?<!\d)(\d{1,3}(?:[.\s]\d{3})*'
            r'(?:[,]\d{1,2})?'
            r'|\d+[.,]\d{1,2})(?!\d)',
            l)
        result = []
        for v in vals:
            parsed = parse_montant(v)
            # Ignorer si ressemble à un taux
            if parsed > 0.5 and parsed < 10_000_000:
                result.append(parsed)
        return sorted(set(result), reverse=True)

    ttc_candidats = []
    ht_candidats  = []
    tva_candidats = []
    taux_tva      = 20.0

    for i, ligne in enumerate(lignes):
        l     = ligne.strip()
        l_low = l.lower()

        if not l:
            continue

        montants = montants_ligne(l)

        # Classifier la ligne
        est_ttc = any(
            re.search(p, l_low) for p in L_TTC)
        est_ht  = any(
            re.search(p, l_low) for p in L_HT)
        est_tva = any(
            re.search(p, l_low) for p in L_TVA)

        # Éviter double classification
        if est_ttc:
            est_ht  = False
            est_tva = False

        if est_ttc and montants:
            ttc_candidats.append(
                (max(montants), i))
            log.info(
                f"TTC ligne {i}: {max(montants)}"
                f" — '{l[:50]}'")

        elif est_ht and montants:
            # Exclure si aussi TTC
            if not re.search(r'\bttc\b', l_low):
                ht_candidats.append(
                    (max(montants), i))
                log.info(
                    f"HT ligne {i}: {max(montants)}"
                    f" — '{l[:50]}'")

        elif est_tva and montants:
            # Exclure taux TVA (20%, 14%, etc.)
            grands = [
                v for v in montants if v > 1]
            if grands:
                tva_candidats.append(
                    (max(grands), i))
                log.info(
                    f"TVA ligne {i}: {max(grands)}"
                    f" — '{l[:50]}'")

        # Extraire taux TVA
        m = re.search(
            r'(?:tva|vat|taxe)\s*\(?'
            r'(\d+(?:[.,]\d+)?)\s*%', l_low)
        if m:
            t = float(m.group(1).replace(',', '.'))
            if t in [0, 2.1, 5, 5.5, 6, 7, 9,
                     10, 12, 14, 20, 21, 23]:
                taux_tva = t

    # Sélectionner meilleurs candidats
    ttc = max((v for v, _ in ttc_candidats),
              default=0.0)
    ht  = max((v for v, _ in ht_candidats
               if v != ttc),
              default=0.0)

    # TVA : prendre celle cohérente avec HT et TTC
    tva = 0.0
    if tva_candidats:
        if ht > 0 and ttc > 0:
            tva_attendue = round(ttc - ht, 2)
            # Chercher le candidat le plus proche
            best_tva = min(
                tva_candidats,
                key=lambda x: abs(x[0] - tva_attendue))
            if abs(best_tva[0] - tva_attendue) < 1.0:
                tva = best_tva[0]
            else:
                tva = max(
                    v for v, _ in tva_candidats)
        else:
            tva = max(v for v, _ in tva_candidats)

    return ht, tva, ttc, taux_tva


def extraire_fournisseur_rules(texte: str) -> tuple[str, float]:
    """
    Détection fournisseur robuste
    Retourne (nom, confiance)
    """
    lignes = [l.strip() for l in texte.split('\n')
              if l.strip()]

    # Mots signalant zone destinataire
    DEST = [
        'destinataire', 'client', 'bill to',
        'ship to', 'facturé à', 'adressé à',
        'vendu à', 'livré à', 'sold to']

    # Mots signalant émetteur explicite
    EMIT = [
        'émis par', 'émetteur', 'issued by',
        'from', 'votre fournisseur', 'seller']

    # Formes juridiques = indicateur fort
    FORMES = [
        r'\bSARL\b', r'\bSAS\b', r'\bSA\b',
        r'\bEURL\b', r'\bSASU\b', r'\bSNC\b',
        r'\bGIE\b', r'\bSARLAU\b', r'\bLTD\b',
        r'\bLLC\b', r'\bINC\b', r'\bCORP\b',
        r'\bGmbH\b', r'\bAG\b', r'\bSRL\b',
        r'\bETS\b', r'\bSTE\b',
    ]

    # Mots à ignorer
    IGNORER = [
        'facture', 'invoice', 'date', 'total',
        'tva', 'ht', 'ttc', 'tel', 'fax',
        '@', 'www.', 'http', 'adresse', 'siret',
        'ice', 'n°', 'page', 'objet', 'ref',
        'désignation', 'quantité', 'prix',
        'signature', 'france', 'maroc', '[nom',
        '[adresse', 'cp ', 'ville', 'téléphone',
        'mobile', 'email', 'logiciel', 'dhl',
        'fedex', 'ups', 'mode', 'modalité',
        'paiement', 'livraison', 'délai',
    ]

    # ── Méthode 1 : label émetteur ────────────────────
    for i, ligne in enumerate(lignes[:20]):
        l_low = ligne.lower()
        for label in EMIT:
            if label in l_low:
                m = re.search(
                    re.escape(label) +
                    r'\s*[:\-]?\s*(.+)',
                    ligne, re.IGNORECASE)
                if m:
                    val = m.group(1).strip()
                    if len(val) > 3:
                        return val, 0.95
                # Ligne suivante
                if i + 1 < len(lignes):
                    suiv = lignes[i + 1].strip()
                    if (len(suiv) > 3 and not any(
                            e in suiv.lower()
                            for e in IGNORER)):
                        return suiv, 0.9

    # ── Méthode 2 : zone avant destinataire ──────────
    fin_zone = len(lignes)
    for i, ligne in enumerate(lignes[:30]):
        if any(d in ligne.lower() for d in DEST):
            fin_zone = i
            break

    zone = lignes[:min(fin_zone, 15)]

    # ── Scorer chaque ligne ───────────────────────────
    candidats = []
    for i, ligne in enumerate(zone):
        if not ligne or len(ligne) < 3:
            continue
        l_low = ligne.lower()

        # Ignorer
        if any(e in l_low for e in IGNORER):
            continue
        if re.match(r'^\d', ligne):
            continue
        if '@' in ligne or 'www.' in l_low:
            continue
        if re.match(r'^\d+\s+\w', ligne):
            continue  # Adresse

        score = 0.0
        raisons = []

        # Forme juridique = fort
        for forme in FORMES:
            if re.search(forme, ligne,
                         re.IGNORECASE):
                score += 0.4
                raisons.append('forme_juridique')
                break

        # Position dans document
        if i == 0:
            score += 0.3
            raisons.append('premiere_ligne')
        elif i <= 2:
            score += 0.2
        elif i <= 5:
            score += 0.1

        # Majuscules
        mots_maj = re.findall(r'[A-Z]{2,}', ligne)
        if mots_maj:
            score += min(len(mots_maj) * 0.05, 0.2)
            raisons.append('majuscules')

        # Longueur raisonnable
        if 4 < len(ligne) < 50:
            score += 0.05

        # Chiffres nombreux = moins probable
        nb_chiffres = len(re.findall(r'\d', ligne))
        if nb_chiffres > 4:
            score -= 0.2

        # Ponctuation excessive
        if ligne.count('/') > 1:
            score -= 0.1

        if score > 0.1:
            candidats.append((score, ligne, raisons))
            log.info(
                f"Candidat fournisseur : '{ligne}' "
                f"score={score:.2f} {raisons}")

    if candidats:
        candidats.sort(
            key=lambda x: x[0], reverse=True)
        best_score, best_val, _ = candidats[0]
        conf = min(best_score, 0.9)
        log.info(
            f"Fournisseur : '{best_val}' "
            f"conf={conf:.2f}")
        return best_val, conf

    return '', 0.0