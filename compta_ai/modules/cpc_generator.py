# modules/cpc_generator.py — V3 FINAL
# ═══════════════════════════════════════════════════════════════
# Génération automatique du CPC (Compte de Produits et Charges)
# Conforme au Plan Comptable Marocain (PCM / CGNC)
# Structure : I→XIII comme sur les images fournies
# ═══════════════════════════════════════════════════════════════
from __future__ import annotations
from modules.logger_config import get_logger

log = get_logger("cpc_generator")


# ───────────────────────────────────────────────────────────────
# MAPPING COMPTES PCG → RUBRIQUES CPC
# Charges (classe 6) et Produits (classe 7)
# ───────────────────────────────────────────────────────────────
PRODUITS = {
    # I — Produits d'exploitation
    '7111': ('I',    "Ventes de marchandises (en l'état)"),
    '7121': ('I',    'Ventes de biens produits'),
    '7122': ('I',    'Ventes de services produits'),
    '7124': ('I',    'Variation de stocks de produits finis'),
    '7125': ('I',    'Variation de stocks de produits en cours'),
    '7131': ('I',    'Travaux, études et prestations'),
    '7141': ('I',    "Subventions d'exploitation"),
    '7161': ('I',    "Autres produits d'exploitation"),
    '7197': ('I',    "Reprises d'exploitation"),
    # IV — Produits financiers
    '7311': ('IV',   'Revenus des titres de participation'),
    '7321': ('IV',   'Produits des autres titres'),
    '7381': ('IV',   'Intérêts et produits assimilés'),
    '7385': ('IV',   'Gains de change'),
    '7397': ('IV',   'Reprises financières'),
    # VIII — Produits non courants
    '7511': ('VIII', "Produits de cession d'immobilisations"),
    '7513': ('VIII', 'Subventions reçues'),
    '7581': ('VIII', 'Produits non courants divers'),
    '7591': ('VIII', "Subventions d'équilibre"),
    '7597': ('VIII', 'Reprises non courantes'),
}

CHARGES = {
    # II — Charges d'exploitation
    '6111':  ('II',  "Achats revendus de marchandises"),
    '6112':  ('II',  "Achats de matières et fournitures"),
    '6121':  ('II',  "Achats consommés de matières premières"),
    '6122':  ('II',  "Achats consommés de matières et fournitures"),
    '6125':  ('II',  "Achats d'eau, gaz et électricité"),
    '6131':  ('II',  "Achats de travaux et prestations"),
    '6141':  ('II',  "Locations et charges locatives"),
    '6142':  ('II',  "Redevances de crédit-bail"),
    '6143':  ('II',  "Entretien et réparations"),
    '6144':  ('II',  "Primes d'assurances"),
    '6145':  ('II',  "Frais de télécommunications"),
    '6146':  ('II',  "Études et recherches"),
    '6147':  ('II',  "Transports sur achats"),
    '6148':  ('II',  "Autres charges externes"),
    '61263': ('II',  "Prestations de services"),
    '61365': ('II',  "Honoraires"),
    '6161':  ('II',  "Impôts et taxes directs"),
    '6169':  ('II',  "Autres impôts et taxes"),
    '6171':  ('II',  "Rémunérations du personnel"),
    '6174':  ('II',  "Charges sociales patronales"),
    '6175':  ('II',  "Charges sociales (CNSS / AMO)"),
    '6182':  ('II',  "Dotations aux amortissements"),
    '6191':  ('II',  "Dotations aux amortissements"),
    '6194':  ('II',  "Dotations aux provisions d'exploitation"),
    '6197':  ('II',  "Dotations d'exploitation"),
    # V — Charges financières
    '6311':  ('V',   "Charges d'intérêts"),
    '6321':  ('V',   "Pertes sur créances irrécouvrables"),
    '6385':  ('V',   "Pertes de change"),
    '6394':  ('V',   "Dotations financières aux amortissements"),
    '6397':  ('V',   "Dotations financières aux provisions"),
    # IX — Charges non courantes
    '6511':  ('IX',  "V.N.A. des immobilisations cédées"),
    '6512':  ('IX',  "Subventions accordées"),
    '6581':  ('IX',  "Charges non courantes diverses"),
    '6594':  ('IX',  "Dotations non courantes"),
    '6597':  ('IX',  "Dotations non courantes aux provisions"),
    # XII — Impôts sur les résultats
    '6701':  ('XII', "Impôt sur les sociétés (IS)"),
    '6702':  ('XII', "Contribution sociale de solidarité"),
}


