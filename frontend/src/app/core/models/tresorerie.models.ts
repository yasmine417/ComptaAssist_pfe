// tresorerie.models.ts
// Interfaces TypeScript pour le dashboard trésorerie

export interface DashboardTresorerie {
  kpis:                   KpisTresorerie;
  evolutionCa:            EvolutionMois[];
  evolutionTresorerie:    TresorerieMois[];
  agingCreances:          AgingCreances;
  topClients:             TopTiers[];
  topFournisseurs:        TopTiers[];
  previsionsTresorerie:   PrevisionMois[];
  derniersEncaissements:  Mouvement[];
  facturesEnRetard:       FactureRetard[];
  generatedAt:            string;
}

export interface KpisTresorerie {
  caFactureMoisHt:    number;
  caFactureMoisTtc:   number;
  caEncaisseMois:     number;
  decaissementsMois:  number;
  tresorerieNette:    number;
  soldeBanqueReel:    number;  // Solde réel compte banque depuis le journal
  creancesTotales:    number;
  dettesTotales:      number;
  facturesEnRetard:   number;
  tauxEncaissement:   number;
  mois:               string;
}

export interface EvolutionMois {
  mois:       string;
  moisLabel:  string;
  caFacture:  number;
  caEncaisse: number;
}

export interface TresorerieMois {
  mois:            string;
  moisLabel:       string;
  encaissements:   number;
  decaissements:   number;
  solde:           number;
}

export interface AgingCreances {
  tranche0_30:    number; nb0_30:    number;
  tranche31_60:   number; nb31_60:   number;
  tranche61_90:   number; nb61_90:   number;
  tranche90plus:  number; nb90plus:  number;
  totalEnRetard:  number;
}

export interface TopTiers {
  nom:              string;
  montantFacture:   number;
  montantEncaisse:  number;
  nbFactures:       number;
}

export interface PrevisionMois {
  mois:                  string;
  moisLabel:             string;
  encaissementsPrevu:    number;
  decaissementsPrevu:    number;
  soldePrevu:            number;
}

export interface Mouvement {
  id:             string;
  date:           string;
  tiers:          string;
  numeroFacture:  string;
  montant:        number;
  type:           'ENCAISSEMENT' | 'DECAISSEMENT';
  mode:           string;
  reference:      string;
}

export interface FactureRetard {
  id:             string;
  tiers:          string;
  numeroFacture:  string;
  echeance:       string;
  joursRetard:    number;
  montantDu:      number;
  devise:         string;
  type:           'CREANCE' | 'DETTE';
  statut:         string;
}
