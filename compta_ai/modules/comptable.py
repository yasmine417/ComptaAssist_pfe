# modules/comptable.py — V5 FINAL
# Plan Comptable Marocain CGNC complet
# Tous les comptes classe 6 et 7 + immobilisations classe 2
from __future__ import annotations
import os
from modules.logger_config import get_logger
from modules.montant_resolver import resoudre_montants, verifier_ecriture

log = get_logger("comptable")

# ══════════════════════════════════════════════════════════════
# COMPTES FIXES
# ══════════════════════════════════════════════════════════════
C_CLIENT      = '3421'
C_FOURNISSEUR = '4411'
C_TVA_RECUP   = '3455'   # 34552 TVA récupérable sur charges
C_TVA_RECUP_IMM = '34551' # TVA récupérable sur immobilisations
C_TVA_COLL    = '4455'
C_BANQUE      = '5141'
C_CAISSE      = '5161'

LIBELLES = {
    '3421':  "Clients",
    '4411':  "Fournisseurs",
    '3455':  "État — TVA récupérable sur charges",
    '34551': "État — TVA récupérable sur immobilisations",
    '34552': "État — TVA récupérable sur charges",
    '4455':  "État — TVA facturée",
    '5141':  "Banques",
    '5161':  "Caisse",
    # Immobilisations incorporelles
    '2210':  "Immobilisation en recherche et développement",
    '2220':  "Brevets, marques, droits et valeurs similaires",
    '2230':  "Fonds commercial",
    '2285':  "Autres immobilisations incorporelles",
    # Immobilisations corporelles
    '2321':  "Bâtiments",
    '2331':  "Installations techniques",
    '2332':  "Matériel et outillage",
    '2340':  "Matériel de transport",
    '2351':  "Mobilier de bureau",
    '2352':  "Matériel de bureau",
    '2355':  "Matériel informatique",
    '2358':  "Autres mobilier et matériel",
}

def _est_vente(donnees: dict) -> bool:
    op = donnees.get('type_operation', '').upper()
    return (op.startswith('VENTE_')
            or op in ('PRESTATION_CLIENT', 'AVOIR_CLIENT'))

# ══════════════════════════════════════════════════════════════
# SEUIL IMMOBILISATION (MAD HT par unité)
# Configurable via .env : SEUIL_IMMOBILISATION=5000
# Par défaut 5000 MAD (pratique fiscale marocaine courante)
# En dessous → charge (classe 6)
# Au-dessus  → confirmation requise auprès du comptable
# ══════════════════════════════════════════════════════════════
SEUIL_IMMOBILISATION = float(os.getenv('SEUIL_IMMOBILISATION', '5000'))

def _est_immobilisation(donnees: dict) -> bool:
    """
    Détermine si l'achat doit être immobilisé (classe 2)
    ou passé en charge (classe 6).
    Critères : valeur unitaire > 5 000 MAD ET nature durable.
    """
    op = donnees.get('type_operation', '').upper()

    # Certains types sont toujours des charges
    TOUJOURS_CHARGE = [
        'ACHAT_ENERGIE', 'ACHAT_TELEPHONE', 'ACHAT_LOYER',
        'ACHAT_ASSURANCE', 'ACHAT_TRANSPORT', 'ACHAT_HONORAIRES',
        'ACHAT_FOURNITURE', 'ACHAT_SERVICE', 'ACHAT_PUBLICITE',
        'ACHAT_TRAVAUX',
    ]
    if op in TOUJOURS_CHARGE:
        return False

    # Vérifier le montant HT par unité
    ht = float(donnees.get('montant_ht', 0) or 0)
    items = donnees.get('items', [])
    if items:
        # Chercher le prix unitaire max parmi les items
        prix_max = max(
            (float(i.get('prix_unitaire', 0) or 0)
             for i in items), default=0)
        if prix_max > 0:
            return prix_max >= SEUIL_IMMOBILISATION

    # Fallback : montant HT total
    if ht >= SEUIL_IMMOBILISATION:
        # Vérifier que c'est bien du matériel durable
        texte = _texte_facture(donnees)
        MOTS_IMMO = [
            'ordinateur', 'laptop', 'écran', 'moniteur',
            'serveur', 'imprimante', 'scanner', 'photocopieur',
            'véhicule', 'voiture', 'camion', 'moto',
            'machine', 'équipement industriel', 'installation',
            'mobilier', 'bureau', 'chaise', 'armoire',
            'logiciel', 'licence perpétuelle',
            'bâtiment', 'terrain', 'construction',
            'matériel', 'outillage',
        ]
        return any(m in texte for m in MOTS_IMMO)

    return False


