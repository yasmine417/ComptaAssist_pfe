# modules/llm_extractor.py — V9 FINAL
# Logique correcte : on lit la facture et on détermine le sens
# depuis la structure de la facture elle-même, sans présupposés.
import os, json, re, base64
from mistralai import Mistral
from dotenv import load_dotenv
from modules.logger_config import get_logger

load_dotenv()
log = get_logger("llm_extractor")
client = Mistral(api_key=os.getenv("MISTRAL_API_KEY"))
MODELE = "pixtral-12b-2409"

PROMPT = """Tu es un expert-comptable marocain certifié (CGNC).
Tu analyses une facture et tu dois extraire ses données avec précision.

═══════════════════════════════════════════════════════════════
ÉTAPE 1 — LIRE LA FACTURE ET IDENTIFIER LES DEUX PARTIES
═══════════════════════════════════════════════════════════════

Sur toute facture il y a TOUJOURS deux parties :

  ÉMETTEUR = celui qui a rédigé et signé la facture
  ──────────────────────────────────────────────────
  → Son nom/logo est en HAUT de la page (entête)
  → Il a souvent son ICE, RC, IF, adresse, compte bancaire
  → Il demande un paiement
  → Met ce nom dans le champ "fournisseur"

  DESTINATAIRE = celui qui doit payer
  ────────────────────────────────────
  → Son nom est dans un encadré séparé, souvent à droite
    ou au milieu, précédé de : "Client :", "Facturé à :",
    "Bill to :", "À :", "Adressé à :", "Destinataire :"
  → Met ce nom dans le champ "client"

  ⚠️  Si tu ne vois PAS de section "Client" distincte,
  laisse le champ "client" vide — ne pas inventer.

═══════════════════════════════════════════════════════════════
ÉTAPE 2 — DÉTERMINER LE TYPE D'OPÉRATION
═══════════════════════════════════════════════════════════════

Regarde CE QUE L'ÉMETTEUR VEND ou FOURNIT :

  Si l'émetteur vend des SERVICES :
    (développement, formation, maintenance, consulting,
     audit, études, prestations informatiques, design,
     traduction, coaching, ingénierie, architecture...)
    → type_operation = VENTE_SERVICE

  Si l'émetteur vend des MARCHANDISES / PRODUITS PHYSIQUES :
    (matériel, équipement, ordinateurs, pièces, produits,
     articles, stocks, produits alimentaires...)
    → type_operation = VENTE_MARCHANDISE

  Si l'émetteur réalise des TRAVAUX :
    (construction, BTP, installation, rénovation...)
    → type_operation = VENTE_TRAVAUX

  Si l'émetteur fournit de l'ÉNERGIE ou de L'EAU :
    (électricité, eau, gaz)
    → type_operation = ACHAT_ENERGIE

  Si l'émetteur est un OPÉRATEUR TÉLÉCOM :
    (service téléphonique, internet, mobile, données)
    → type_operation = ACHAT_TELEPHONE

  Si l'émetteur est un PROPRIÉTAIRE / BAILLEUR :
    (loyer, location de local, de matériel)
    → type_operation = ACHAT_LOYER

  Si l'émetteur est une COMPAGNIE D'ASSURANCE :
    → type_operation = ACHAT_ASSURANCE

  Si l'émetteur est un TRANSPORTEUR / LIVREUR :
    → type_operation = ACHAT_TRANSPORT

  Si l'émetteur est un AVOCAT, NOTAIRE, EXPERT-COMPTABLE :
    → type_operation = ACHAT_HONORAIRES

  Si l'émetteur vend des MARCHANDISES que l'entreprise achète
  pour les REVENDRE ou les CONSOMMER :
    → type_operation = ACHAT_MARCHANDISE

  Si c'est un ACHAT de SERVICES généraux (non listé au-dessus) :
    → type_operation = ACHAT_SERVICE

  ─────────────────────────────────────────────
  RÉSUMÉ : VENTE si l'émetteur vend ses propres
  produits/services à un client.
  ACHAT si l'émetteur est un fournisseur externe
  (énergie, télécom, loyer, assurance, transport...).
  ─────────────────────────────────────────────

═══════════════════════════════════════════════════════════════
ÉTAPE 3 — LIRE LES MONTANTS SANS INVENTER
═══════════════════════════════════════════════════════════════

  Règle A — ht_lu > 0 ET tva_lu > 0 :
    montant_ht = ht_lu, montant_tva = tva_lu
    montant_ttc = ht_lu + tva_lu + port_ht_lu
    regle_appliquee = "A"

  Règle B — ht_lu > 0 ET ttc_lu > 0 ET tva_lu = 0 :
    montant_tva = ttc_lu - ht_lu, montant_ttc = ttc_lu
    regle_appliquee = "B"

  Règle C — ttc_lu > 0 ET tva_lu > 0 ET ht_lu = 0 :
    montant_ht = ttc_lu - tva_lu, montant_ttc = ttc_lu
    regle_appliquee = "C"

  Règle D — ttc_lu > 0 seulement (exonéré de TVA) :
    montant_ht = ttc_lu, montant_tva = 0
    regle_appliquee = "D"

  • tva_ligne dans items = TAUX % (ex: 20.0), jamais un montant
  • total_ligne_ht = quantité × prix unitaire HT
  • Montant absent = 0.0, Taux TVA absent = 0.0

═══════════════════════════════════════════════════════════════
ÉTAPE 4 — RETOURNER LE JSON (UNIQUEMENT, sans markdown)
═══════════════════════════════════════════════════════════════

{
  "type_facture_detecte": "FACTURE_B2B",
  "numero_facture": "",
  "date_facture": "JJ/MM/AAAA ou vide",
  "fournisseur": "nom de l'ÉMETTEUR (entête en haut de la facture)",
  "client": "nom du DESTINATAIRE (encadré client) ou vide si absent",
  "ice": "ICE 15 chiffres de l'ÉMETTEUR (entête) ou vide",
  "ice_client": "ICE 15 chiffres du DESTINATAIRE (encadré client) ou vide",
  "if_fournisseur": "identifiant fiscal ou vide",
  "rc": "registre de commerce ou vide",
  "siret": "SIRET français 14 chiffres ou vide",
  "devise": "MAD ou EUR",
  "pays": "MA ou FR",

  "montants_bruts": {
    "ttc_lu": 0.0, "ttc_libelle": "libellé exact lu ou ABSENT",
    "ht_lu":  0.0, "ht_libelle":  "libellé exact lu ou ABSENT",
    "tva_lu": 0.0, "tva_libelle": "libellé exact lu ou ABSENT",
    "taux_tva_lu": 0.0,
    "taux_source": "lu_direct ou exonere ou absent",
    "remise_lu": 0.0, "remise_libelle": "ABSENT",
    "port_ht_lu": 0.0, "port_libelle": "ABSENT"
  },

  "montant_ht": 0.0,
  "tva_taux": 0.0,
  "montant_tva": 0.0,
  "remise": 0.0,
  "frais_port_ht": 0.0,
  "autres_frais": 0.0,
  "montant_ttc": 0.0,
  "regle_appliquee": "A",
  "coherence_ok": true,
  "ecart_coherence": 0.0,

  "type_facture": "FACTURE",
  "type_operation": "VENTE_SERVICE",

  "items": [{
    "description": "désignation de la ligne",
    "quantite": 1.0,
    "prix_unitaire": 0.0,
    "tva_ligne": 0.0,
    "remise_ligne": 0.0,
    "total_ligne_ht": 0.0
  }],

  "mode_paiement": "",
  "echeance": "",
  "notes": ""
}"""


