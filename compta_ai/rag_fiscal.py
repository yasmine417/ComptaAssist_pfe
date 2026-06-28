"""
rag_fiscal.py — RAG universel hybride
Blocs avec chevauchement (fonctionne pour TOUT PDF)
+ indexation par article quand détecté (améliore la précision)
"""
import os
import re
import pdfplumber
import chromadb
from groq import Groq
from sentence_transformers import SentenceTransformer
from dotenv import load_dotenv

load_dotenv()

groq_client = Groq(api_key=os.getenv("GROQ_API_KEY"))
MODELE      = "llama-3.1-8b-instant"

print("Chargement modele embeddings...")
embeddings_model = SentenceTransformer(
    "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
)

chroma_client = chromadb.PersistentClient(path="./chromadb_data")
collection    = chroma_client.get_or_create_collection("documents")


# ════════════════════════════════════════════════════════════════════
# INDEXATION HYBRIDE
# ════════════════════════════════════════════════════════════════════
def indexer_pdf(chemin_pdf: str, nom_document: str) -> dict:
    existants = collection.get(where={"source": nom_document})
    if existants and len(existants["ids"]) > 0:
        msg = f"{nom_document} deja indexe ({len(existants['ids'])} blocs)."
        print(msg)
        return {"statut": "deja_indexe", "message": msg}

    print(f"Lecture de {chemin_pdf}...")
    texte = ""
    with pdfplumber.open(chemin_pdf) as pdf:
        total = len(pdf.pages)
        print(f"  {total} pages.")
        for i, page in enumerate(pdf.pages):
            t = page.extract_text()
            if t and t.strip():
                texte += t.strip() + "\n"
            if (i + 1) % 50 == 0:
                print(f"  {i+1}/{total} pages lues...")

    if not texte.strip():
        return {"statut": "erreur", "message": "PDF illisible"}

    blocs, ids, metas = [], [], []

    # ── Étape 1 : blocs génériques avec chevauchement ────────────
    # Fonctionne pour TOUT type de PDF
    # Blocs de 150 mots, chevauchement 75 mots
    mots      = texte.split()
    TAILLE    = 150
    CHEVAUCHE = 75
    n = 0
    i = 0
    while i < len(mots):
        bloc = " ".join(mots[i:i+TAILLE])
        bid  = f"{nom_document}_b{n}"
        blocs.append(bloc)
        ids.append(bid)
        metas.append({"source": nom_document, "type": "bloc", "ref": str(n)})
        i += TAILLE - CHEVAUCHE
        n += 1
    print(f"  {n} blocs generiques crees.")

    # ── Étape 2 : si articles détectés, indexer aussi par article ─
    # Améliore la précision pour "que contient l'article X"
    pattern_art = re.compile(
        r'(?:^|\n)\s*(?:Article|ARTICLE|Art\.)\s+'
        r'(\d+(?:\s*(?:bis|ter|quater|premier|BIS|TER|QUATER|PREMIER))?)'
        r'(?:\s*[\.\-–]\-?|\s+(?=[A-ZÀÂÉÈÊËÏÎÔÙÛÜ]))',
        re.MULTILINE
    )
    matches = list(pattern_art.finditer(texte))

    if len(matches) >= 3:
        print(f"  {len(matches)} articles detects. Indexation par article aussi...")
        compteur = {}
        for idx, match in enumerate(matches):
            numero = match.group(1).strip()
            debut  = match.start()
            fin    = matches[idx+1].start() if idx+1 < len(matches) else len(texte)
            contenu = texte[debut:fin].strip()

            # Tronquer à 1000 chars
            if len(contenu) > 1000:
                contenu = contenu[:1000] + "\n[...]"

            compteur[numero] = compteur.get(numero, 0) + 1
            occ = compteur[numero]

            art_id = f"{nom_document}_art_{numero.replace(' ','_')}_occ{occ}"

            # Ajouter seulement si pas déjà présent (IDs uniques)
            if art_id not in ids:
                blocs.append(contenu)
                ids.append(art_id)
                metas.append({
                    "source" : nom_document,
                    "type"   : "article",
                    "ref"    : numero
                })

    # Vectorisation
    print(f"  {len(blocs)} morceaux total. Vectorisation...")
    for j in range(0, len(blocs), 100):
        lot  = blocs[j:j+100]
        meta = metas[j:j+100]
        collection.add(
            documents=lot,
            embeddings=embeddings_model.encode(lot).tolist(),
            ids=ids[j:j+100],
            metadatas=meta
        )
        print(f"  {min(j+100, len(blocs))}/{len(blocs)} indexes...")

    msg = f"OK -- {len(blocs)} morceaux indexes pour {nom_document}"
    print(msg)
    return {"statut": "ok", "message": msg}