# ══════════════════════════════════════════════════════════════
# COMPTES D'IMMOBILISATION — CLASSE 2
# ══════════════════════════════════════════════════════════════
REGLES_IMMO = [
    # Matériel informatique
    {
        'compte': '2355',
        'libelle': "Matériel informatique",
        'ops': ['ACHAT_INFORMATIQUE', 'ACHAT_MARCHANDISE'],
        'mots': ['ordinateur', 'laptop', 'pc ', 'serveur',
                 'écran', 'moniteur', 'imprimante', 'scanner',
                 'tablette', 'clavier', 'souris', 'disque dur',
                 'ssd', 'ram', 'processeur', 'routeur', 'switch',
                 'nas', 'onduleur', 'photocopieur', 'dell', 'hp',
                 'lenovo', 'apple', 'mac', 'asus', 'acer'],
    },
    # Matériel de transport
    {
        'compte': '2340',
        'libelle': "Matériel de transport",
        'ops': ['ACHAT_VEHICULE'],
        'mots': ['véhicule', 'voiture', 'camion', 'camionnette',
                 'moto', 'scooter', 'auto', 'utilitaire',
                 'renault', 'peugeot', 'ford', 'toyota',
                 'mercedes', 'volkswagen', 'fiat', 'dacia'],
    },
    # Mobilier de bureau
    {
        'compte': '2351',
        'libelle': "Mobilier de bureau",
        'ops': [],
        'mots': ['bureau', 'chaise', 'fauteuil', 'armoire',
                 'étagère', 'table', 'meuble', 'canapé',
                 'bibliothèque', 'classeur meuble'],
    },
    # Matériel de bureau
    {
        'compte': '2352',
        'libelle': "Matériel de bureau",
        'ops': [],
        'mots': ['téléphone fixe', 'standard téléphonique',
                 'fax', 'machine à calculer', 'coffre-fort'],
    },
    # Installations techniques / matériel outillage
    {
        'compte': '2332',
        'libelle': "Matériel et outillage",
        'ops': [],
        'mots': ['machine', 'outillage', 'outil industriel',
                 'équipement industriel', 'presse', 'tour',
                 'fraiseuse', 'perceuse industrielle',
                 'compresseur', 'groupe électrogène'],
    },
    # Logiciels / licences perpétuelles
    {
        'compte': '2285',
        'libelle': "Autres immobilisations incorporelles",
        'ops': [],
        'mots': ['logiciel', 'licence perpétuelle', 'erp',
                 'crm', 'progiciel', 'application métier'],
    },
    # Fallback immobilisation
    {
        'compte': '2358',
        'libelle': "Autres mobilier, matériel de bureau et aménagements",
        'ops': [], 'mots': [],
    },
]


# ══════════════════════════════════════════════════════════════
# COMPTES DE PRODUIT — CLASSE 7
# ══════════════════════════════════════════════════════════════
REGLES_PRODUIT = [
    # 711 Ventes de marchandises
    {
        'compte': '7111',
        'libelle': "Ventes de marchandises au Maroc",
        'ops': ['VENTE_MARCHANDISE'],
        'mots': ['marchandise', 'article', 'revente', 'négoce',
                 'produit revendu'],
    },
    # 7124 Prestations de services au Maroc
    {
        'compte': '71243',
        'libelle': "Prestations de services",
        'ops': ['VENTE_SERVICE', 'PRESTATION_CLIENT'],
        'mots': ['prestation', 'service', 'développement',
                 'formation', 'maintenance', 'consulting',
                 'conseil', 'audit', 'informatique', 'web',
                 'mobile', 'logiciel', 'cloud', 'hébergement',
                 'support', 'intégration', 'migration',
                 'ingénierie', 'expertise'],
    },
    # 71241 Travaux
    {
        'compte': '71241',
        'libelle': "Travaux",
        'ops': ['VENTE_TRAVAUX'],
        'mots': ['travaux', 'construction', 'btp', 'génie civil',
                 'maçonnerie', 'plomberie', 'électricité bâtiment',
                 'peinture bâtiment', 'carrelage'],
    },
    # 71242 Études
    {
        'compte': '71242',
        'libelle': "Études",
        'ops': ['VENTE_ETUDE'],
        'mots': ['étude', 'étude technique', 'étude de marché',
                 'rapport', 'diagnostic', 'analyse'],
    },
    # 71276 Ports facturés
    {
        'compte': '71276',
        'libelle': "Ports et frais accessoires facturés",
        'ops': [],
        'mots': ['port facturé', 'frais de port',
                 'frais accessoires facturés'],
    },
    # 71271 Locations reçues (revenus locatifs)
    {
        'compte': '71271',
        'libelle': "Locations diverses reçues",
        'ops': ['VENTE_LOCATION'],
        'mots': ['loyer reçu', 'location facturée',
                 'sous-location', 'revenu locatif'],
    },
    # Fallback
    {
        'compte': '71243',
        'libelle': "Prestations de services",
        'ops': [], 'mots': [],
    },
]


