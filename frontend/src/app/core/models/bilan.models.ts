export interface DemandeAnalyseRequest {
  documentId: string;
  clientId:   string;
  cabinetId:  string;
  exercice:   number;   // ← automatique
  cheminPdf:  string;   // ← automatique depuis minioObject
}

export interface AnalyseBilanResponse {
  id: string;
  clientId: string;
  cabinetId: string;
  documentId: string;
  exercice?: number;
  // Masses
  actifImmobilise?: number;
  actifCirculantHT?: number;
  tresorerieActif?: number;
  totalActif?: number;
  financementPermanent?: number;
  passifCirculantHT?: number;
  tresoreriePassif?: number;
  totalPassif?: number;
  // Postes clés
  capitauxPropres?: number;
  resultatNet?: number;
  caOuPrimes?: number;
  // Équilibre
  frf?: number;
  bfg?: number;
  tn?: number;
  tnMethode2?: number;
  coherence?: boolean;
  // Interprétations
  interpretationFRF?: string;
  interpretationBFG?: string;
  interpretationTN?: string;
  statutTN?: string;
  // Ratios avec statut
  liquiditeGenerale?: number;
  liquiditeGeneraleStatut?: string;
  liquiditeGeneraleTexte?: string;
  liquiditeImmediate?: number;
  liquiditeImmediateStatut?: string;
  liquiditeImmediateTexte?: string;
  autonomieFinanciere?: number;
  autonomieFinanciereStatut?: string;
  autonomieFinanciereTexte?: string;
  tauxEndettement?: number;
  tauxEndettementStatut?: string;
  tauxEndettementTexte?: string;
  couvertureEmplois?: number;
  couvertureEmploisStatut?: string;
  couvertureEmploisTexte?: string;
  rentabiliteCommerciale?: number;
  rentabiliteCommercialeStatut?: string;
  rentabiliteCommercialeTexte?: string;
  rentabiliteFinanciere?: number;
  rentabiliteFinanciereStatut?: string;
  rentabiliteFinanciereTexte?: string;
  // Résumé
  anomalies?: string;
  pointsForts?: string;
  conclusion?: string;
  createdAt?: string;
  // Anciens champs pour compatibilité
  resultats?: string;
  ratios?: Record<string, number>;
  dateAnalyse?: string;
  analyseParId?: string;
}

export interface TendanceResponse {
  id: string;
  clientId: string;
  cabinetId?: string;
  indicateur?: string;
  type?: string;
  message: string;
  niveau: 'INFO' | 'WARNING' | 'CRITICAL';
  typeAlerte?: string;
  valeurActuelle?: number;
  valeurPrecedente?: number;
  estTraite?: boolean;
  traite?: boolean;
  dateCreation?: string;
  dateDetection?: string;
}
