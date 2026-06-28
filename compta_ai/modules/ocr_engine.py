# modules/ocr_engine.py
import pytesseract
from PIL import Image
import os
import hashlib
from modules.logger_config import get_logger

log = get_logger("ocr_engine")

pytesseract.pytesseract.tesseract_cmd = \
    r'C:\Users\yasmine\AppData\Local\Programs\Tesseract-OCR\tesseract.exe'

CACHE_DIR = os.path.join(
    os.path.dirname(os.path.dirname(__file__)),
    'cache')
os.makedirs(CACHE_DIR, exist_ok=True)


def _hash(path: str) -> str:
    h = hashlib.md5()
    with open(path, 'rb') as f:
        h.update(f.read(16384))
    return h.hexdigest()


def _cache_get(cle: str) -> str | None:
    p = os.path.join(CACHE_DIR, f"{cle}.txt")
    if os.path.exists(p):
        with open(p, 'r', encoding='utf-8') as f:
            contenu = f.read()
            # Cache valide seulement si texte lisible
            mots = [m for m in contenu.split()
                    if len(m) > 2
                    and m.isalpha()]
            if len(mots) > 10:
                log.info("Cache OCR utilisé")
                return contenu
            else:
                log.info(
                    "Cache invalide (bruit) "
                    "→ recalcul")
                os.unlink(p)
    return None


def _cache_set(cle: str, texte: str):
    p = os.path.join(CACHE_DIR, f"{cle}.txt")
    with open(p, 'w', encoding='utf-8') as f:
        f.write(texte)


def _qualite_texte(texte: str) -> float:
    """
    Mesure la qualité du texte extrait
    Retourne score 0-1
    """
    if not texte.strip():
        return 0.0

    mots  = texte.split()
    total = len(mots)
    if total == 0:
        return 0.0

    # Compter mots lisibles
    # (alphabétiques, longueur raisonnable)
    lisibles = sum(
        1 for m in mots
        if len(m) >= 2
        and sum(c.isalpha() for c in m)
        / len(m) >= 0.6
    )

    return lisibles / total


def _ocr_config(path: str,
                 config: str) -> tuple[str, float]:
    """Lance Tesseract avec une config donnée"""
    try:
        texte = pytesseract.image_to_string(
            Image.open(path),
            config=config
        )
        qualite = _qualite_texte(texte)
        return texte, qualite
    except Exception as e:
        log.error(f"Tesseract erreur ({config}): {e}")
        return "", 0.0


def extraire_texte(image_orig: str,
                    image_pre: str) -> dict:
    cle    = _hash(image_orig)
    cached = _cache_get(cle)
    if cached:
        return {
            "texte":     cached,
            "moteur":    "cache",
            "confiance": 1.0
        }

    # Essayer plusieurs configurations
    # et garder le meilleur résultat
    configs = [
        '--oem 1 --psm 6 -l fra+eng',
        '--oem 1 --psm 4 -l fra+eng',
        '--oem 1 --psm 3 -l fra+eng',
        '--oem 1 --psm 6 -l fra',
    ]

    meilleur_texte   = ""
    meilleure_qualite = 0.0

    for config in configs:
        texte, qualite = _ocr_config(
            image_pre, config)
        log.info(
            f"Config '{config}' → "
            f"qualité={qualite:.2f} "
            f"chars={len(texte)}")

        if qualite > meilleure_qualite:
            meilleure_qualite = qualite
            meilleur_texte    = texte

        # Assez bon → arrêter
        if qualite > 0.7:
            break

    log.info(
        f"Tesseract final : "
        f"{len(meilleur_texte)} chars "
        f"qualité={meilleure_qualite:.2f}")

    # Sauvegarder en cache seulement si lisible
    if meilleure_qualite > 0.3:
        _cache_set(cle, meilleur_texte)

    return {
        "texte":     meilleur_texte,
        "moteur":    "tesseract",
        "confiance": round(meilleure_qualite, 2)
    }