# ══════════════════════════════════════════════════════════════
# COMPTES DE CHARGE — CLASSE 6 (CGNC COMPLET)
# ══════════════════════════════════════════════════════════════
REGLES_CHARGE = [
    # ── 611 Achats revendus de marchandises ──────────────────
    {
        'compte': '6111',
        'libelle': "Achats de marchandises (groupe A)",
        'ops': ['ACHAT_MARCHANDISE'],
        'mots': ['marchandise revendue', 'article revendu',
                 'négoce', 'produit de revente',
                 'stock marchandise'],
    },
    # ── 612 Matières et fournitures ──────────────────────────
    {
        'compte': '6121',
        'libelle': "Achats de matières premières",
        'ops': ['ACHAT_MATIERE_PREMIERE'],
        'mots': ['matière première', 'matière brute',
                 'acier', 'bois', 'tissu', 'cuir', 'plastique'],
    },
    {
        'compte': '61227',
        'libelle': "Achats de fournitures de bureau",
        'ops': ['ACHAT_FOURNITURE'],
        'mots': ['fourniture de bureau', 'papeterie',
                 'stylo', 'ramette', 'cartouche encre',
                 'toner', 'classeur', 'cahier'],
    },
    {
        'compte': '61253',
        'libelle': "Achats de petit outillage et petit équipement",
        'ops': [],
        'mots': ['petit outillage', 'petit équipement',
                 'outil', 'perceuse', 'visseuse', 'marteau',
                 'clé', 'tournevis', 'matériel <= 500'],
    },
    {
        'compte': '61254',
        'libelle': "Achats de fournitures de bureau (non stockées)",
        'ops': [],
        'mots': ['fourniture non stockée', 'consommable bureau'],
    },
    # ── 6125 Eau, électricité, gaz ───────────────────────────
    {
        'compte': '61251',
        'libelle': "Achats de fournitures non stockables (eau, électricité)",
        'ops': ['ACHAT_ENERGIE'],
        'mots': ['onee', 'lydec', 'redal', 'amendis', 'radeef',
                 'steg', 'sonede',
                 'électricité', 'electricite', 'énergie',
                 'eau potable', 'eau ', 'gaz naturel', 'gaz ',
                 'distribution eau', 'consommation énergie',
                 'quittance eau', 'facture électricité'],
    },
    # ── 6126 Travaux, études, prestations ────────────────────
    {
        'compte': '61261',
        'libelle': "Achats de travaux (sous-traitance)",
        'ops': ['ACHAT_TRAVAUX'],
        'mots': ['sous-traitance travaux', 'travaux sous-traités',
                 'construction', 'btp', 'maçonnerie',
                 'plomberie', 'électricité bâtiment'],
    },
    {
        'compte': '61262',
        'libelle': "Achats d'études",
        'ops': ['ACHAT_ETUDE'],
        'mots': ['étude sous-traitée', 'étude externe',
                 'expertise externe', 'rapport externe'],
    },
    {
        'compte': '61263',
        'libelle': "Achats de prestations de service",
        'ops': ['ACHAT_SERVICE', 'ACHAT_INFORMATIQUE'],
        'mots': ['prestation informatique', 'maintenance logiciel',
                 'infogérance', 'hosting', 'hébergement web',
                 'cloud', 'saas', 'abonnement logiciel',
                 'développement logiciel', 'support technique',
                 'formation professionnelle', 'coaching',
                 'intégration système', 'migration données',
                 'cybersécurité', 'audit informatique'],
    },
    # ── 613/614 Autres charges externes ──────────────────────
    # 6131 Locations
    {
        'compte': '6131',
        'libelle': "Locations et charges locatives",
        'ops': ['ACHAT_LOYER'],
        'mots': ['loyer', 'bail', 'location bureau',
                 'location local', 'location entrepôt',
                 'charge locative', 'gérance', 'sous-location'],
    },
    {
        'compte': '61315',
        'libelle': "Locations de matériel informatique",
        'ops': [],
        'mots': ['location serveur', 'location pc',
                 'location ordinateur', 'location matériel info',
                 'leasing matériel'],
    },
    {
        'compte': '61316',
        'libelle': "Locations de matériel de transport",
        'ops': [],
        'mots': ['location véhicule', 'location voiture',
                 'location camion', 'location utilitaire',
                 'leasing véhicule'],
    },
    # 6132 Crédit-bail
    {
        'compte': '61321',
        'libelle': "Redevances de crédit-bail",
        'ops': ['ACHAT_CREDIT_BAIL'],
        'mots': ['crédit-bail', 'leasing', 'redevance leasing',
                 'loyer leasing'],
    },
    # 6133 Entretien et réparations
    {
        'compte': '61331',
        'libelle': "Entretien et réparations des biens immobiliers",
        'ops': [],
        'mots': ['entretien bâtiment', 'réparation locaux',
                 'maintenance bâtiment', 'nettoyage locaux'],
    },
    {
        'compte': '61332',
        'libelle': "Entretien et réparations des biens mobiliers",
        'ops': [],
        'mots': ['entretien matériel', 'réparation matériel',
                 'maintenance équipement', 'dépannage matériel',
                 'réparation informatique', 'entretien véhicule'],
    },
    {
        'compte': '61335',
        'libelle': "Maintenance",
        'ops': [],
        'mots': ['contrat maintenance', 'maintenance préventive',
                 'maintenance corrective', 'télémaintenance'],
    },
    # 6134 Assurances
    {
        'compte': '6134',
        'libelle': "Primes d'assurances",
        'ops': ['ACHAT_ASSURANCE'],
        'mots': ['assurance', 'prime assurance',
                 'multirisque', 'responsabilité civile',
                 'assurance incendie', 'assurance vol',
                 'assurance maladie', 'assurance accident',
                 'wafa assurance', 'axa', 'saham', 'rma',
                 'atlanta', 'zurich', 'allianz', 'axa assurance',
                 'sanlam', 'mcma'],
    },
    # 6135 Personnel extérieur
    {
        'compte': '61351',
        'libelle': "Rémunérations du personnel occasionnel",
        'ops': [],
        'mots': ['personnel occasionnel', 'vacataire',
                 'prestataire rh', 'interim'],
    },
    {
        'compte': '61352',
        'libelle': "Rémunérations du personnel intérimaire",
        'ops': [],
        'mots': ['intérimaire', 'interim', 'agence intérim',
                 'manpower', 'adecco', 'randstad'],
    },
    # 6136 Honoraires
    {
        'compte': '61361',
        'libelle': "Commissions et courtages",
        'ops': [],
        'mots': ['commission', 'courtage', 'commissionnaire'],
    },
    {
        'compte': '61365',
        'libelle': "Honoraires",
        'ops': ['ACHAT_HONORAIRES'],
        'mots': ['honoraires', 'honoraire', 'avocat',
                 'notaire', 'expert-comptable', 'expert comptable',
                 'commissaire aux comptes', 'conseil juridique',
                 'fiduciaire', 'acte notarial', 'frais juridiques',
                 'consultant externe', 'conseil fiscal'],
    },
    {
        'compte': '61367',
        'libelle': "Frais d'actes et de contentieux",
        'ops': [],
        'mots': ['frais judiciaires', 'huissier',
                 'frais de justice', 'contentieux', 'tribunal'],
    },
    # 6137 Redevances brevets
    {
        'compte': '61371',
        'libelle': "Redevances pour brevets",
        'ops': [],
        'mots': ['redevance brevet', 'licence brevet',
                 'royalties', 'droit propriété intellectuelle'],
    },
    # 6141 Études et documentation
    {
        'compte': '6141',
        'libelle': "Études, recherches et documentation",
        'ops': ['ACHAT_ETUDE'],
        'mots': ['étude de marché', 'recherche', 'documentation',
                 'abonnement documentation', 'revue technique'],
    },
    # 6142 Transports
    {
        'compte': '61421',
        'libelle': "Transports du personnel",
        'ops': [],
        'mots': ['transport personnel', 'navette personnel',
                 'taxi personnel', 'bus personnel'],
    },
    {
        'compte': '61425',
        'libelle': "Transports sur achats",
        'ops': ['ACHAT_TRANSPORT'],
        'mots': ['transport', 'livraison', 'fret', 'coursier',
                 'dhl', 'fedex', 'ups', 'amana', 'chronopost',
                 'expédition', 'shipping', 'transit', 'messagerie',
                 'déménagement', 'frais de port', 'port payé'],
    },
    {
        'compte': '61426',
        'libelle': "Transports sur ventes",
        'ops': [],
        'mots': ['transport sur vente', 'livraison client',
                 'expedition client'],
    },
    # 6143 Déplacements
    {
        'compte': '61431',
        'libelle': "Voyages et déplacements",
        'ops': ['ACHAT_DEPLACEMENT'],
        'mots': ['voyage', 'déplacement', 'billet avion',
                 'billet train', 'hotel', 'hébergement mission',
                 'frais déplacement', 'note de frais'],
    },
    {
        'compte': '61436',
        'libelle': "Réceptions",
        'ops': [],
        'mots': ['réception', 'invitation client',
                 'restaurant affaires', 'cadeaux clients'],
    },
    # 6144 Publicité
    {
        'compte': '6144',
        'libelle': "Publicité, publications et relations publiques",
        'ops': ['ACHAT_PUBLICITE'],
        'mots': ['publicité', 'pub', 'annonce', 'affichage',
                 'marketing digital', 'réseaux sociaux',
                 'seo', 'référencement', 'google ads',
                 'facebook ads', 'instagram ads', 'linkedin ads',
                 'campagne publicitaire', 'catalogue', 'flyer',
                 'impression publicitaire', 'site web marketing'],
    },
    # 6145 Télécommunications
    {
        'compte': '61451',
        'libelle': "Frais postaux",
        'ops': [],
        'mots': ['poste maroc', 'timbre', 'affranchissement',
                 'courrier', 'colis postal', 'recommandé'],
    },
    {
        'compte': '61455',
        'libelle': "Frais de téléphone",
        'ops': ['ACHAT_TELEPHONE'],
        'mots': ['maroc telecom', 'orange maroc', 'inwi', 'meditel',
                 'téléphone', 'telephone', 'mobile', 'gsm',
                 'internet', 'adsl', 'fibre optique', 'forfait',
                 'abonnement téléphonique', 'abonnement mobile',
                 'sms', 'data mobile', 'telecom', 'télécom',
                 'ligne téléphonique', 'voip'],
    },
    # 6146 Cotisations et dons
    {
        'compte': '61461',
        'libelle': "Cotisations",
        'ops': [],
        'mots': ['cotisation', 'adhésion', 'abonnement association',
                 'quote-part', 'syndicat professionnel'],
    },
    {
        'compte': '61462',
        'libelle': "Dons",
        'ops': [],
        'mots': ['don', 'donation', 'libéralité', 'sponsoring'],
    },
    # 6147 Services bancaires
    {
        'compte': '61471',
        'libelle': "Frais d'achat et de vente des titres",
        'ops': [],
        'mots': ['courtage bourse', 'frais titre', 'brokerage'],
    },
    {
        'compte': '61473',
        'libelle': "Frais et commissions sur services bancaires",
        'ops': ['ACHAT_FRAIS_BANCAIRES'],
        'mots': ['frais bancaires', 'commission bancaire',
                 'agios', 'frais tenue compte', 'virement bancaire',
                 'attijariwafa', 'bmce', 'cih', 'bmci',
                 'banque populaire', 'société générale',
                 'crédit du maroc', 'al barid bank'],
    },
    # 616 Impôts et taxes
    {
        'compte': '6161',
        'libelle': "Impôts et taxes directs",
        'ops': ['ACHAT_TAXE'],
        'mots': ['taxe urbaine', 'patente', 'taxe édilité',
                 'taxe locale', 'vignette', 'droits enregistrement',
                 'impôt local', 'contribution'],
    },
    # 617 Charges de personnel
    {
        'compte': '6171',
        'libelle': "Rémunérations du personnel",
        'ops': [],
        'mots': ['salaire', 'appointement', 'rémunération',
                 'prime', 'gratification', 'indemnité personnel',
                 'paie', 'bulletin de salaire'],
    },
    {
        'compte': '6174',
        'libelle': "Charges sociales (CNSS, AMO, retraite)",
        'ops': [],
        'mots': ['cnss', 'amo', 'cimr', 'retraite',
                 'cotisation sociale', 'charges sociales',
                 'sécurité sociale', 'caisse retraite',
                 'mutuelle', 'prévoyance'],
    },
    # 631 Charges financières
    {
        'compte': '6311',
        'libelle': "Intérêts des emprunts et dettes",
        'ops': ['ACHAT_INTERET'],
        'mots': ['intérêt emprunt', 'intérêts bancaires',
                 'agios bancaires', 'charges financières',
                 'intérêt crédit', 'frais financiers'],
    },
    # 633 Pertes de change
    {
        'compte': '6331',
        'libelle': "Pertes de change",
        'ops': [],
        'mots': ['perte de change', 'écart de change',
                 'différence de change'],
    },
    # 638 Autres charges financières
    {
        'compte': '6386',
        'libelle': "Escomptes accordés",
        'ops': [],
        'mots': ['escompte accordé', 'remise paiement comptant'],
    },
    # 67 Impôts sur les résultats
    {
        'compte': '6701',
        'libelle': "Impôts sur les bénéfices (IS)",
        'ops': ['ACHAT_IS'],
        'mots': ['impôt société', 'is ', 'impôt sur bénéfices',
                 'contribution solidarité', 'cotisation minimale'],
    },
    # Fallback
    {
        'compte': '6148',
        'libelle': "Autres charges externes",
        'ops': [], 'mots': [],
    },
]


