# ocr_api.py — V5
# API FastAPI : analyse de factures + génération CPC
# V5 : support type_operation_force → pipeline IA avec sens forcé
from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
import tempfile, asyncio, os, sys, logging

sys.path.insert(0, os.path.dirname(__file__))

log = logging.getLogger("ocr_api")

from ocr_service import (
    analyser_facture_cli,
    analyser_plusieurs_factures,
)
from modules.cpc_generator import (
    generer_cpc,
    afficher_cpc,
    accumuler_depuis_donnees,
)

app = FastAPI(
    title="ComptaAssist OCR",
    description=(
        "Extraction intelligente de factures + PCG Maroc + CPC\n"
        "V5 : type_operation_force via pipeline IA"
    ),
    version="5.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

TYPES_OK = [
    'application/pdf',
    'image/jpeg', 'image/jpg',
    'image/png', 'image/bmp',
    'image/tiff', 'image/webp',
    'image/avif',
    'application/octet-stream',
]

EXT_OK = ['.pdf', '.jpg', '.jpeg',
          '.png', '.bmp', '.tiff', '.webp',
          '.avif']


# ─────────────────────────────────────────────────────────
# Health check
# ─────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {
        "status":  "ok",
        "service": "ComptaAssist OCR v5",
        "pipeline": [
            "LLM (Mistral Pixtral)",
            "montant_resolver V6",
            "fiscal_countries",
            "validator",
            "correction_sens vente/achat",
            "comptable V3 (PCG)",
            "cpc_generator V3",
        ],
    }


# ─────────────────────────────────────────────────────────
# Helper : sauvegarder le fichier uploadé en temp
# ─────────────────────────────────────────────────────────
async def _sauvegarder_tmp(fichier: UploadFile) -> str:
    ext = os.path.splitext(fichier.filename or 'fichier.pdf')[1].lower()
    type_ok = (fichier.content_type in TYPES_OK or ext in EXT_OK)
    if not type_ok:
        raise HTTPException(
            status_code=400,
            detail=f"Type non supporté : {fichier.content_type} / {ext}",
        )
    if not ext or ext not in EXT_OK:
        ext = '.pdf'
    tmp = tempfile.NamedTemporaryFile(suffix=ext, delete=False)
    tmp.write(await fichier.read())
    tmp.close()
    return tmp.name


# ─────────────────────────────────────────────────────────
# Helper : wrapper analyser_facture_cli avec sens forcé
# ─────────────────────────────────────────────────────────
def _analyser_avec_sens(chemin_tmp: str, type_operation_force: Optional[str]) -> dict:
    log.info(f"Pipeline lancé — type_operation_force={type_operation_force}")
    return analyser_facture_cli(chemin_tmp,
                                type_operation_force=type_operation_force)


# ─────────────────────────────────────────────────────────
# Analyser une facture
# ─────────────────────────────────────────────────────────
@app.post("/api/ocr/analyser-facture")
async def analyser(
        fichier: UploadFile = File(...),
        type_operation_force: Optional[str] = Form(None)):
    """
    Analyse une facture (PDF ou image).
    type_operation_force : si fourni, force le sens du pipeline
    (ex: ACHAT_MARCHANDISE, VENTE_SERVICE...)
    """
    tmp_path = await _sauvegarder_tmp(fichier)

    try:
        loop = asyncio.get_event_loop()
        donnees = await asyncio.wait_for(
            loop.run_in_executor(
                None,
                _analyser_avec_sens,
                tmp_path,
                type_operation_force,
            ),
            timeout=300.0,
        )

        if not donnees:
            raise HTTPException(500, "Pipeline retourné vide")
        if 'erreur' in donnees:
            raise HTTPException(500, donnees['erreur'])

        if type_operation_force:
            log.info(
                f"forcé={type_operation_force} "
                f"| op finale={donnees.get('type_operation')}"
            )

        # Générer le CPC
        if donnees.get('ecritures_comptables'):
            totaux = accumuler_depuis_donnees([donnees])
            cpc = generer_cpc(
                totaux_comptes=totaux,
                entreprise=donnees.get('fournisseur', ''),
                periode_debut=donnees.get('date_facture', ''),
                periode_fin=donnees.get('date_facture', ''),
            )
            donnees['cpc'] = cpc

        return donnees

    except asyncio.TimeoutError:
        raise HTTPException(408, "Timeout — traitement > 5 min")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(500, f"Erreur pipeline : {e}")
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


