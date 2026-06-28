 # rag_api.py — FastAPI RAG fiscal
from fastapi import FastAPI, HTTPException, Request, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from minio import Minio
from typing import Optional
import os, sys, tempfile, re, logging

sys.path.append(os.path.dirname(os.path.dirname(__file__)))
from rag_fiscal import (
    repondre,
    repondre_avec_donnees_client,
    indexer_pdf,
    collection,
    supprimer_document
)

log = logging.getLogger("rag_api")

app = FastAPI(title="ComptaAssist RAG Fiscal", version="3.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

minio_client = Minio(
    "localhost:9000",
    access_key="minioadmin",
    secret_key="minioadmin",
    secure=False
)


# ─────────────────────────────────────────────────────────
# Models Pydantic
# ─────────────────────────────────────────────────────────

class IndexerRequest(BaseModel):
    chemin_pdf:   str
    nom_document: str


class QuestionClientRequest(BaseModel):
    question:       str
    donnees_client: dict


# ─────────────────────────────────────────────────────────
# Helper MinIO
# ─────────────────────────────────────────────────────────

def obtenir_chemin_reel(chemin_pdf: str) -> tuple[str, bool]:
    if os.path.exists(chemin_pdf):
        return chemin_pdf, False
    try:
        extension = os.path.splitext(chemin_pdf)[1] or '.pdf'
        tmp = tempfile.NamedTemporaryFile(suffix=extension, delete=False)
        tmp.close()
        minio_client.fget_object("comptaassist", chemin_pdf, tmp.name)
        log.info(f"Fichier téléchargé depuis MinIO : {chemin_pdf}")
        return tmp.name, True
    except Exception as e:
        raise HTTPException(
            status_code=404,
            detail=f"Fichier introuvable : {e}"
        )


# ─────────────────────────────────────────────────────────
# Health check
# ─────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "service": "ComptaAssist RAG Fiscal v3"}


# ─────────────────────────────────────────────────────────
# Upload direct depuis Angular (admin)
# ─────────────────────────────────────────────────────────

@app.post("/api/upload-indexer")
async def upload_et_indexer(
        fichier: UploadFile = File(...),
        nom_document: str = Form(...),
        forcer: bool = Form(False)):
    """
    L'admin uploade un PDF directement.
    nom_document = nom logique ex: CGI_2024 ou CGNC
    forcer = True → supprime l'ancien et réindexe
    """
    ext = os.path.splitext(fichier.filename or "")[1].lower()
    if ext not in ['.pdf']:
        raise HTTPException(400, "Seuls les PDFs sont acceptés")

    if forcer:
        try:
            supprimer_document(nom_document)
            log.info(f"Document {nom_document} supprimé avant réindexation")
        except Exception as e:
            log.warning(f"Suppression avant réindexation : {e}")

    tmp = tempfile.NamedTemporaryFile(suffix='.pdf', delete=False)
    try:
        tmp.write(await fichier.read())
        tmp.close()

        resultat = indexer_pdf(tmp.name, nom_document)

        msg = resultat.get("message", "")
        m = re.search(r"(\d+)\s+morceaux", msg)
        if m:
            resultat["nb_morceaux"] = int(m.group(1))

        log.info(f"Indexation terminée : {resultat}")
        return resultat

    except Exception as e:
        log.error(f"Erreur indexation : {e}")
        raise HTTPException(500, f"Erreur indexation : {str(e)}")
    finally:
        if os.path.exists(tmp.name):
            os.unlink(tmp.name)


# ─────────────────────────────────────────────────────────
# Indexer depuis chemin local ou MinIO
# ─────────────────────────────────────────────────────────

@app.post("/api/indexer")
def indexer_document(request: IndexerRequest):
    chemin_reel, est_temp = obtenir_chemin_reel(request.chemin_pdf)
    try:
        resultat = indexer_pdf(chemin_reel, request.nom_document)
        msg = resultat.get("message", "")
        m = re.search(r"(\d+)\s+morceaux", msg)
        if m:
            resultat["nb_morceaux"] = int(m.group(1))
        return resultat
    except Exception as e:
        raise HTTPException(500, f"Erreur indexation : {str(e)}")
    finally:
        if est_temp and os.path.exists(chemin_reel):
            os.unlink(chemin_reel)


# ─────────────────────────────────────────────────────────
# Question sur les lois fiscales (ChromaDB uniquement)
# ─────────────────────────────────────────────────────────

@app.post("/api/chat")
async def chat_fiscal(request: Request):
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(400, "Body JSON invalide")

    question = (
        body.get("question") or
        body.get("Question") or
        body.get("QUESTION") or ""
    )

    if not str(question).strip():
        raise HTTPException(400, "La question ne peut pas être vide")

    try:
        return repondre(str(question).strip())
    except Exception as e:
        raise HTTPException(500, f"Erreur RAG : {str(e)}")


# ─────────────────────────────────────────────────────────
# Question sur un client spécifique
# (données PostgreSQL + lois fiscales ChromaDB)
# ─────────────────────────────────────────────────────────

@app.post("/api/chat-client")
async def chat_client(request: QuestionClientRequest):
    """
    Répond à une question sur un client spécifique.
    Combine les données réelles du client (envoyées par Spring)
    avec les lois fiscales indexées dans ChromaDB.
    """
    if not request.question.strip():
        raise HTTPException(400, "Question vide")
    try:
        return repondre_avec_donnees_client(
            request.question,
            request.donnees_client
        )
    except Exception as e:
        log.error(f"Erreur chat-client : {e}")
        raise HTTPException(500, f"Erreur : {str(e)}")


# ─────────────────────────────────────────────────────────
# Supprimer un document de ChromaDB
# ─────────────────────────────────────────────────────────

@app.delete("/api/supprimer/{nom_document}")
def supprimer(nom_document: str):
    """Supprime tous les blocs d'un document de ChromaDB"""
    try:
        return supprimer_document(nom_document)
    except Exception as e:
        raise HTTPException(500, f"Erreur suppression : {str(e)}")


# ─────────────────────────────────────────────────────────
# Statut ChromaDB
# ─────────────────────────────────────────────────────────

@app.get("/api/statut")
def statut_rag():
    try:
        nb = collection.count()
        return {
            "statut":            "ok" if nb > 0 else "vide",
            "morceaux_chromadb": nb,
            "message":           "Base indexée et prête" if nb > 0 else "Base vide"
        }
    except Exception as e:
        raise HTTPException(500, f"Erreur ChromaDB : {str(e)}")


# ─────────────────────────────────────────────────────────
# Sources indexées
# ─────────────────────────────────────────────────────────

@app.get("/api/sources")
def lister_sources():
    try:
        tous = collection.get()
        sources_count = {}
        for m in tous["metadatas"]:
            if m and "source" in m:
                s = m["source"]
                sources_count[s] = sources_count.get(s, 0) + 1

        return {
            "sources": [
                {"nom": nom, "morceaux": count}
                for nom, count in sources_count.items()
            ],
            "total_morceaux": collection.count()
        }
    except Exception as e:
        raise HTTPException(500, str(e))