def _texte_facture(donnees: dict) -> str:
    fourn  = (donnees.get('fournisseur') or '').lower()
    client = (donnees.get('client')      or '').lower()
    notes  = (donnees.get('notes')       or '').lower()
    op     = (donnees.get('type_operation', '')).lower()
    items  = ' '.join(
        str(i.get('description', ''))
        for i in donnees.get('items', [])).lower()
    return f"{fourn} {client} {items} {notes} {op}"


def _score(regle: dict, op: str, texte: str) -> int:
    s = 0
    if op in regle['ops']:
        s += 10
    s += sum(2 for m in regle['mots'] if m in texte)
    return s


def _detecter_compte_immo(donnees: dict) -> tuple[str, str]:
    op    = donnees.get('type_operation', '').upper()
    texte = _texte_facture(donnees)
    best  = -1
    best_r = REGLES_IMMO[-1]
    for r in REGLES_IMMO[:-1]:
        s = _score(r, op, texte)
        if s > best:
            best  = s
            best_r = r
    log.info(f"Compte immo : {best_r['compte']} '{best_r['libelle']}' (score={best})")
    return best_r['compte'], best_r['libelle']


def _detecter_compte_produit(donnees: dict) -> tuple[str, str]:
    op    = donnees.get('type_operation', '').upper()
    texte = _texte_facture(donnees)
    best  = -1
    best_r = REGLES_PRODUIT[-1]
    for r in REGLES_PRODUIT[:-1]:
        s = _score(r, op, texte)
        if s > best:
            best  = s
            best_r = r
    log.info(f"Compte produit : {best_r['compte']} '{best_r['libelle']}' (score={best})")
    return best_r['compte'], best_r['libelle']


