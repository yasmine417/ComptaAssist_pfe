from fastapi import FastAPI, HTTPException
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel
from minio import Minio
import os
import io
import tempfile
from contextlib import redirect_stdout
import sys
from fastapi.middleware.cors import CORSMiddleware
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from analyser_bilan import (
    extraire_bilan_python,
    calculer_equilibre,
    interpreter_groq,
    afficher
)

app = FastAPI(title="ComptaAssist AI Engine", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
# ── Client MinIO ──────────────────────────────────────────────────
minio_client = Minio(
    "localhost:9000",
    access_key="minioadmin",
    secret_key="minioadmin",
    secure=False
)

class BilanRequest(BaseModel):
    chemin_pdf: str

# ── Endpoint santé ────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok", "service": "ComptaAssist AI Engine"}

# ── Télécharger PDF depuis MinIO ou chemin local ──────────────────
def obtenir_chemin_reel(chemin_pdf: str) -> tuple[str, bool]:
    """
    Retourne (chemin_reel, est_temporaire)
    Si chemin local → retourne tel quel
    Si chemin MinIO → télécharge dans un fichier temp
    """
    # Chemin local direct — pour les tests
    if os.path.exists(chemin_pdf):
        return chemin_pdf, False

    # Chemin MinIO — télécharger
    try:
        tmp = tempfile.NamedTemporaryFile(
            suffix=".pdf", delete=False)
        tmp.close()
        minio_client.fget_object(
            "comptaassist",
            chemin_pdf,
            tmp.name
        )
        return tmp.name, True
    except Exception as e:
        raise HTTPException(
            status_code=404,
            detail=f"PDF introuvable — chemin local ou MinIO : {e}"
        )

# ── Endpoint JSON pour Java ───────────────────────────────────────
@app.post("/analyser-bilan")
def analyser_bilan_json(request: BilanRequest):
    chemin_reel, est_temp = obtenir_chemin_reel(request.chemin_pdf)

    try:
        postes          = extraire_bilan_python(chemin_reel)
        equilibre       = calculer_equilibre(postes)
        interpretations = interpreter_groq(postes, equilibre)
    finally:
        if est_temp and os.path.exists(chemin_reel):
            os.unlink(chemin_reel)

    # Récupérer les interprétations des ratios
    ir = interpretations.get("interpretations_ratios", {})

    # Construire les ratios avec statut et texte inclus
    ratios = [
        {
            "nom":    "Liquidité générale",
            "valeur": equilibre["ratios"].get("liquidite_generale"),
            "norme":  "> 1",
            "statut": ir.get("liquidite_generale", {}).get("statut", "?"),
            "texte":  ir.get("liquidite_generale", {}).get("texte", "")
        },
        {
            "nom":    "Liquidité immédiate",
            "valeur": equilibre["ratios"].get("liquidite_immediate"),
            "norme":  "> 0.2",
            "statut": ir.get("liquidite_immediate", {}).get("statut", "?"),
            "texte":  ir.get("liquidite_immediate", {}).get("texte", "")
        },
        {
            "nom":    "Autonomie financière",
            "valeur": equilibre["ratios"].get("autonomie_financiere"),
            "norme":  "> 0.33",
            "statut": ir.get("autonomie_financiere", {}).get("statut", "?"),
            "texte":  ir.get("autonomie_financiere", {}).get("texte", "")
        },
        {
            "nom":    "Taux endettement",
            "valeur": equilibre["ratios"].get("taux_endettement"),
            "norme":  "< 1",
            "statut": ir.get("taux_endettement", {}).get("statut", "?"),
            "texte":  ir.get("taux_endettement", {}).get("texte", "")
        },
        {
            "nom":    "Couverture emplois stables",
            "valeur": equilibre["ratios"].get("couverture_emplois"),
            "norme":  "> 1",
            "statut": ir.get("couverture_emplois", {}).get("statut", "?"),
            "texte":  ir.get("couverture_emplois", {}).get("texte", "")
        },
        {
            "nom":    "Rentabilité commerciale",
            "valeur": equilibre["ratios"].get("rentabilite_commerciale"),
            "norme":  "> 0.05",
            "statut": ir.get("rentabilite_commerciale", {}).get("statut", "?"),
            "texte":  ir.get("rentabilite_commerciale", {}).get("texte", "")
        },
        {
            "nom":    "Rentabilité financière",
            "valeur": equilibre["ratios"].get("rentabilite_financiere"),
            "norme":  "> 0.10",
            "statut": ir.get("rentabilite_financiere", {}).get("statut", "?"),
            "texte":  ir.get("rentabilite_financiere", {}).get("texte", "")
        },
    ]

    return {
        # ── Masses du bilan ──────────────────────────────────────
        "postes": postes,

        # ── Équilibre financier ──────────────────────────────────
        "equilibre": {
            "FRF":         equilibre["FRF"],
            "BFG":         equilibre["BFG"],
            "TN_methode1": equilibre["TN1"],
            "TN_methode2": equilibre["TN2"],
            "coherence":   equilibre["coherence"]
        },

        # ── Interprétations FRF / BFG / TN ──────────────────────
        "interpretation_FRF": interpretations.get(
            "interpretation_FRF", ""),
        "interpretation_BFG": interpretations.get(
            "interpretation_BFG", ""),
        "interpretation_TN":  interpretations.get(
            "interpretation_TN", ""),
        "statut_TN": interpretations.get("statut_TN", "BON"),

        # ── Ratios avec statut et texte ──────────────────────────
        "ratios": ratios,

        # ── Anomalies ────────────────────────────────────────────
        "anomalies": interpretations.get("anomalies", []),

        # ── Points forts ─────────────────────────────────────────
        "points_forts": interpretations.get("points_forts", []),

        # ── Conclusion ───────────────────────────────────────────
        "conclusion": interpretations.get("conclusion", ""),
    }

# ── Endpoint TEXTE — même affichage que le terminal ───────────────
@app.post("/analyser-bilan/rapport", response_class=PlainTextResponse)
def analyser_bilan_rapport(request: BilanRequest):
    chemin_reel, est_temp = obtenir_chemin_reel(request.chemin_pdf)

    try:
        postes          = extraire_bilan_python(chemin_reel)
        equilibre       = calculer_equilibre(postes)
        interpretations = interpreter_groq(postes, equilibre)
    finally:
        if est_temp and os.path.exists(chemin_reel):
            os.unlink(chemin_reel)

    buffer = io.StringIO()
    with redirect_stdout(buffer):
        afficher(
            nom="Entreprise",
            exercice="2025",
            d=postes,
            eq=equilibre,
            interp=interpretations
        )

    return buffer.getvalue()