# ─────────────────────────────────────────────────────────
# Analyser plusieurs factures (lot) + CPC consolidé
# ─────────────────────────────────────────────────────────
@app.post("/api/ocr/analyser-lot")
async def analyser_lot(
        fichiers: list[UploadFile] = File(...)):
    """
    Analyse plusieurs factures et retourne le CPC consolidé.
    """
    chemins: list[str] = []
    try:
        for f in fichiers:
            ext = os.path.splitext(f.filename or 'f.pdf')[1].lower()
            if ext not in EXT_OK:
                ext = '.pdf'
            tmp = tempfile.NamedTemporaryFile(suffix=ext, delete=False)
            tmp.write(await f.read())
            tmp.close()
            chemins.append(tmp.name)

        loop     = asyncio.get_event_loop()
        resultat = await asyncio.wait_for(
            loop.run_in_executor(
                None, analyser_plusieurs_factures, chemins),
            timeout=600.0,
        )
        return resultat

    except asyncio.TimeoutError:
        raise HTTPException(408, "Timeout lot")
    except Exception as e:
        raise HTTPException(500, f"Erreur lot : {e}")
    finally:
        for c in chemins:
            if os.path.exists(c):
                os.unlink(c)


# ─────────────────────────────────────────────────────────
# Générer le CPC depuis des écritures existantes
# ─────────────────────────────────────────────────────────
class CpcRequest(BaseModel):
    ecritures:     list[dict]
    entreprise:    Optional[str] = ""
    periode_debut: Optional[str] = ""
    periode_fin:   Optional[str] = ""


@app.post("/api/cpc/generer")
def generer_cpc_endpoint(req: CpcRequest):
    try:
        totaux: dict[str, float] = {}
        for ligne in req.ecritures:
            compte = str(ligne.get('compte', '')).strip()
            debit  = float(ligne.get('debit',  0) or 0)
            credit = float(ligne.get('credit', 0) or 0)
            if compte.startswith('6') and debit > 0:
                totaux[compte] = totaux.get(compte, 0) + debit
            elif compte.startswith('7') and credit > 0:
                totaux[compte] = totaux.get(compte, 0) + credit

        return generer_cpc(
            totaux_comptes=totaux,
            entreprise=req.entreprise,
            periode_debut=req.periode_debut,
            periode_fin=req.periode_fin,
        )
    except Exception as e:
        raise HTTPException(500, f"Erreur CPC : {e}")


@app.post("/api/cpc/afficher")
def afficher_cpc_endpoint(req: CpcRequest):
    try:
        totaux: dict[str, float] = {}
        for ligne in req.ecritures:
            compte = str(ligne.get('compte', '')).strip()
            debit  = float(ligne.get('debit',  0) or 0)
            credit = float(ligne.get('credit', 0) or 0)
            if compte.startswith('6') and debit > 0:
                totaux[compte] = totaux.get(compte, 0) + debit
            elif compte.startswith('7') and credit > 0:
                totaux[compte] = totaux.get(compte, 0) + credit

        cpc   = generer_cpc(totaux, req.entreprise,
                            req.periode_debut, req.periode_fin)
        texte = afficher_cpc(cpc)
        return {"cpc_texte": texte, "resultats": cpc['resultats']}

    except Exception as e:
        raise HTTPException(500, f"Erreur affichage CPC : {e}")