def _detecter_compte_charge(donnees: dict) -> tuple[str, str]:
    op    = donnees.get('type_operation', '').upper()
    texte = _texte_facture(donnees)
    best  = -1
    best_r = REGLES_CHARGE[-1]
    for r in REGLES_CHARGE[:-1]:
        s = _score(r, op, texte)
        if s > best:
            best  = s
            best_r = r
    log.info(f"Compte charge : {best_r['compte']} '{best_r['libelle']}' (score={best})")
    return best_r['compte'], best_r['libelle']


# ══════════════════════════════════════════════════════════════
# ÉCRITURE VENTE — Journal VE
# DÉBIT  3421  Clients              TTC
# CRÉDIT 7xxx  Produits              HT
# CRÉDIT 4455  TVA facturée          TVA
# ══════════════════════════════════════════════════════════════
def _generer_ecriture_vente(donnees, compte7, libelle7):
    ht      = float(donnees.get('montant_ht',    0) or 0)
    tva     = float(donnees.get('montant_tva',   0) or 0)
    ttc     = float(donnees.get('montant_ttc',   0) or 0)
    port_ht = float(donnees.get('frais_port_ht', 0) or 0)
    ttc_final = ttc if ttc > 0 else round(ht + tva + port_ht, 2)
    date = donnees.get('date_facture', '')
    num  = donnees.get('numero_facture', '')
    dest = donnees.get('client', donnees.get('fournisseur', ''))
    ref  = f"Fac.{num}" if num else dest
    lignes = []

    lignes.append({'num': 1, 'journal': 'VE', 'date': date, 'piece': ref,
                   'compte': '3421', 'libelle': f"Clients — {ref}",
                   'desc': "Clients",
                   'debit': round(ttc_final, 2), 'credit': 0.0, 'sens': 'D'})
    if ht > 0:
        lignes.append({'num': 2, 'journal': 'VE', 'date': date, 'piece': ref,
                       'compte': compte7, 'libelle': f"{libelle7} — {ref}",
                       'desc': libelle7,
                       'debit': 0.0, 'credit': round(ht, 2), 'sens': 'C'})
    if port_ht > 0:
        lignes.append({'num': len(lignes)+1, 'journal': 'VE', 'date': date, 'piece': ref,
                       'compte': '71276', 'libelle': f"Ports facturés — {ref}",
                       'desc': "Ports et frais accessoires facturés",
                       'debit': 0.0, 'credit': round(port_ht, 2), 'sens': 'C'})
    if tva > 0:
        lignes.append({'num': len(lignes)+1, 'journal': 'VE', 'date': date, 'piece': ref,
                       'compte': '4455', 'libelle': f"TVA facturée — {ref}",
                       'desc': "État — TVA facturée",
                       'debit': 0.0, 'credit': round(tva, 2), 'sens': 'C'})
    return _equilibrer(lignes, '3421', 'debit')


