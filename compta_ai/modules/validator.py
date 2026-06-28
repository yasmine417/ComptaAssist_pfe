# modules/validator.py
import re
from modules.logger_config    import get_logger
from modules.fiscal_countries import valider_taux

log = get_logger("validator")

# Tolérance comptable : 2 centimes
TOLERANCE = 0.02


def _corriger_tva(donnees: dict) -> dict:
    """
    Logique TVA avec priorité au calcul fiable
    Ordre de priorité :
    1. Si HT et taux connus → recalculer TVA
    2. Si HT et TTC connus → déduire TVA
    3. Si TVA et TTC connus → déduire HT
    4. Garder valeurs LLM
    """
    ht      = donnees.get('montant_ht',  0.0)
    tva     = donnees.get('montant_tva', 0.0)
    ttc     = donnees.get('montant_ttc', 0.0)
    taux    = donnees.get('tva_taux',    0.0)
    remise  = donnees.get('remise',      0.0)
    port    = donnees.get('frais_port',  0.0)
    autres  = donnees.get('autres_frais', 0.0)

    # Base de calcul TVA = HT - remise + frais
    base_tva = round(ht - remise + port + autres, 2)

    # Cas 1 : base et taux connus → TVA fiable
    if base_tva > 0 and taux > 0:
        tva_calc = round(base_tva * taux / 100, 2)
        if tva > 0 and abs(tva_calc - tva) > TOLERANCE:
            log.warning(
                f"TVA corrigée : "
                f"{tva} → {tva_calc}")
        donnees['montant_tva'] = tva_calc
        donnees['montant_ttc'] = round(
            base_tva + tva_calc, 2)
        return donnees

    # Cas 2 : HT et TTC connus → déduire TVA
    if ht > 0 and ttc > 0 and tva == 0:
        tva_deduite = round(ttc - base_tva, 2)
        if tva_deduite > 0:
            donnees['montant_tva'] = tva_deduite
            # Déduire taux approximatif
            if base_tva > 0:
                taux_calc = round(
                    tva_deduite / base_tva * 100, 1)
                taux_valides = [
                    0, 7, 10, 14, 20, 2.1, 5.5]
                taux_proche = min(
                    taux_valides,
                    key=lambda t: abs(t - taux_calc))
                donnees['tva_taux'] = taux_proche
            log.info(
                f"TVA déduite : {tva_deduite}")
        return donnees

    # Cas 3 : TVA et TTC → déduire HT
    if tva > 0 and ttc > 0 and ht == 0:
        ht_deduit = round(ttc - tva - port + autres, 2)
        if ht_deduit > 0:
            donnees['montant_ht'] = ht_deduit
            log.info(f"HT déduit : {ht_deduit}")
        return donnees

    # Cas 4 : recalculer TTC si HT et TVA connus
    if ht > 0 and tva > 0 and ttc == 0:
        donnees['montant_ttc'] = round(
            base_tva + tva, 2)

    return donnees


def _valider_items(donnees: dict) -> list:
    """Valider cohérence items vs total HT"""
    alertes = []
    items   = donnees.get('items', [])
    ht      = donnees.get('montant_ht', 0.0)

    if not items or ht == 0:
        return alertes

    # Recalculer total items
    total_items = 0.0
    for item in items:
        desc = item.get('description', '')
        pu   = item.get('prix_unitaire', 0.0)
        qte  = item.get('quantite', 1.0)
        rem  = item.get('remise_ligne', 0.0)
        tot  = item.get('total_ligne_ht', 0.0)

        # Vérifier cohérence ligne
        if pu > 0 and qte > 0:
            calc = round(pu * qte - rem, 2)
            if tot > 0 and abs(calc - tot) > 0.05:
                alertes.append(
                    f"Item '{desc[:30]}' : "
                    f"{pu} × {qte} = {calc} "
                    f"≠ {tot}")
            total_items += (
                tot if tot > 0 else calc)

    # Comparer somme items vs HT
    if total_items > 0:
        ecart = abs(total_items - ht)
        if ecart > 1.0:
            alertes.append(
                f"Somme items ({total_items}) "
                f"≠ HT ({ht}) "
                f"[écart={ecart}]")

    return alertes