# ───────────────────────────────────────────────────────────────
# STRUCTURE CPC (I → XIII) — conforme PCM
# ───────────────────────────────────────────────────────────────
RUBRIQUES = [
    # (code, type, titre, fn_calcul)
    ('I',    'produit', "Produits d'exploitation",         None),
    ('II',   'charge',  "Charges d'exploitation",          None),
    ('III',  'result',  "Résultat d'exploitation (I − II)",
     lambda p, c: p.get('I', 0) - c.get('II', 0)),

    ('IV',   'produit', "Produits financiers",             None),
    ('V',    'charge',  "Charges financières",             None),
    ('VI',   'result',  "Résultat financier (IV − V)",
     lambda p, c: p.get('IV', 0) - c.get('V', 0)),

    ('VII',  'result',  "Résultat courant (III + VI)",
     lambda p, c: (p.get('I', 0) - c.get('II', 0))
                + (p.get('IV', 0) - c.get('V', 0))),

    ('VIII', 'produit', "Produits non courants",           None),
    ('IX',   'charge',  "Charges non courantes",           None),
    ('X',    'result',  "Résultat non courant (VIII − IX)",
     lambda p, c: p.get('VIII', 0) - c.get('IX', 0)),

    ('XI',   'result',  "Résultat avant impôts (VII + X)",
     lambda p, c: (p.get('I', 0) - c.get('II', 0))
                + (p.get('IV', 0) - c.get('V', 0))
                + (p.get('VIII', 0) - c.get('IX', 0))),

    ('XII',  'charge',  "Impôts sur les résultats",        None),

    ('XIII', 'result',  "Résultat net (XI − XII)",
     lambda p, c: (p.get('I', 0) - c.get('II', 0))
                + (p.get('IV', 0) - c.get('V', 0))
                + (p.get('VIII', 0) - c.get('IX', 0))
                - c.get('XII', 0)),
]


# ───────────────────────────────────────────────────────────────
# ACCUMULATION DEPUIS PLUSIEURS FACTURES
# ───────────────────────────────────────────────────────────────
def accumuler_depuis_donnees(liste_donnees: list[dict]) -> dict:
    """
    Agrège les montants par compte PCG à partir d'une liste
    de résultats d'analyse de factures.

    Seuls les débits des comptes de charge (6xxx) et
    les crédits des comptes de produit (7xxx) sont comptés.
    Les comptes 3455, 4411, 5xxx sont ignorés (bilantaires).
    """
    totaux: dict[str, float] = {}

    for donnees in liste_donnees:
        for ligne in donnees.get('ecritures_comptables', []):
            compte = str(ligne.get('compte', '')).strip()
            debit  = float(ligne.get('debit',  0) or 0)
            credit = float(ligne.get('credit', 0) or 0)

            # Comptes de charge (6xxx) → débit
            if compte.startswith('6') and debit > 0:
                totaux[compte] = totaux.get(compte, 0) + debit

            # Comptes de produit (7xxx) → crédit
            elif compte.startswith('7') and credit > 0:
                totaux[compte] = totaux.get(compte, 0) + credit

    return totaux