# ══════════════════════════════════════════════════════════════
# ÉCRITURE ACHAT CHARGE — Journal AC
# DÉBIT  6xxx  Charge HT
# DÉBIT  3455  TVA récupérable sur charges
# CRÉDIT 4411  Fournisseurs         TTC
# ══════════════════════════════════════════════════════════════
def _generer_ecriture_achat(donnees, compte6, libelle6):
    ht      = float(donnees.get('montant_ht',     0) or 0)
    tva     = float(donnees.get('montant_tva',    0) or 0)
    ttc     = float(donnees.get('montant_ttc',    0) or 0)
    port_ht = float(donnees.get('frais_port_ht',  0) or 0)
    port_tv = float(donnees.get('frais_port_tva', 0) or 0)
    tva_totale = round(tva + port_tv, 2)
    ttc_calc   = round(ht + port_ht + tva_totale, 2)
    ttc_final  = ttc if ttc > 0 and abs(ttc - ttc_calc) <= 0.10 else ttc_calc
    date  = donnees.get('date_facture', '')
    num   = donnees.get('numero_facture', '')
    fourn = donnees.get('fournisseur', 'Fournisseur')
    ref   = f"Fac.{num}" if num else fourn
    mode  = donnees.get('mode_paiement', '').upper()
    c_contrep = '5161' if mode == 'ESPECES' else '4411'
    lib_contrep = LIBELLES.get(c_contrep, c_contrep)
    lignes = []

    if ht > 0:
        lignes.append({'num': 1, 'journal': 'AC', 'date': date, 'piece': ref,
                       'compte': compte6, 'libelle': f"{libelle6} — {ref}",
                       'desc': libelle6,
                       'debit': round(ht, 2), 'credit': 0.0, 'sens': 'D'})
    if port_ht > 0:
        lignes.append({'num': len(lignes)+1, 'journal': 'AC', 'date': date, 'piece': ref,
                       'compte': '61425', 'libelle': f"Transports sur achats — {ref}",
                       'desc': "Transports sur achats",
                       'debit': round(port_ht, 2), 'credit': 0.0, 'sens': 'D'})
    if tva_totale > 0:
        lignes.append({'num': len(lignes)+1, 'journal': 'AC', 'date': date, 'piece': ref,
                       'compte': '34552', 'libelle': f"TVA récup. charges — {ref}",
                       'desc': "État — TVA récupérable sur charges",
                       'debit': round(tva_totale, 2), 'credit': 0.0, 'sens': 'D'})
    elif ht == 0 and ttc_final > 0:
        if lignes:
            lignes[0]['debit'] = round(ttc_final, 2)

    lignes.append({'num': len(lignes)+1, 'journal': 'AC', 'date': date, 'piece': ref,
                   'compte': c_contrep, 'libelle': f"{lib_contrep} — {ref}",
                   'desc': lib_contrep,
                   'debit': 0.0, 'credit': round(ttc_final, 2), 'sens': 'C'})
    return _equilibrer(lignes, c_contrep, 'credit')