# ════════════════════════════════════════════════════════════════════
# RECHERCHE PURE — aucun cas spécial
# ════════════════════════════════════════════════════════════════════
def chercher(question: str, nb: int = 5) -> list:
    passages = {}

    # ── Recherche directe par numéro d'article ────────────────────
    # Si la question dit "article 4" → on va chercher directement
    # l'entrée article_4 dans ChromaDB sans passer par les vecteurs
    nums = re.findall(r'article\s+(\d+(?:\s*(?:bis|ter|premier))?)', question.lower())
    for num in nums:
        try:
            res = collection.get(
    where={"$and": [{"ref": {"$eq": num.strip()}}, {"type": {"$eq": "article"}}]},
    limit=5
)
            
            if res and res["ids"]:
                # Prendre la première occurrence (version originale)
                passages[res["ids"][0]] = {
                    "texte" : res["documents"][0],
                    "source": res["metadatas"][0]["source"],
                    "type"  : "article",
                    "ref"   : num.strip(),
                    "id"    : res["ids"][0]
                }
                print(f"  Article {num} trouvé directement.")
        except Exception as e:
            print(f"  Erreur recherche directe : {e}")

    # ── Recherche vectorielle sémantique ──────────────────────────
    # Complète toujours — fonctionne pour tout type de question
    vecteur   = embeddings_model.encode([question]).tolist()
    resultats = collection.query(query_embeddings=vecteur, n_results=nb)
    for doc, meta, rid in zip(
        resultats["documents"][0],
        resultats["metadatas"][0],
        resultats["ids"][0]
    ):
        if rid not in passages:
            passages[rid] = {
                "texte" : doc,
                "source": meta["source"],
                "type"  : meta.get("type", ""),
                "ref"   : meta.get("ref", ""),
                "id"    : rid
            }

    # Les articles directs en tête, puis les blocs par pertinence
    def score(p):
        if p["type"] == "article" and p["ref"] in [n.strip() for n in nums]:
            return 999
        mots = [m.lower() for m in question.split() if len(m) > 3]
        return sum(1 for m in mots if m in p["texte"].lower())

    trie = sorted(passages.values(), key=score, reverse=True)
    return trie[:4]


