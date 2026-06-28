import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface CpcResultats {
  exploitation: number;
  financier:    number;
  courant:      number;
  non_courant:  number;
  avant_impots: number;
  net:          number;
}

export interface CpcLigne {
  compte:  string;
  libelle: string;
  montant: number;
}

export interface CpcRubrique {
  rubrique: string;
  titre:    string;
  type:     'produit' | 'charge' | 'resultat';
  lignes:   CpcLigne[];
  total:    number;
}

export interface CpcData {
  periode:        { debut: string; fin: string };
  rubriques:      CpcRubrique[];
  resultats:      CpcResultats;
  totaux_comptes: Record<string, number>;
}

export interface LigneBalance {
  compte:      string;
  intitule:    string;
  totalDebit:  number;
  totalCredit: number;
  solde:       number;
}

export interface LigneJournal {
  id:           string;
  journal:      string;
  dateEcriture: string;
  piece:        string;
  compte:       string;
  libelle:      string;
  debit:        number;
  credit:       number;
}

export interface LigneGrandLivre {
  id:           string;
  dateEcriture: string;
  piece:        string;
  libelle:      string;
  debit:        number;
  credit:       number;
  solde?:       number;
}

@Injectable({ providedIn: 'root' })
export class JournalService {

  private http        = inject(HttpClient);
  private authService = inject(AuthService);
  private BASE = 'http://localhost:8086/api/journal';

  private h(): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${this.authService.getToken()}`,
    });
  }

  // ── CPC ────────────────────────────────────────────
  getCpc(
    exercice?: string,
    debut?: string,
    fin?: string,
    clientId?: string
  ): Observable<CpcData> {
    let params = new HttpParams();
    if (exercice) { params = params.set('exercice', exercice); }
    else {
      if (debut) params = params.set('debut', debut);
      if (fin)   params = params.set('fin',   fin);
    }
    if (clientId) params = params.set('clientId', clientId);
    return this.http.get<CpcData>(
      `${this.BASE}/cpc`, { headers: this.h(), params });
  }

  // ── Balance ────────────────────────────────────────
  getBalance(
    debut: string,
    fin: string,
    clientId?: string
  ): Observable<LigneBalance[]> {
    let params = new HttpParams()
      .set('debut', debut)
      .set('fin',   fin);
    if (clientId) params = params.set('clientId', clientId);
    return this.http.get<LigneBalance[]>(
      `${this.BASE}/balance`, { headers: this.h(), params });
  }

  // ── Journal comptable (toutes les écritures) ───────
  getJournal(
    debut: string,
    fin: string,
    clientId?: string
  ): Observable<LigneJournal[]> {
    let params = new HttpParams()
      .set('debut', debut)
      .set('fin',   fin);
    if (clientId) params = params.set('clientId', clientId);
    return this.http.get<LigneJournal[]>(
      `${this.BASE}/ecritures`, { headers: this.h(), params });
  }

  // ── Grand-livre d'un compte ────────────────────────
  getGrandLivre(
    compte: string,
    debut: string,
    fin: string,
    clientId?: string
  ): Observable<LigneGrandLivre[]> {
    let params = new HttpParams()
      .set('debut', debut)
      .set('fin', fin);
    if (clientId) params = params.set('clientId', clientId);
    return this.http.get<LigneGrandLivre[]>(
      `${this.BASE}/grand-livre/${compte}`,
      { params }
    );
  }


}