# ══════════════════════════════════════════════════════════════
# ÉCRITURE ACHAT IMMOBILISATION — Journal AC
# DÉBIT  2xxx   Immobilisation       HT
# DÉBIT  34551  TVA récupérable immo TVA
# CRÉDIT 4411   Fournisseurs         TTC
# ══════════════════════════════════════════════════════════════
def _generer_ecriture_immo(donnees, compte2, libelle2):
    ht  = float(donnees.get('montant_ht',  0) or 0)
    tva = float(donnees.get('montant_tva', 0) or 0)
    ttc = float(donnees.get('montant_ttc', 0) or 0)
    tva_totale = round(tva, 2)
    ttc_calc   = round(ht + tva_totale, 2)
    ttc_final  = ttc if ttc > 0 and abs(ttc - ttc_calc) <= 0.10 else ttc_calc
    date  = donnees.get('date_facture', '')
    num   = donnees.get('numero_facture', '')
    fourn = donnees.get('fournisseur', 'Fournisseur')
    ref   = f"Fac.{num}" if num else fourn
    lignes = []

    if ht > 0:
        lignes.append({'num': 1, 'journal': 'AC', 'date': date, 'piece': ref,
                       'compte': compte2, 'libelle': f"{libelle2} — {ref}",
                       'desc': libelle2,
                       'debit': round(ht, 2), 'credit': 0.0, 'sens': 'D'})
    if tva_totale > 0:
        lignes.append({'num': len(lignes)+1, 'journal': 'AC', 'date': date, 'piece': ref,
                       'compte': '34551',
                       'libelle': f"TVA récup. immobilisations — {ref}",
                       'desc': "État — TVA récupérable sur immobilisations",
                       'debit': round(tva_totale, 2), 'credit': 0.0, 'sens': 'D'})

    lignes.append({'num': len(lignes)+1, 'journal': 'AC', 'date': date, 'piece': ref,
                   'compte': '4411', 'libelle': f"Fournisseurs — {ref}",
                   'desc': "Fournisseurs",
                   'debit': 0.0, 'credit': round(ttc_final, 2), 'sens': 'C'})
    return _equilibrer(lignes, '4411', 'credit')


