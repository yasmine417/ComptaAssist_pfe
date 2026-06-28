# modules/fiscal_countries.py
import json
import os
import re
from modules.logger_config import get_logger

log = get_logger("fiscal")

_DATA = os.path.join(
    os.path.dirname(os.path.dirname(__file__)),
    'data', 'tva_countries.json')

with open(_DATA, 'r', encoding='utf-8') as f:
    PAYS_FISCAL = json.load(f)


def detecter_pays(texte: str) -> str:
    """
    Détecte le pays de la facture
    Retourne code pays : MA, FR, UE, US
    """
    t = texte.lower()

    # Champs fiscaux marocains
    if re.search(r'\bice\b|\bpatente\b', t):
        return 'MA'
    if re.search(r'dirham|mad\b|dh\b', t):
        return 'MA'

    # Champs fiscaux français
    if re.search(r'\bsiret\b|\bsiren\b', t):
        return 'FR'

    # TVA intracommunautaire UE
    if re.search(r'tva\s*intra|fr\s*\d{11}|'
                 r'be\d{10}|de\d{9}', t):
        return 'UE'

    # États-Unis
    if re.search(r'\bein\b|\btax\s*id\b|\busd\b|'
                 r'\$', t):
        return 'US'

    # Devise €
    if '€' in texte or 'eur' in t:
        return 'FR'

    return 'MA'  # Défaut Maroc


def get_config_pays(pays: str) -> dict:
    return PAYS_FISCAL.get(
        pays, PAYS_FISCAL['MA'])


def taux_valides(pays: str) -> list:
    return get_config_pays(pays).get('taux', [20])


def valider_taux(taux: float, pays: str) -> bool:
    valides = taux_valides(pays)
    return any(abs(taux - t) < 0.1 for t in valides)