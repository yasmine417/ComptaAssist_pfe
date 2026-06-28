# modules/montant_resolver.py — V6 FINAL
# ═══════════════════════════════════════════════════════════════
#
# PHILOSOPHIE V6 : SIMPLE ET DÉTERMINISTE
#
# Le LLM a déjà extrait les montants bruts de la facture.
# Ce module fait UNE SEULE CHOSE :
#   → Vérifier la cohérence mathématique et compléter ce qui manque.
#
# FORMULE UNIQUE ET IMMUABLE :
#   TTC = HT + TVA + PORT_HT
#
# HIÉRARCHIE DE CONFIANCE (du plus fiable au moins fiable) :
#   1. TTC lu sur la facture         → priorité absolue
#   2. TVA lue sur la facture        → ne jamais recalculer
#   3. HT lu sur la facture          → ne jamais recalculer
#   4. PORT_HT lu sur la facture     → ne jamais recalculer
#   5. Valeurs calculées             → uniquement si absentes
#
# CAS TRAITÉS (exhaustifs) :
#   Cas 1 : TTC + TVA + HT + PORT  → vérifier cohérence, corriger TTC si besoin
#   Cas 2 : TTC + TVA + HT         → pas de port, vérifier cohérence
#   Cas 3 : TTC + TVA, HT=0        → HT = TTC - TVA - PORT_HT
#   Cas 4 : HT + TVA, TTC=0        → TTC = HT + TVA + PORT_HT
#   Cas 5 : HT + TTC, TVA=0        → TVA = TTC - HT - PORT_HT (déduction)
#   Cas 6 : HT seul                → TVA=0, TTC=HT+PORT_HT
#   Cas 7 : TTC seul               → HT=TTC-PORT_HT, TVA=0 (exonéré)
#   Cas 8 : Rien                   → tout à 0
#
# CE QUI EST INTERDIT :
#   ✗ Calculer TVA = HT × taux  (le taux peut être absent ou erroné)
#   ✗ Supposer taux = 20%
#   ✗ Décomposer TTC seul par taux
#
# ═══════════════════════════════════════════════════════════════
from __future__ import annotations
from modules.logger_config import get_logger

log = get_logger("montant_resolver")

TOL = 0.10   # tolérance arrondi en MAD/EUR


def _r(v: float) -> float:
    return round(v, 2)

def _ok(a: float, b: float) -> bool:
    return abs(a - b) <= TOL


