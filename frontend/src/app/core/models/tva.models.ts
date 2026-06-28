// src/app/core/models/tva.models.ts

export interface DashboardTva {
  clientsActifs:   number;
  declareesCeMois: number;
  enAttente:       number;
  enRetard:        number;
}

export interface ProchainePeriode {
  clientId:                    string;
  regime:                      RegimeTva;
  periodeLabel:                string;   // "Mai 2026" ou "T2 2026 (Avr — Jun)"
  dateDebut:                   string;
  dateFin:                     string;
  dateLimite:                  string;   // avant le 20 du mois suivant
  enRetard:                    boolean;
  declarationExistanteId?:     string;
  declarationExistanteStatut?: string;
}

export interface LigneTva {
  taux:            number;
  baseHtAchats:    number;
  tvaDeductible:   number;
  baseHtVentes:    number;
  tvaCollectee:    number;
  nbFacturesAchat: number;
  nbFacturesVente: number;
}

export interface DeclarationTva {
  id:                  string;
  clientId:            string;
  cabinetId:           string;
  periodeLabel:        string;
  annee:               number;
  mois?:               number;
  trimestre?:          number;
  dateDebut:           string;
  dateFin:             string;
  dateLimite:          string;
  statut:              StatutDeclaration;
  tvaCollecteeTotal:   number;
  tvaDeductibleTotal:  number;
  tvaNette:            number;
  creditTvaReporte:    number;
  dateSoumission?:     string;
  lignes:              LigneTva[];
}

export interface ClientTvaConfig {
  clientId: string;
  regime:   RegimeTva;
}

export interface ConfirmerPeriodeRequest {
  clientId:  string;
  cabinetId: string;
  dateDebut: string;
  dateFin:   string;
}

export interface ConfigurerRegimeRequest {
  clientId:  string;
  cabinetId: string;
  regime:    RegimeTva;
}

export type RegimeTva        = 'MENSUEL' | 'TRIMESTRIEL';
export type StatutDeclaration = 'BROUILLON' | 'SOUMISE' | 'EN_RETARD' | 'VALIDEE';
