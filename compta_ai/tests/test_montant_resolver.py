# tests/test_montant_resolver.py — V6
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from modules.montant_resolver import resoudre_montants, verifier_ecriture

TOL = 0.10
ok_n = 0
nb_n = 0

def chk(cas, label, ht_a=None, tva_a=None, ttc_a=None):
    global ok_n, nb_n
    nb_n += 1
    d  = resoudre_montants(cas.copy())
    ht = d['montant_ht'];  tva = d['montant_tva'];  ttc = d['montant_ttc']
    ph = d['frais_port_ht'];  ptva = d['frais_port_tva']
    rg = d['regle_resolution'];  ok_d = d['coherence_ok']

    # Formule stable : TTC = HT + TVA_totale + PORT_HT
    ttc_th = round(ht + tva + ph, 2)
    form   = abs(ttc - ttc_th) <= TOL and ok_d

    errs = []
    if ht_a  is not None and abs(ht  - ht_a)  > TOL: errs.append(f"HT {ht}≠{ht_a}")
    if tva_a is not None and abs(tva - tva_a) > TOL: errs.append(f"TVA {tva}≠{tva_a}")
    if ttc_a is not None and abs(ttc - ttc_a) > TOL: errs.append(f"TTC {ttc}≠{ttc_a}")

    ok = form and not errs
    if ok: ok_n += 1

    s = "✅" if ok else "❌"
    print(f"{s}  {label}")
    print(f"     HT={ht}  TVA={tva}  PORT={ph}(tva={ptva})  TTC={ttc}  [{rg}]")
    if not form: print(f"     ⚠ formule: {ht}+{tva}+{ph}={ttc_th} ≠ {ttc}")
    for e in errs: print(f"     ⚠ {e}")
    return ok