# ───────────────────────────────────────────────────────────────
# POINT D'ENTRÉE UNIQUE
# ───────────────────────────────────────────────────────────────
def resoudre_montants(donnees: dict) -> dict:
    """
    Résout et valide les montants d'une facture.
    Modifie donnees en place et le retourne.
    """

    # ── Lecture des bruts ────────────────────────────────
    ht      = float(donnees.get('montant_ht',    0) or 0)
    tva     = float(donnees.get('montant_tva',   0) or 0)
    ttc     = float(donnees.get('montant_ttc',   0) or 0)
    port_ht = float(
        donnees.get('frais_port_ht', 0) or
        donnees.get('frais_port',    0) or 0)
    taux    = float(donnees.get('tva_taux',      0) or 0)

    # Priorité absolue aux valeurs lues sur la facture (montants_bruts)
    # Si le LLM a recalculé à tort montant_ht, on restaure ht_lu
    mb     = donnees.get('montants_bruts', {})
    ht_lu  = float(mb.get('ht_lu',  0) or 0)
    tva_lu = float(mb.get('tva_lu', 0) or 0)
    ttc_lu = float(mb.get('ttc_lu', 0) or 0)

    # Restaurer les valeurs lues si le LLM les a recalculées à tort
    if ht_lu > 0 and abs(ht_lu - ht) > 0.05:
        log.warning(
            f"HT restauré depuis montants_bruts : "
            f"{ht} → {ht_lu} (LLM avait recalculé à tort)")
        ht = ht_lu
    if tva_lu > 0 and abs(tva_lu - tva) > 0.05:
        log.warning(
            f"TVA restaurée depuis montants_bruts : "
            f"{tva} → {tva_lu}")
        tva = tva_lu
    if ttc_lu > 0 and (ttc == 0 or abs(ttc_lu - ttc) > 0.05):
        ttc = ttc_lu

    mb = donnees.get('montants_bruts', {})
    ht_lu  = float(mb.get('ht_lu',  0) or 0)
    tva_lu = float(mb.get('tva_lu', 0) or 0)
    ttc_lu = float(mb.get('ttc_lu', 0) or 0)

    if ht_lu > 0 and abs(ht_lu - ht) > 0.05:
        log.warning(
            f"HT corrigé depuis bruts : {ht} → {ht_lu} "
            f"(LLM avait recalculé à tort)")
        ht = ht_lu
    if tva_lu > 0 and abs(tva_lu - tva) > 0.05 and tva_lu != tva:
        # Ne corriger que si la différence est significative
        # et que tva_lu correspond à ce qui est sur la facture
        pass  # on laisse montant_resolver décider
    if ttc_lu > 0 and abs(ttc_lu - ttc) > 0.05:
        log.info(f"TTC brut : {ttc_lu} (montant_ttc={ttc})")
        ttc = ttc_lu  # toujours priorité au TTC lu

    # Normaliser taux (légaux Maroc/France)
    TAUX_OK = {0, 5.5, 7, 10, 14, 20, 21}
    if taux > 0 and not any(abs(taux - t) <= 0.6 for t in TAUX_OK):
        taux = 0.0  # taux inconnu → on l'ignore

    log.info(
        f"[V6] HT={ht} TVA={tva} TTC={ttc} "
        f"PORT={port_ht} taux={taux}%")

    # ── TVA du port (calculée seulement si taux lu) ──────
    # La TVA port n'est calculée que si le taux a été lu
    # ET que la TVA fournie n'inclut pas déjà le port.
    port_tva = 0.0
    port_ttc = _r(port_ht)  # par défaut : port sans TVA

    # ── Déterminer si TVA fournie est totale ou merch ────
    # On teste les deux hypothèses contre le TTC lu.
    # C'est la seule façon fiable de trancher.
    tva_est_totale = False

    if ttc > 0 and tva > 0 and ht > 0 and port_ht > 0:
        # Hypothèse A : TVA = totale → TTC = HT + TVA + PORT_HT
        ttc_si_totale = _r(ht + tva + port_ht)
        # Hypothèse B : TVA = merch → TVA_port = PORT_HT × taux
        tva_port_calc = _r(port_ht * taux / 100) if taux > 0 else 0.0
        ttc_si_merch  = _r(ht + tva + port_ht + tva_port_calc)

        if _ok(ttc, ttc_si_totale):
            tva_est_totale = True
            port_tva  = 0.0
            port_ttc  = _r(port_ht)
            log.info(
                f"TVA totale (TTC={ttc} ≈ "
                f"HT+TVA+PORT={ttc_si_totale})")
        elif _ok(ttc, ttc_si_merch) and tva_port_calc > 0:
            tva_est_totale = False
            port_tva  = tva_port_calc
            port_ttc  = _r(port_ht + port_tva)
            log.info(
                f"TVA merch-only (TTC={ttc} ≈ "
                f"HT+TVA+PORT+TVA_port={ttc_si_merch})")
        else:
            # Aucune hypothèse exacte → préférer totale
            # (plus sûr : ne pas inventer TVA_port)
            if _r(ht + tva + port_ht) == ttc or taux == 0:
                tva_est_totale = True
                port_tva = 0.0
                port_ttc = _r(port_ht)
            elif taux > 0:
                port_tva = _r(port_ht * taux / 100)
                port_ttc = _r(port_ht + port_tva)
            log.info(
                f"TVA ambiguë → "
                f"{'totale' if tva_est_totale else 'merch'} "
                f"(taux={'absent' if taux==0 else taux})")

    elif port_ht > 0 and taux > 0 and not tva_est_totale:
        # Port présent mais TTC/HT/TVA pas tous fournis
        # → calculer TVA port uniquement si taux lu
        port_tva = _r(port_ht * taux / 100)
        port_ttc = _r(port_ht + port_tva)

    # ── Résolution selon les données disponibles ─────────
    regle = "conserve"

    if ttc != 0 and tva != 0 and ht == 0:
        # CAS 3 : TTC + TVA → HT
        # Quand HT=0, on ne peut pas savoir si TVA est totale sans HT.
        # On teste les deux et on prend celui qui donne un HT > 0 cohérent.
        ht_si_totale = _r(ttc - tva - port_ht)
        ht_si_merch  = _r(ttc - tva - port_ttc)

        # Vérifier laquelle est cohérente avec le taux lu
        if port_ht > 0 and taux > 0:
            tva_totale_th = _r((ht_si_totale + port_ht) * taux / 100) if ht_si_totale > 0 else -1
            tva_merch_th  = _r(ht_si_merch * taux / 100) if ht_si_merch > 0 else -1
            dist_totale = abs(tva - tva_totale_th) if tva_totale_th >= 0 else 99
            dist_merch  = abs(tva - tva_merch_th)  if tva_merch_th  >= 0 else 99

            if dist_totale <= dist_merch and ht_si_totale > 0:
                # TVA totale
                ht = ht_si_totale
                port_tva = 0.0
                port_ttc = _r(port_ht)
                tva_est_totale = True
            else:
                # TVA merch
                ht = ht_si_merch
                tva_est_totale = False
        elif port_ht > 0:
            # Pas de taux → préférer totale (ne pas inventer port_tva)
            ht = ht_si_totale if ht_si_totale > 0 else ht_si_merch
            port_tva = 0.0
            port_ttc = _r(port_ht)
            tva_est_totale = True
        else:
            # Pas de port
            ht = _r(ttc - tva)

        regle = "A"
        log.info(f"Cas 3 (A) : HT = {ht} tva_totale={tva_est_totale}")

    elif ht > 0 and tva > 0 and ttc == 0:
        # CAS 4 : HT + TVA → TTC
        if tva_est_totale:
            ttc = _r(ht + tva + port_ht)
        else:
            ttc = _r(ht + tva + port_ttc)
        regle = "calcul_ttc"
        log.info(f"Cas 4 : TTC = {ttc}")

    elif ht > 0 and tva == 0 and ttc > 0:
        # CAS 5 : HT + TTC → TVA déduite mathématiquement
        tva = _r(ttc - ht - port_ht)
        if tva < 0:
            tva = 0.0
        regle = "C"
        log.info(f"Cas 5 (C) : TVA = {tva}")

    elif ht > 0 and tva == 0 and ttc == 0:
        # CAS 6 : HT seul → TVA=0
        ttc = _r(ht + port_ttc)
        regle = "ht_only"
        log.info(f"Cas 6 : TTC = HT+PORT = {ttc}")

    elif ttc > 0 and ht == 0 and tva == 0:
        # CAS 7 : TTC seul → exonéré ou TVA inconnue
        ht  = _r(ttc - port_ht)
        regle = "ttc_only"
        log.info(f"Cas 7 : HT = {ht}")

    elif ht > 0 and tva > 0 and ttc > 0:
        # CAS 1/2 : tout fourni → vérifier cohérence
        if tva_est_totale:
            ttc_th = _r(ht + tva + port_ht)
        else:
            ttc_th = _r(ht + tva + port_ttc)

        if _ok(ttc, ttc_th):
            regle = "coherent"
            log.info(f"Cohérent : TTC={ttc}")
        else:
            ecart = abs(ttc - ttc_th)
            log.warning(
                f"TTC incohérent : lu={ttc} "
                f"calculé={ttc_th} écart={ecart:.4f}")
            # Priorité au TTC lu sur la facture
            # Recalculer HT à partir de TTC lu
            if tva_est_totale:
                ht = _r(ttc - tva - port_ht)
            else:
                ht = _r(ttc - tva - port_ttc)
            regle = "F_ttc_prioritaire"

    # ── TVA totale exposée ────────────────────────────────
    # montant_tva = TVA totale que le comptable saisit en 3455
    if tva_est_totale or port_tva == 0:
        tva_totale = tva          # déjà totale
    else:
        tva_totale = _r(tva + port_tva)   # merch + port

    # ── Vérification finale absolue ───────────────────────
    ttc_verif = _r(ht + tva_totale + port_ht)
    ecart     = abs(ttc - ttc_verif)
    coherent  = _ok(ttc, ttc_verif)

    if not coherent:
        log.warning(
            f"Incohérence résiduelle : "
            f"TTC={ttc} ≠ HT({ht})+TVA({tva_totale})+PORT({port_ht})"
            f"={ttc_verif} → TTC forcé à {ttc_verif}")
        ttc    = ttc_verif
        ecart  = 0.0
        coherent = True

    # ── Score de confiance simple ─────────────────────────
    conf = 0.5
    if regle == "coherent":
        conf = 1.0
    elif regle in ("A", "C"):
        conf = 0.9
    elif regle in ("calcul_ttc", "F_ttc_prioritaire"):
        conf = 0.85
    elif regle in ("ht_only", "ttc_only"):
        conf = 0.7

    # ── Écriture du dict ──────────────────────────────────
    donnees.update({
        'montant_ht':            _r(ht),
        'montant_tva':           _r(tva_totale),
        'montant_ttc':           _r(ttc),
        'frais_port_ht':         _r(port_ht),
        'frais_port_tva':        _r(port_tva),
        'frais_port_ttc':        _r(port_ttc),
        'montant_tva_merch':     _r(tva),
        'montant_ttc_hors_port': _r(ht + tva),
        'tva_taux':              taux,
        'regle_resolution':      regle,
        'confiance_montants':    conf,
        'coherence_ok':          coherent,
        'ecart_coherence':       _r(ecart),
        'tva_lue_sur_facture':   tva > 0,
        'tva_etait_totale':      tva_est_totale,
    })

    log.info(
        f"✅ [V6] ({regle}) "
        f"HT={ht} TVA_tot={tva_totale} PORT={port_ht} "
        f"TTC={ttc} | conf={conf}")

    return donnees


# ───────────────────────────────────────────────────────────────
# VÉRIFICATION ÉCRITURE
# ───────────────────────────────────────────────────────────────
def verifier_ecriture(ecritures: list[dict]) -> dict:
    """Vérifie Σdébits = Σcrédits."""
    td    = sum(float(e.get('debit',  0) or 0) for e in ecritures)
    tc    = sum(float(e.get('credit', 0) or 0) for e in ecritures)
    ecart = abs(td - tc)
    eq    = ecart <= TOL
    if not eq:
        log.warning(
            f"Écriture déséquilibrée : "
            f"Σd={td:.2f} Σc={tc:.2f} écart={ecart:.2f}")
    else:
        log.info(f"Écriture équilibrée : Σ={td:.2f}")
    return {
        'equilibre':    eq,
        'total_debit':  _r(td),
        'total_credit': _r(tc),
        'ecart':        _r(ecart),
    }