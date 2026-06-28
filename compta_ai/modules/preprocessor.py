# modules/preprocessor.py
import cv2
import numpy as np
import tempfile
import os
from modules.logger_config import get_logger

log = get_logger("preprocessor")


def _redresser(image: np.ndarray) -> np.ndarray:
    try:
        coords = np.column_stack(
            np.where(image < 128))
        if len(coords) < 100:
            return image
        angle = cv2.minAreaRect(coords)[-1]
        if angle < -45:
            angle = -(90 + angle)
        else:
            angle = -angle
        if abs(angle) > 10:
            return image
        h, w = image.shape
        M = cv2.getRotationMatrix2D(
            (w // 2, h // 2), angle, 1.0)
        return cv2.warpAffine(
            image, M, (w, h),
            flags=cv2.INTER_CUBIC,
            borderMode=cv2.BORDER_REPLICATE)
    except Exception:
        return image


def pretraiter(image_path: str) -> str:
    img = cv2.imread(image_path)
    if img is None:
        log.warning(f"Image illisible : {image_path}")
        return image_path

    # ── Agrandir ──────────────────────────────────────
    h, w = img.shape[:2]
    if w < 2000:
        scale = 2000 / w
        img = cv2.resize(
            img, None,
            fx=scale, fy=scale,
            interpolation=cv2.INTER_CUBIC)

    # ── Niveaux de gris ───────────────────────────────
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # ── Débruitage fort pour photos ───────────────────
    denoised = cv2.fastNlMeansDenoising(
        gray, h=15,
        templateWindowSize=7,
        searchWindowSize=21)

    # ── Améliorer contraste ───────────────────────────
    clahe = cv2.createCLAHE(
        clipLimit=3.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(denoised)

    # ── Binarisation adaptative ───────────────────────
    # Meilleure que Otsu pour photos avec éclairage
    # non uniforme
    binary = cv2.adaptiveThreshold(
        enhanced, 255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY, 31, 10)

    # ── Nettoyer bruit résiduel ───────────────────────
    kernel = np.ones((1, 1), np.uint8)
    binary = cv2.morphologyEx(
        binary, cv2.MORPH_CLOSE, kernel)

    # ── Redresser ─────────────────────────────────────
    binary = _redresser(binary)

    tmp = tempfile.NamedTemporaryFile(
        suffix='_pre.png', delete=False)
    cv2.imwrite(tmp.name, binary)
    tmp.close()
    log.info(f"Prétraité → {tmp.name}")
    return tmp.name