def _encoder_image(path: str) -> tuple[str, str]:
    ext  = os.path.splitext(path)[1].lower()
    mime = {'.jpg':'image/jpeg', '.jpeg':'image/jpeg',
            '.png':'image/png',  '.webp':'image/webp',
            '.bmp':'image/png'}.get(ext, 'image/png')
    with open(path, 'rb') as f:
        data = base64.b64encode(f.read()).decode('utf-8')
    return data, mime


def _f(val, default: float = 0.0) -> float:
    try:
        return round(float(
            str(val).replace(',', '.').replace(' ', '')
            .replace('\xa0', '').replace('\u202f', '')), 2)
    except Exception:
        return default


def _normaliser_tva_ligne(tva_val: float) -> float:
    """tva_ligne doit être un taux %, jamais un montant."""
    if tva_val <= 0:   return 0.0
    if tva_val > 100:
        log.warning(f"tva_ligne={tva_val} semble un montant → 0")
        return 0.0
    for t in [5.5, 7.0, 10.0, 14.0, 20.0, 21.0]:
        if abs(tva_val - t) < 0.6:
            return t
    return tva_val


def _normaliser_items(items: list) -> list:
    result = []
    for item in items:
        try:
            result.append({
                'description':    str(item.get('description', '')),
                'quantite':       _f(item.get('quantite',     1)),
                'prix_unitaire':  _f(item.get('prix_unitaire', 0)),
                'tva_ligne':      _normaliser_tva_ligne(
                                    _f(item.get('tva_ligne', 0))),
                'remise_ligne':   _f(item.get('remise_ligne',   0)),
                'total_ligne_ht': _f(item.get('total_ligne_ht', 0)),
            })
        except Exception:
            continue
    return result