def _score_confiance(donnees: dict,
                      alertes: list,
                      warnings: list) -> float:
    """
    Score de confiance réel basé sur
    complétude et cohérence
    """
    score = 0.0
    total = 0.0

    # Montants (poids 60%)
    champs_montants = [
        ('montant_ht',  0.2),
        ('montant_tva', 0.15),
        ('montant_ttc', 0.25),
    ]
    for champ, poids in champs_montants:
        total += poids
        if donnees.get(champ, 0) > 0:
            score += poids

    # Champs texte (poids 30%)
    champs_texte = [
        ('numero_facture', 0.1),
        ('date_facture',   0.1),
        ('fournisseur',    0.1),
    ]
    for champ, poids in champs_texte:
        total += poids
        if donnees.get(champ, ''):
            score += poids

    # Items (poids 10%)
    total += 0.1
    if donnees.get('items'):
        score += 0.1

    # Pénalités
    score -= len(alertes)  * 0.15
    score -= len(warnings) * 0.05

    return round(max(0.0, min(1.0,
        score / total if total else 0)), 2)


def valider(donnees: dict,
             pays: str = 'MA') -> dict:
    alertes  = []
    warnings = []

    # ── Correction TVA prioritaire ────────────────────
    donnees = _corriger_tva(donnees)

    ht   = donnees.get('montant_ht',  0.0)
    tva  = donnees.get('montant_tva', 0.0)
    ttc  = donnees.get('montant_ttc', 0.0)
    taux = donnees.get('tva_taux',    0.0)
    rem  = donnees.get('remise',      0.0)
    port = donnees.get('frais_port',  0.0)

    # ── Règle 1 : HT + TVA + frais = TTC ─────────────
    if ht > 0 and ttc > 0:
        base  = round(ht - rem + port, 2)
        calc  = round(base + tva, 2)
        ecart = abs(calc - ttc)
        if ecart > TOLERANCE:
            alertes.append(
                f"HT({ht}) - Remise({rem}) "
                f"+ Port({port}) + TVA({tva}) "
                f"= {calc} ≠ TTC({ttc}) "
                f"[écart={ecart:.2f}]")
        else:
            log.info(
                f"✅ Cohérence TTC "
                f"[écart={ecart:.3f}]")

    # ── Règle 2 : Taux TVA valide ─────────────────────
    if taux > 0:
        if not valider_taux(taux, pays):
            warnings.append(
                f"Taux TVA {taux}% "
                f"non standard pour {pays}")

    # ── Règle 3 : Montants positifs ───────────────────
    for champ, val in [
        ('montant_ht',  ht),
        ('montant_tva', tva),
        ('montant_ttc', ttc)
    ]:
        if val < 0:
            alertes.append(
                f"{champ} négatif : {val}")

    # ── Règle 4 : TTC ≥ HT ───────────────────────────
    if ttc > 0 and ht > 0 and ttc < ht - rem:
        alertes.append(
            f"TTC({ttc}) < HT net({ht - rem})")

    # ── Règle 5 : Remise ≤ HT ────────────────────────
    if rem > ht and ht > 0:
        alertes.append(
            f"Remise({rem}) > HT({ht})")

    # ── Règle 6 : Cohérence items ─────────────────────
    alertes_items = _valider_items(donnees)
    warnings.extend(alertes_items)

    # ── Règle 7 : Champs obligatoires Maroc ──────────
    if pays == 'MA':
        if not donnees.get('ice') \
                and not donnees.get('if_fournisseur'):
            warnings.append(
                "Facture MA : ICE et IF absents")

    # ── Score confiance ───────────────────────────────
    score = _score_confiance(
        donnees, alertes, warnings)

    donnees['alertes']         = alertes
    donnees['warnings']        = warnings
    donnees['valide']          = len(alertes) == 0
    donnees['score_confiance'] = score
    donnees['pays_detecte']    = pays

    return donnees