# ════════════════════════════════════════════════════════════════════
# RÉPONSE
# ════════════════════════════════════════════════════════════════════
def repondre(question: str) -> dict:
    if collection.count() == 0:
        return {"reponse": "Base vide. Lance : python rag_fiscal.py indexer",
                "sources": []}

    blocs = chercher(question)

    if not blocs:
        return {"reponse": "Aucun passage pertinent trouve.", "sources": []}

    # Contexte limité à 2500 chars
    contexte    = ""
    
    total_chars = 0
    for i, b in enumerate(blocs, 1):
        texte = b["texte"]
        dispo = 5000 - total_chars
        if dispo <= 0:
            break
        if len(texte) > dispo:
            texte = texte[:dispo]
        contexte    += f"--- Extrait {i} ---\n{texte}\n\n"
        total_chars += len(texte)

    sources = list(set(b["source"] for b in blocs))

    prompt = f"""Tu es un assistant expert qui répond aux questions basées sur des documents.

EXTRAITS DES DOCUMENTS :
{contexte}

QUESTION : {question}

INSTRUCTIONS STRICTES :
- Réponds UNIQUEMENT avec les informations présentes dans les extraits
- Reproduis exactement les montants, taux, chiffres et textes cités
- Si la réponse n'est pas dans les extraits : "Cette information ne figure pas dans les documents"
- N'invente rien

**Réponse directe :**
[La réponse avec les informations exactes]

**Texte exact du document :**
[Citation mot pour mot]

**Explication :**
[Basée uniquement sur le texte]

**Points importants :**
[Conditions ou exceptions du texte]"""

    reponse = groq_client.chat.completions.create(
        model=MODELE,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.0,
        max_tokens=800
    )

    return {
        "reponse"    : reponse.choices[0].message.content.strip(),
        "sources"    : sources,
        "nb_extraits": len(blocs),
        "extraits"   : [b["texte"][:150] + "..." for b in blocs]
    }



def supprimer_document(nom_document: str) -> dict:
    """Supprime tous les blocs d'un document de ChromaDB."""
    try:
        existants = collection.get(where={"source": nom_document})
        if not existants or not existants["ids"]:
            return {"statut": "introuvable",
                    "message": f"{nom_document} non trouvé dans la base"}
        ids_a_supprimer = existants["ids"]
        collection.delete(ids=ids_a_supprimer)
        msg = f"{len(ids_a_supprimer)} blocs supprimés pour {nom_document}"
        print(msg)
        return {"statut": "ok", "message": msg}
    except Exception as e:
        raise RuntimeError(f"Erreur suppression : {e}")




def repondre_avec_donnees_client(question: str, donnees_client: dict) -> dict:
    """
    Répond à une question en combinant :
    - Les données réelles du client (depuis Spring/PostgreSQL)
    - Les extraits des lois fiscales (ChromaDB)
    """
    # Chercher aussi dans les lois fiscales
    blocs_lois = []
    try:
        blocs_lois = chercher(question, nb=3)
    except Exception:
        pass

    # Construire le contexte avec données client
    contexte_client = f"""
DONNÉES RÉELLES DU CLIENT :
- Nom : {donnees_client.get('nomEntreprise', 'N/A')}
- ICE : {donnees_client.get('ice', 'N/A')}
- Chiffre d'affaires HT : {donnees_client.get('chiffreAffaires', 0)} MAD
- Total dépenses HT : {donnees_client.get('totalDepenses', 0)} MAD
- Résultat net : {donnees_client.get('benefice', 0)} MAD
- TVA collectée : {donnees_client.get('tvaCollectee', 0)} MAD
- TVA récupérable : {donnees_client.get('tvaRecuperable', 0)} MAD
- TVA à payer : {donnees_client.get('tvaAPayer', 0)} MAD
- Factures émises : {donnees_client.get('nbEmises', 0)}
- Factures payées : {donnees_client.get('nbPayees', 0)}
- Factures impayées : {donnees_client.get('nbImpayes', 0)}
- Reste à payer : {donnees_client.get('resteAPayer', 0)} MAD
- Solde bancaire : {donnees_client.get('soldeBanque', 0)} MAD
- Période : {donnees_client.get('periode', 'N/A')}
"""

    # Ajouter extraits lois fiscales si pertinents
    contexte_lois = ""
    sources = []
    if blocs_lois:
        for i, b in enumerate(blocs_lois, 1):
            contexte_lois += f"\n--- Loi fiscale extrait {i} ---\n{b['texte'][:500]}\n"
        sources = list(set(b["source"] for b in blocs_lois))

    prompt = f"""Tu es un expert-comptable marocain certifié.
Tu analyses les données financières réelles d'un client et tu réponds à la question posée.

{contexte_client}
{f"RÉFÉRENCES LÉGALES :{contexte_lois}" if contexte_lois else ""}

QUESTION : {question}

INSTRUCTIONS :
- Utilise les données réelles du client pour répondre avec précision
- Si tu cites une loi ou un taux, appuie-toi sur les références légales
- Donne une réponse claire, professionnelle et actionnable
- Si la question porte sur un calcul, montre les étapes
- Réponds en français

Réponse :"""

    reponse = groq_client.chat.completions.create(
        model="llama-3.1-8b-instant",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.1,
        max_tokens=1000
    )

    return {
        "reponse"    : reponse.choices[0].message.content.strip(),
        "sources"    : sources,
        "nb_extraits": len(blocs_lois),
        "type"       : "client"
    }




