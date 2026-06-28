# ocr_service.py — V9
# Pipeline : LLM V9 → resolver → pays → validation → PCG → CPC
import os, sys, json, tempfile
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pdf2image import convert_from_path
from dotenv import load_dotenv

load_dotenv()
sys.path.insert(0, os.path.dirname(__file__))

from modules.logger_config    import get_logger
from modules.preprocessor     import pretraiter
from modules.validator        import valider
from modules.comptable        import appliquer_pcg
from modules.llm_extractor    import extraire_avec_llm
from modules.fiscal_countries import detecter_pays
from modules.montant_resolver import resoudre_montants
from modules.cpc_generator    import (
    accumuler_depuis_donnees,
    generer_cpc,
    afficher_cpc,
)

log = get_logger("ocr_service")

app = FastAPI(title="ComptaAssist OCR Service V9")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


def pdf_vers_images(chemin: str) -> list[str]:
    images = convert_from_path(chemin, dpi=200, fmt='png')
    chemins = []
    for i, img in enumerate(images):
        tmp = tempfile.NamedTemporaryFile(suffix=f'_p{i}.png', delete=False)
        img.save(tmp.name, 'PNG')
        chemins.append(tmp.name)
    return chemins


def _pipeline_facture(chemins_images: list[str],
                      type_operation_force: str = None) -> dict:
    # 1. LLM
    image_pre = pretraiter(chemins_images[0])
    try:
        donnees = extraire_avec_llm(texte="", image_path=image_pre)
    finally:
        if image_pre != chemins_images[0] and os.path.exists(image_pre):
            os.unlink(image_pre)

    if not donnees:
        return {'erreur': 'Extraction LLM échouée — vérifier MISTRAL_API_KEY'}

    # 2. Pages supplémentaires
    if len(chemins_images) > 1:
        tous_items = list(donnees.get('items', []))
        for chemin in chemins_images[1:]:
            pre = pretraiter(chemin)
            try:
                r = extraire_avec_llm(texte="", image_path=pre)
                if r and r.get('items'):
                    tous_items.extend(r['items'])
            finally:
                if pre != chemin and os.path.exists(pre):
                    os.unlink(pre)
        donnees['items'] = tous_items

    # 3. Si Spring a forcé un type_operation (correction par ICE),
    #    on l'applique maintenant AVANT le PCG pour que comptable.py
    #    génère les bons comptes et les bonnes écritures
    if type_operation_force:
        log.info(f"type_operation forcé par Spring : "
                 f"{donnees.get('type_operation')} → {type_operation_force}")
        donnees['type_operation'] = type_operation_force

    # 4. Résolution montants
    donnees = resoudre_montants(donnees)

    # 5. Pays
    if not donnees.get('pays'):
        donnees['pays'] = detecter_pays(str(donnees))

    # 6. Validation fiscale
    donnees = valider(donnees, donnees.get('pays', 'MA'))

    # 7. PCG — génère les écritures avec les bons comptes
    donnees = appliquer_pcg(donnees)

    log.info(
        f"✅ sens={donnees.get('sens_ecriture')} "
        f"op={donnees.get('type_operation')} "
        f"HT={donnees.get('montant_ht')} "
        f"TTC={donnees.get('montant_ttc')} "
        f"journal={donnees.get('journal')} "
        f"equilibre={donnees.get('ecriture_equilibree')}")

    return donnees


@app.post("/api/ocr/analyser-facture")
async def analyser_facture_endpoint(
        fichier: UploadFile = File(...),
        type_operation_force: str = Form(None)):
    ext = os.path.splitext(fichier.filename or "")[1].lower()
    if ext not in ['.pdf', '.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.webp']:
        raise HTTPException(400, detail=f"Format non supporté : {ext}")

    tmp = tempfile.NamedTemporaryFile(suffix=ext, delete=False)
    try:
        tmp.write(await fichier.read())
        tmp.flush()
        tmp.close()
        chemins_images: list[str] = []
        try:
            chemins_images = pdf_vers_images(tmp.name) if ext == '.pdf' \
                             else [tmp.name]
            result = _pipeline_facture(chemins_images,
                                       type_operation_force=type_operation_force)
            if 'erreur' in result:
                raise HTTPException(500, detail=result['erreur'])
            return result
        finally:
            for c in chemins_images:
                if c != tmp.name and os.path.exists(c):
                    os.unlink(c)
    finally:
        if os.path.exists(tmp.name):
            os.unlink(tmp.name)