# ───────────────────────────────────────────────────────────────
# GÉNÉRATION DU CPC
# ───────────────────────────────────────────────────────────────
def generer_cpc(
        totaux_comptes: dict,
        entreprise: str = "",
        periode_debut: str = "",
        periode_fin: str = "") -> dict:
    """
    Génère le CPC complet depuis les totaux par compte PCG.

    Retourne un dict structuré prêt pour l'API et l'affichage :
    {
      entreprise, periode,
      rubriques: [{rubrique, titre, type, lignes, total}],
      resultats: {exploitation, financier, courant, non_courant, avant_impots, net}
    }
    """
    # Agréger par rubrique CPC
    tot_p: dict[str, float] = {}   # produits par rubrique
    tot_c: dict[str, float] = {}   # charges par rubrique
    detail: dict[str, list] = {}   # lignes détaillées

    for compte, montant in totaux_comptes.items():
        if montant <= 0:
            continue

        if compte in PRODUITS:
            rb, lib = PRODUITS[compte]
            tot_p[rb] = round(tot_p.get(rb, 0) + montant, 2)
            detail.setdefault(rb, []).append({
                'compte': compte,
                'libelle': lib,
                'montant': round(montant, 2),
            })

        elif compte in CHARGES:
            rb, lib = CHARGES[compte]
            tot_c[rb] = round(tot_c.get(rb, 0) + montant, 2)
            detail.setdefault(rb, []).append({
                'compte': compte,
                'libelle': lib,
                'montant': round(montant, 2),
            })

        else:
            # Compte non mappé → tenter par préfixe
            _mapper_par_prefixe(compte, montant, tot_p, tot_c, detail)

    # Construire la liste des rubriques dans l'ordre
    rubriques_cpc = []
    for code, typ, titre, fn in RUBRIQUES:
        if typ == 'result':
            total = round(fn(tot_p, tot_c), 2) if fn else 0.0
            rubriques_cpc.append({
                'rubrique': code,
                'titre':    titre,
                'type':     'resultat',
                'lignes':   [],
                'total':    total,
            })
        else:
            src   = tot_p if typ == 'produit' else tot_c
            total = round(src.get(code, 0.0), 2)
            rubriques_cpc.append({
                'rubrique': code,
                'titre':    titre,
                'type':     typ,
                'lignes':   sorted(
                    detail.get(code, []),
                    key=lambda x: x['compte']),
                'total':    total,
            })

    # Résultats clés
    def _get(rb: str) -> float:
        for r in rubriques_cpc:
            if r['rubrique'] == rb:
                return r['total']
        return 0.0

    resultats = {
        'exploitation': _get('III'),
        'financier':    _get('VI'),
        'courant':      _get('VII'),
        'non_courant':  _get('X'),
        'avant_impots': _get('XI'),
        'net':          _get('XIII'),
    }

    log.info(
        f"✅ CPC généré — "
        f"Résultat exploitation={resultats['exploitation']:,.2f} | "
        f"Résultat net={resultats['net']:,.2f}")

    return {
        'entreprise': entreprise,
        'periode':    {'debut': periode_debut, 'fin': periode_fin},
        'rubriques':  rubriques_cpc,
        'resultats':  resultats,
        'totaux_comptes': totaux_comptes,   # pour debug
    }