# ════════════════════════════════════════════════════════════════════
# POINT D'ENTRÉE
# ════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "indexer":
        for nom, fichier in [
            ("Code_General_Impots_2024",    "CGI_2024.pdf"),
            ("Code_Normalisation_Comptable","CGNC.pdf"),
        ]:
            if os.path.exists(fichier):
                indexer_pdf(fichier, nom)
            else:
                print(f"{fichier} introuvable.")

    elif len(sys.argv) > 1 and sys.argv[1] == "indexer-fichier":
        if len(sys.argv) >= 4:
            fichier, nom = sys.argv[2], sys.argv[3]
            if os.path.exists(fichier):
                indexer_pdf(fichier, nom)
            else:
                print(f"Fichier {fichier} introuvable.")
        else:
            print("Usage : python rag_fiscal.py indexer-fichier fichier.pdf Nom")

    elif len(sys.argv) > 1 and sys.argv[1] == "status":
        print(f"Morceaux ChromaDB : {collection.count()}")
        print(f"Modele Groq       : {MODELE}")
        cle = os.getenv("GROQ_API_KEY")
        print(f"Cle Groq          : {'OK' if cle else 'MANQUANTE'}")

    elif len(sys.argv) > 1 and sys.argv[1] == "debug":
        terme = " ".join(sys.argv[2:]) if len(sys.argv) > 2 else "article 4"
        print(f"Morceaux trouves pour : '{terme}'\n")
        for i, b in enumerate(chercher(terme, nb=5), 1):
            print(f"--- [{b['type']}] ref={b['ref']} source={b['source']} ---")
            print(b["texte"][:400])
            print()

    elif len(sys.argv) > 1 and sys.argv[1] == "question":
        if len(sys.argv) > 2:
            q = " ".join(sys.argv[2:])
            print(f"\nQuestion : {q}\n")
            r = repondre(q)
            print(f"REPONSE :\n{r['reponse']}")
            print(f"\nSources  : {r['sources']}")
            print(f"Extraits : {r['nb_extraits']}")
        else:
            print("Usage : python rag_fiscal.py question \"ta question\"")

    else:
        nb  = collection.count()
        cle = os.getenv("GROQ_API_KEY")
        print(f"Morceaux ChromaDB : {nb}")
        print(f"Modele            : {MODELE}")
        print(f"Cle Groq          : {'OK' if cle else 'MANQUANTE'}\n")

        if nb == 0:
            print("Lance : python rag_fiscal.py indexer")
            sys.exit()
        if not cle:
            print("Ajoute GROQ_API_KEY dans .env")
            sys.exit()

        questions = [
           " Selon l’article 6-I-A-10° du Code Général des Impôts 2024, les sociétés non résidentes bénéficient-elles d’une exonération de l’impôt sur les sociétés pour les plus-values réalisées sur les cessions de valeurs mobilières cotées à la bourse des valeurs du Maroc ? Quelles sont exactement les exclusions prévues ?"
            
            ]
        for q in questions:
            print(f"\n{'='*65}")
            print(f"QUESTION : {q}")
            print('='*65)
            r = repondre(q)
            print(f"\n{r['reponse']}")
            print(f"\nSources : {r['sources']} | Extraits : {r['nb_extraits']}")