def run():
    print("="*65)
    print("  TESTS V6 — Déterministe, formule TTC=HT+TVA+PORT_HT")
    print("="*65)

    print("\n── Cas 1/2 : tout fourni ──────────────────────────────")
    # Facture réelle : TVA=totale (TTC=HT+TVA+PORT)
    chk({'montant_ht':2157.70,'montant_tva':431.54,'montant_ttc':2638.24,
         'tva_taux':20,'frais_port_ht':49},
        "Facture réelle : HT=2157.70 TVA=431.54(tot) PORT=49 TTC=2638.24",
        ht_a=2157.70, tva_a=431.54, ttc_a=2638.24)

    # TVA merch-only (TTC inclut TVA_port séparée)
    # HT=2000 TVA_merch=400 PORT=50 TVA_port=10 → TTC=2460
    chk({'montant_ht':2000,'montant_tva':400,'montant_ttc':2460,
         'tva_taux':20,'frais_port_ht':50},
        "TVA merch-only : HT=2000 TVA=400 PORT=50 → TVA_tot=410 TTC=2460",
        ht_a=2000, tva_a=410, ttc_a=2460)

    # Cohérent sans port
    chk({'montant_ht':1200,'montant_tva':240,'montant_ttc':1440},
        "Cohérent sans port : 1200+240=1440",
        ht_a=1200, tva_a=240, ttc_a=1440)

    # TTC incohérent → TTC recalculé depuis HT+TVA
    chk({'montant_ht':1000,'montant_tva':200,'montant_ttc':1250},
        "TTC incohérent 1250 → HT ajusté, TTC=1250 conservé",
        tva_a=200, ttc_a=1250)

    print("\n── Cas 3 : TTC + TVA → HT ────────────────────────────")
    # Télécom "Dont TVA 20%"
    chk({'montant_ht':0,'montant_tva':240,'montant_ttc':1440,'tva_taux':20},
        "A — Télécom TTC=1440 TVA=240 → HT=1200",
        ht_a=1200, tva_a=240, ttc_a=1440)

    # Avec port, TVA totale
    chk({'montant_ht':0,'montant_tva':441.34,'montant_ttc':2648.04,
         'tva_taux':20,'frais_port_ht':49},
        "A+PORT TVA totale : TTC=2648.04 TVA=441.34 PORT=49 → HT=2157.70",
        ht_a=2157.70, tva_a=441.34, ttc_a=2648.04)

    # Taux 7%
    chk({'montant_ht':0,'montant_tva':35,'montant_ttc':535,'tva_taux':7},
        "A — Eau TTC=535 TVA=35 → HT=500",
        ht_a=500, tva_a=35, ttc_a=535)

    # Avoir
    chk({'montant_ht':0,'montant_tva':-200,'montant_ttc':-1200},
        "A — Avoir TTC=-1200 TVA=-200 → HT=-1000",
        ht_a=-1000, tva_a=-200, ttc_a=-1200)

    print("\n── Cas 4 : HT + TVA → TTC ────────────────────────────")
    # HT + TVA_merch + PORT → TTC
    chk({'montant_ht':2157.70,'montant_tva':431.54,'montant_ttc':0,
         'tva_taux':20,'frais_port_ht':49},
        "HT=2157.70 TVA=431.54 PORT=49 taux=20% → TTC calculé",
        ht_a=2157.70, tva_a=441.34, ttc_a=2648.04)

    # Sans port
    chk({'montant_ht':5000,'montant_tva':1000,'montant_ttc':0},
        "HT=5000 TVA=1000 → TTC=6000",
        ht_a=5000, tva_a=1000, ttc_a=6000)

    print("\n── Cas 5 : HT + TTC → TVA déduite ───────────────────")
    chk({'montant_ht':3500,'montant_tva':0,'montant_ttc':4200},
        "C — HT=3500 TTC=4200 → TVA=700",
        ht_a=3500, tva_a=700, ttc_a=4200)

    chk({'montant_ht':1000,'montant_tva':0,'montant_ttc':1000},
        "C — HT=TTC=1000 → TVA=0 (exonéré)",
        ht_a=1000, tva_a=0, ttc_a=1000)

    # Avec port
    chk({'montant_ht':1000,'montant_tva':0,'montant_ttc':1272,
         'frais_port_ht':60},
        "C+PORT — HT=1000 TTC=1272 PORT=60 → TVA=212",
        ht_a=1000, tva_a=212, ttc_a=1272)

    print("\n── Cas 6 : HT seul (TVA=0) ────────────────────────────")
    chk({'montant_ht':5000,'montant_tva':0,'montant_ttc':0,'tva_taux':20},
        "B2B sans TVA : HT=5000 → TVA=0 TTC=5000",
        ht_a=5000, tva_a=0, ttc_a=5000)

    chk({'montant_ht':3000,'montant_tva':0,'montant_ttc':0,'tva_taux':0},
        "Intra-UE : HT=3000 taux=0 → TVA=0 TTC=3000",
        ht_a=3000, tva_a=0, ttc_a=3000)

    chk({'montant_ht':1500,'montant_tva':0,'montant_ttc':0,
         'tva_taux':0,'frais_port_ht':30},
        "HT=1500 PORT=30 sans TVA → TVA=0 TTC=1530",
        ht_a=1500, tva_a=0, ttc_a=1530)

    print("\n── Cas 7 : TTC seul ───────────────────────────────────")
    chk({'montant_ht':0,'montant_tva':0,'montant_ttc':800,'tva_taux':0},
        "TTC=800 seul → HT=800 TVA=0",
        ht_a=800, tva_a=0, ttc_a=800)

    print("\n── Écritures comptables ────────────────────────────────")
    global nb_n, ok_n

    # Facture réelle équilibrée
    nb_n += 1
    v = verifier_ecriture([
        {'compte':'6111','debit':2157.70,'credit':0},
        {'compte':'6147','debit':49.00,  'credit':0},
        {'compte':'3455','debit':431.54, 'credit':0},
        {'compte':'4411','debit':0,      'credit':2638.24},
    ])
    if v['equilibre']: ok_n += 1
    print(f"{'✅' if v['equilibre'] else '❌'}  Facture réelle équilibrée : {v}")

    # Sans TVA
    nb_n += 1
    v2 = verifier_ecriture([
        {'compte':'6131','debit':5000,'credit':0},
        {'compte':'4411','debit':0,'credit':5000},
    ])
    if v2['equilibre']: ok_n += 1
    print(f"{'✅' if v2['equilibre'] else '❌'}  B2B sans TVA : {v2}")

    # Déséquilibre détecté
    nb_n += 1
    v3 = verifier_ecriture([
        {'compte':'6145','debit':1000,'credit':0},
        {'compte':'4411','debit':0,'credit':1500},
    ])
    if not v3['equilibre']: ok_n += 1
    print(f"{'✅' if not v3['equilibre'] else '❌'}  Déséquilibre détecté écart={v3['ecart']}")

    print(f"\n{'═'*65}")
    print(f"  RÉSULTAT : {ok_n}/{nb_n} tests passés")
    if ok_n == nb_n:
        print("  ✅ V6 — Fiable et déterministe sur tous les cas")
    else:
        print(f"  ❌ {nb_n-ok_n} échec(s)")
    print(f"{'═'*65}")
    return ok_n == nb_n

if __name__ == '__main__':
    success = run()
    sys.exit(0 if success else 1)