export interface CabinetRequest {
  nom: string;
  adresse?: string;
  telephone?: string;
  email?: string;
}

export interface CabinetResponse {
  id: string;
  nom: string;
  adresse?: string;
  telephone?: string;
  email?: string;
  directeurId: string;
  createdAt: string;
}

export interface ClientRequest {
  nomEntreprise: string;   // ← backend utilise nomEntreprise
  numeroFiscal: string;
  ice?: string;
  adresse?: string;
  capitalSocial?: number;
  telephone?: string;
  email?: string;
  secteur?: string;
  comptableId?: string;    // optionnel — pas obligatoire
}

export interface ClientResponse {
  id: string;
  nomEntreprise: string;   // ← backend utilise nomEntreprise
  numeroFiscal: string;
  ice?: string;
  adresse?: string;
  telephone?: string;
  email?: string;
  capitalSocial?: number;
  secteur?: string;
  cabinetId: string;
  comptableId?: string;
  actif: boolean;
  createdAt: string;
}

export interface MembreRequest {
  nom: string;       // ← backend attend nom/prenom/email/role
  prenom: string;
  email: string;
  role?: string;
}

export interface MembreResponse {
  id: string;
  cabinetId: string;
  userId: string;
  nom: string;
  prenom: string;
  email: string;
  role: string;
  actif: boolean;
  createdAt: string;
}


export interface ClientDetailComplet {
  client: ClientResponse;
  comptableId?: string;
  comptableNom?: string;
  factures: any[];
  ecritures: any[];
  declarationsTva: any[];
}
export interface AvancementDossier {
  clientId: string;
  nomEntreprise: string;
  comptableNom: string;
  statutCalcule: 'TERMINÉ' | 'EN COURS' | 'EN RETARD';
  facturesEnAttente: number;
  facturesTraitees: number;
  statutTva: 'DECLAREE' | 'EN_RETARD' | 'A_VENIR' | 'AUCUNE_OBLIGATION';
}