@app.post("/api/ocr/analyser-lot")
async def analyser_lot_endpoint(fichiers: list[UploadFile] = File(...)):
    resultats, erreurs = [], []
    for fichier in fichiers:
        ext = os.path.splitext(fichier.filename or "")[1].lower()
        tmp = tempfile.NamedTemporaryFile(suffix=ext, delete=False)
        chemins: list[str] = []
        try:
            tmp.write(await fichier.read())
            tmp.flush()
            tmp.close()
            chemins = pdf_vers_images(tmp.name) if ext == '.pdf' else [tmp.name]
            r = _pipeline_facture(chemins)
            if 'erreur' in r:
                erreurs.append({'fichier': fichier.filename, **r})
            else:
                resultats.append(r)
        except Exception as e:
            erreurs.append({'fichier': fichier.filename, 'erreur': str(e)})
        finally:
            for c in chemins:
                if c != tmp.name and os.path.exists(c):
                    os.unlink(c)
            if os.path.exists(tmp.name):
                os.unlink(tmp.name)

    cpc = None
    if resultats:
        cpc = generer_cpc(totaux_comptes=accumuler_depuis_donnees(resultats))

    return {
        'factures': resultats, 'erreurs': erreurs, 'cpc': cpc,
        'resume': {'total': len(fichiers), 'succes': len(resultats),
                   'echecs': len(erreurs)},
    }


@app.get("/api/ocr/health")
def health():
    return {"status": "ok", "version": "9.0"}


def analyser_facture_cli(chemin_fichier: str,
                         type_operation_force: str = None) -> dict:
    # ← CORRECTION : accepte et passe type_operation_force au pipeline
    ext = os.path.splitext(chemin_fichier)[1].lower()
    chemins_images: list[str] = []
    try:
        if ext == '.pdf':
            chemins_images = pdf_vers_images(chemin_fichier)
        elif ext in ['.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.webp']:
            chemins_images = [chemin_fichier]
        else:
            return {'erreur': f'Format non supporté : {ext}'}
        return _pipeline_facture(chemins_images,
                                 type_operation_force=type_operation_force)
    finally:
        for c in chemins_images:
            if c != chemin_fichier and os.path.exists(c):
                os.unlink(c)


def analyser_plusieurs_factures(chemins: list[str]) -> dict:
    resultats, erreurs = [], []
    for chemin in chemins:
        try:
            r = analyser_facture_cli(chemin)
            if 'erreur' in r:
                erreurs.append({'fichier': chemin, **r})
            else:
                resultats.append(r)
        except Exception as e:
            erreurs.append({'fichier': chemin, 'erreur': str(e)})

    cpc = None
    if resultats:
        cpc = generer_cpc(totaux_comptes=accumuler_depuis_donnees(resultats))

    return {
        'factures': resultats, 'erreurs': erreurs, 'cpc': cpc,
        'resume': {'total': len(chemins), 'succes': len(resultats),
                   'echecs': len(erreurs)},
    }


if __name__ == '__main__':
    if len(sys.argv) > 1:
        f = sys.argv[1]
        if os.path.exists(f):
            print(json.dumps(analyser_facture_cli(f),
                             ensure_ascii=False, indent=2))
        else:
            print(f"Fichier introuvable : {f}")
    else:
        import uvicorn
        uvicorn.run("ocr_service:app", host="0.0.0.0", port=8002, reload=True)