def _equilibrer(lignes, compte_ajust, sens_ajust):
    td = round(sum(e['debit']  for e in lignes), 2)
    tc = round(sum(e['credit'] for e in lignes), 2)
    if abs(td - tc) > 0.005:
        ecart = round(td - tc, 2)
        log.warning(f"Correction écart={ecart} sur {compte_ajust}/{sens_ajust}")
        for e in lignes:
            if e['compte'] == compte_ajust:
                e[sens_ajust] = round(e[sens_ajust] + ecart, 2)
                break
    return lignes


# ══════════════════════════════════════════════════════════════
# POINT D'ENTRÉE
# ══════════════════════════════════════════════════════════════
def appliquer_pcg(donnees: dict) -> dict:
    donnees = resoudre_montants(donnees)
    vente   = _est_vente(donnees)
    donnees['sens_ecriture'] = 'VENTE' if vente else 'ACHAT'

    if vente:
        compte, libelle = _detecter_compte_produit(donnees)
        donnees['journal']       = 'VE'
        donnees['compte_charge'] = compte
        donnees['type_ecriture'] = 'PRODUIT'
        donnees['confirmation_requise'] = False
        ecritures = _generer_ecriture_vente(donnees, compte, libelle)
        log.info(f"📤 VENTE VE {compte} TTC={donnees.get('montant_ttc')}")

    else:
        immo = _est_immobilisation(donnees)

        if immo:
            # ── CONFIRMATION REQUISE ────────────────────────────────
            # L'achat semble être une immobilisation (prix unitaire ≥ 5000 MAD)
            # On génère les écritures en immobilisation par défaut
            # mais on demande confirmation à l'utilisateur.
            # Spring/Angular affichera une popup "Immobilisation ou Charge ?"
            compte, libelle = _detecter_compte_immo(donnees)
            donnees['journal']       = 'AC'
            donnees['compte_charge'] = compte
            donnees['type_ecriture'] = 'IMMOBILISATION'

            # Infos pour la popup de confirmation
            items = donnees.get('items', [])
            prix_max = max(
                (float(i.get('prix_unitaire', 0) or 0) for i in items),
                default=float(donnees.get('montant_ht', 0) or 0)
            )
            donnees['confirmation_requise']    = True
            donnees['confirmation_type']       = 'IMMOBILISATION_OU_CHARGE'
            donnees['confirmation_message']    = (
                f"Cet achat ({prix_max:,.0f} MAD/unité) semble être "
                f"une immobilisation ({libelle}). "
                f"Confirmez la classification :"
            )
            donnees['confirmation_option_a']   = 'IMMOBILISATION'
            donnees['confirmation_option_a_lib'] = f"Immobilisation → {compte} {libelle} (Bilan)"
            donnees['confirmation_option_b']   = 'CHARGE'
            donnees['confirmation_option_b_lib'] = "Charge → compte 6xxx (CPC)"
            donnees['compte_charge_alternatif'] = _detecter_compte_charge(donnees)[0]
            donnees['libelle_alternatif']       = _detecter_compte_charge(donnees)[1]

            ecritures = _generer_ecriture_immo(donnees, compte, libelle)
            log.info(f"🏭 IMMO AC {compte} — confirmation requise — TTC={donnees.get('montant_ttc')}")
        else:
            # Charge → classe 6
            compte, libelle = _detecter_compte_charge(donnees)
            donnees['journal']       = 'AC'
            donnees['compte_charge'] = compte
            donnees['type_ecriture'] = 'CHARGE'
            donnees['confirmation_requise'] = False
            ecritures = _generer_ecriture_achat(donnees, compte, libelle)
            log.info(f"📥 ACHAT AC {compte} TTC={donnees.get('montant_ttc')}")

    donnees['ecritures_comptables'] = ecritures
    verif = verifier_ecriture(ecritures)
    donnees['ecriture_equilibree'] = verif['equilibre']
    donnees['verif_ecriture']      = verif

    log.info(
        f"{'✅' if verif['equilibre'] else '⚠️'} PCG V5 "
        f"{'VENTE' if vente else 'ACHAT'} "
        f"compte={compte} "
        f"Σ={verif['total_debit']:.2f} "
        f"equilibre={verif['equilibre']}"
        f"{' ⚠️ CONFIRMATION REQUISE' if donnees.get('confirmation_requise') else ''}")

    return donnees