def _mapper_par_prefixe(
        compte: str, montant: float,
        tot_p: dict, tot_c: dict, detail: dict):
    """
    Fallback : si le compte n'est pas dans PRODUITS/CHARGES,
    on tente un mapping par les 3 premiers chiffres.
    """
    prefixes_charge = {
        '611': ('II', "Achats de marchandises"),
        '612': ('II', "Achats consommés"),
        '613': ('II', "Achats de travaux"),
        '614': ('II', "Autres charges externes"),
        '615': ('II', "Autres charges externes"),
        '616': ('II', "Impôts et taxes"),
        '617': ('II', "Charges de personnel"),
        '618': ('II', "Autres charges d'exploitation"),
        '619': ('II', "Dotations d'exploitation"),
        '631': ('V',  "Charges financières"),
        '638': ('V',  "Charges financières"),
        '639': ('V',  "Dotations financières"),
        '651': ('IX', "Charges non courantes"),
        '658': ('IX', "Charges non courantes diverses"),
        '659': ('IX', "Dotations non courantes"),
        '670': ('XII',"Impôts sur les résultats"),
    }
    prefixes_produit = {
        '711': ('I',    "Ventes de marchandises"),
        '712': ('I',    "Ventes de produits"),
        '713': ('I',    "Ventes de services"),
        '714': ('I',    "Subventions d'exploitation"),
        '716': ('I',    "Autres produits d'exploitation"),
        '731': ('IV',   "Produits financiers"),
        '738': ('IV',   "Produits financiers"),
        '751': ('VIII', "Produits non courants"),
        '758': ('VIII', "Produits non courants divers"),
    }

    pref3 = compte[:3]
    if compte.startswith('6') and pref3 in prefixes_charge:
        rb, lib = prefixes_charge[pref3]
        tot_c[rb] = round(tot_c.get(rb, 0) + montant, 2)
        detail.setdefault(rb, []).append(
            {'compte': compte, 'libelle': lib, 'montant': round(montant, 2)})
        log.info(f"Compte {compte} mappé par préfixe → {rb}")
    elif compte.startswith('7') and pref3 in prefixes_produit:
        rb, lib = prefixes_produit[pref3]
        tot_p[rb] = round(tot_p.get(rb, 0) + montant, 2)
        detail.setdefault(rb, []).append(
            {'compte': compte, 'libelle': lib, 'montant': round(montant, 2)})
        log.info(f"Compte {compte} mappé par préfixe → {rb}")
    else:
        log.warning(f"Compte {compte} non mappé dans le CPC (montant={montant})")


# ───────────────────────────────────────────────────────────────
# AFFICHAGE TEXTE (console / logs)
# ───────────────────────────────────────────────────────────────
def afficher_cpc(cpc: dict) -> str:
    """Représentation texte du CPC conforme aux images PCM fournies."""
    L = []
    SEP = "─" * 65
    SEP2 = "═" * 65

    L.append(SEP2)
    L.append("      COMPTE DE PRODUITS ET CHARGES (CPC)")
    L.append("      Plan Comptable Marocain (PCM / CGNC)")
    if cpc.get('entreprise'):
        L.append(f"      Entreprise : {cpc['entreprise']}")
    p = cpc.get('periode', {})
    if p.get('debut') or p.get('fin'):
        L.append(f"      Du {p.get('debut','...')} au {p.get('fin','...')}")
    L.append(SEP2)
    L.append(f"  {'Rubrique':<6} {'Désignation':<38} {'Montant':>12}")
    L.append(SEP)

    for r in cpc.get('rubriques', []):
        rb  = r['rubrique']
        tit = r['titre']
        typ = r['type']
        tot = r['total']

        if typ == 'resultat':
            signe = "+" if tot >= 0 else ""
            L.append(f"\n  {rb:<6} {tit:<38} {signe}{tot:>12,.2f}")
            L.append(SEP)
        else:
            L.append(f"\n  {rb:<6} {tit}")
            for ligne in r.get('lignes', []):
                L.append(
                    f"         • {ligne['libelle']:<35} "
                    f"{ligne['montant']:>10,.2f}")
            if tot != 0 or r.get('lignes'):
                L.append(
                    f"         {'Total ' + rb:<39} {tot:>10,.2f}")

    L.append(SEP2)
    res = cpc.get('resultats', {})
    L.append(f"\n  RÉSUMÉ DES RÉSULTATS")
    L.append(f"  Résultat d'exploitation : {res.get('exploitation', 0):>12,.2f}")
    L.append(f"  Résultat financier      : {res.get('financier',    0):>12,.2f}")
    L.append(f"  Résultat courant        : {res.get('courant',       0):>12,.2f}")
    L.append(f"  Résultat non courant    : {res.get('non_courant',   0):>12,.2f}")
    L.append(f"  Résultat avant impôts   : {res.get('avant_impots',  0):>12,.2f}")
    L.append(f"  ── Impôts sur résultats : {cpc.get('totaux_comptes',{}).get('6701',0):>12,.2f}")
    L.append(f"  RÉSULTAT NET            : {res.get('net',           0):>12,.2f}")
    L.append(SEP2)
    return "\n".join(L)