def extraire_avec_llm(texte: str = "",
                      image_path: str = None) -> dict | None:
    try:
        log.info("Appel Mistral Pixtral V9...")

        if image_path and os.path.exists(image_path):
            img_b64, mime = _encoder_image(image_path)
            messages = [{"role": "user", "content": [
                {"type": "image_url",
                 "image_url": {
                     "url": f"data:{mime};base64,{img_b64}"}},
                {"type": "text", "text": PROMPT},
            ]}]
        else:
            messages = [{"role": "user",
                         "content": (f"{PROMPT}\n\n"
                                     f"TEXTE OCR :\n"
                                     f"{texte[:4000]}")}]

        resp = client.chat.complete(
            model=MODELE, messages=messages,
            temperature=0, max_tokens=1500)

        contenu = resp.choices[0].message.content
        log.info(f"LLM ({len(contenu)} chars) : {contenu[:400]}")

        contenu = re.sub(r'```json|```', '', contenu).strip()
        m = re.search(r'\{.*\}', contenu, re.DOTALL)
        if not m:
            log.error("Pas de JSON dans la réponse LLM")
            return None

        data = json.loads(m.group(0))

        # Normaliser montants
        for c in ['montant_ht', 'montant_tva', 'montant_ttc',
                  'remise', 'frais_port_ht', 'autres_frais',
                  'tva_taux']:
            data[c] = _f(data.get(c, 0))

        # Rétrocompatibilité
        if _f(data.get('frais_port', 0)) > 0 \
                and data['frais_port_ht'] == 0:
            data['frais_port_ht'] = _f(data['frais_port'])

        # Valeurs par défaut
        defaults = {
            'type_facture_detecte': 'FACTURE_B2B',
            'numero_facture':  '',
            'date_facture':    '',
            'fournisseur':     '',
            'client':          '',
            'ice':             '',
            'ice_client':      '',   # ICE du destinataire
            'if_fournisseur':  '',
            'rc':              '',
            'siret':           '',
            'devise':          'MAD',
            'pays':            'MA',
            'type_facture':    'FACTURE',
            'type_operation':  'VENTE_SERVICE',
            'mode_paiement':   '',
            'echeance':        '',
            'notes':           '',
            'items':           [],
            'regle_appliquee': '',
            'coherence_ok':    True,
            'ecart_coherence': 0.0,
            'montants_bruts':  {},
        }
        for k, v in defaults.items():
            data.setdefault(k, v)

        data['items'] = _normaliser_items(data.get('items', []))

        log.info(
            f"✅ LLM V9 : {data.get('type_facture_detecte')} "
            f"op={data.get('type_operation')} "
            f"fournisseur={data.get('fournisseur')!r} "
            f"client={data.get('client')!r} "
            f"ice_emetteur={data.get('ice')!r} "
            f"ice_client={data.get('ice_client')!r} "
            f"HT={data.get('montant_ht')} "
            f"TTC={data.get('montant_ttc')}")

        return data

    except json.JSONDecodeError as e:
        log.error(f"JSON invalide : {e}")
        return None
    except Exception as e:
        log.error(f"Erreur LLM : {e}")
        return None