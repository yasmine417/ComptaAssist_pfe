export interface FactureCPC {
  id:              string;
  documentId?:     string;
  minioObject?:    string;
  nomOriginal?:    string;
  clientId?:       string;
  cabinetId?:      string;
  comptableId?:    string;

  numeroFacture?:      string;
  dateFacture?:        string;
  fournisseur?:        string;
  client?:             string;
  ice?:                string;
  ifFournisseur?:      string;
  rc?:                 string;
  siret?:              string;
  devise?:             string;
  pays?:               string;
  typeFacture?:        string;
  typeFactureDetecte?: string;
  typeOperation?:      string;
  modePaiement?:       string;
  echeance?:           string;

  montantHt?:          number;
  tvaTaux?:            number;
  montantTva?:         number;
  remise?:             number;
  montantTtc?:         number;

  fraisPortHt?:        number;
  fraisPortTva?:       number;
  fraisPortTtc?:       number;
  autresFrais?:        number;
  montantTvaMerch?:    number;
  montantTtcHorsPort?: number;

  journal?:       string;
  compteCharge?:  string;
  compteTva?:     string;
  compteTiers?:   string;
  libelleCompte?: string;

  valide?:             boolean;
  scoreConfiance?:     number;
  confianceMontants?:  number;
  regleResolution?:    string;
  coherenceOk?:        boolean;
  ecartCoherence?:     number;
  tvaLueSurFacture?:   boolean;
  tvaEtaitTotale?:     boolean;
  ecritureEquilibree?: boolean;

  alertes?:           string[];
  warnings?:          string[];
  items?:             FactureItem[];
  ecritureComptable?: EcritureComptable[];
  montantsBruts?:     MontantsBruts;
  cpc?:               CpcData;

  statut?:            StatutFacture;
  createdAt?:         string;
  urlTelechargement?: string;

  // Paiement — rapprochement bancaire
  datePaiement?:       string;
  modePaiementReel?:   string;
  referenceVirement?:  string;
  montantPaye?:        number;
  paiementComplet?:    boolean;
  paiementPartiel?:    boolean;
  resteAPayer?:        number;

  confirmationRequise?:   boolean;
  typeEcriture?:          string;
  compteChargeAlternatif?: string;
  libelleAlternatif?:     string;
  confirmationMessage?:   string;
  confirmationOptionALib?: string;
  confirmationOptionBLib?: string;
}

export interface FactureItem {
  description:     string;
  quantite:        number;
  prixUnitaire:    number;
  tvaLigne:        number;
  remiseLigne:     number;
  totalLigneHt:    number;
  prix_unitaire?:  number;
  tva_ligne?:      number;
  remise_ligne?:   number;
  total_ligne_ht?: number;
}

export interface EcritureComptable {
  num:     number;
  journal: string;
  date:    string;
  piece:   string;
  compte:  string;
  libelle: string;
  debit:   number;
  credit:  number;
  sens:    string;
  desc:    string;
}

export interface MontantsBruts {
  ttc_lu?:         number;
  ttc_libelle?:    string;
  ht_lu?:          number;
  ht_libelle?:     string;
  tva_lu?:         number;
  tva_libelle?:    string;
  taux_tva_lu?:    number;
  taux_source?:    string;
  remise_lu?:      number;
  remise_libelle?: string;
  port_ht_lu?:     number;
  port_libelle?:   string;
}

export interface CpcRubrique {
  rubrique: string;
  titre:    string;
  type:     string;
  lignes:   CpcLigne[];
  total:    number;
}

export interface CpcLigne {
  compte:  string;
  libelle: string;
  montant: number;
}

export interface CpcResultats {
  exploitation: number;
  financier:    number;
  courant:      number;
  non_courant:  number;
  avant_impots: number;
  net:          number;
}

export interface CpcData {
  entreprise?:     string;
  periode?:        { debut: string; fin: string };
  rubriques?:      CpcRubrique[];
  resultats?:      CpcResultats;
  totaux_comptes?: Record<string, number>;
}

export interface StatsFactures {
  nouveau:          number;
  enregistre:       number;
  approuve:         number;
  rejete:           number;
  paiement_partiel?: number;
  paye?:            number;
}

// Workflow :
// NOUVEAU → ENREGISTRE → APPROUVE → PAYE
//                      ↘ REJETE
//         ↗ PAIEMENT_PARTIEL (rapprochement bancaire partiel)
//
// Le client paie indépendamment (virement bancaire).
// Le comptable reçoit l'avis de sa banque (SMS/email),
// fait le rapprochement et clique "Virement reçu".
// EN_ATTENTE_PAIEMENT supprimé — inutile dans ce workflow.
export type StatutFacture =
  | 'NOUVEAU'
  | 'ENREGISTRE'
  | 'APPROUVE'
  | 'REJETE'
  | 'PAIEMENT_PARTIEL'
  | 'PAYE';
