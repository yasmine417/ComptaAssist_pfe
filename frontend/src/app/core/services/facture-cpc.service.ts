import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { FactureCPC, StatsFactures } from '../models/facture.models';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class FactureCpcService {

  private http        = inject(HttpClient);
  private authService = inject(AuthService);

  private BASE = 'http://localhost:8086/api/factures-cpc';

  private headers(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authService.getToken()}`
    });
  }

  // ── Lister toutes mes factures ────────────────────
  mesFactures(clientId?: string): Observable<FactureCPC[]> {
    let params = new HttpParams();
    if (clientId) params = params.set('clientId', clientId);
    return this.http.get<FactureCPC[]>(
      `${this.BASE}/mes-factures`,
      { headers: this.headers(), params }
    );
  }

  // ── Lister par statut ─────────────────────────────
  parStatut(statut: string, clientId?: string): Observable<FactureCPC[]> {
    let params = new HttpParams();
    if (clientId) params = params.set('clientId', clientId);
    return this.http.get<FactureCPC[]>(
      `${this.BASE}/mes-factures/statut/${statut}`,
      { headers: this.headers(), params }
    );
  }

  // ── Stats ─────────────────────────────────────────
  stats(clientId?: string): Observable<StatsFactures> {
    let params = new HttpParams();
    if (clientId) params = params.set('clientId', clientId);
    return this.http.get<StatsFactures>(
      `${this.BASE}/stats`,
      { headers: this.headers(), params }
    );
  }

  // ── Détail facture ────────────────────────────────
  getById(id: string): Observable<FactureCPC> {
    return this.http.get<FactureCPC>(
      `${this.BASE}/${id}`,
      { headers: this.headers() }
    );
  }

  // ── Changer statut ────────────────────────────────
  changerStatut(id: string, statut: string): Observable<FactureCPC> {
    return this.http.patch<FactureCPC>(
      `${this.BASE}/${id}/statut/${statut}`,
      {},
      { headers: this.headers() }
    );
  }

  // ── URL PDF depuis document-service ───────────────
  getUrlPdf(documentId: string): Observable<any> {
    return this.http.get(
      `http://localhost:8083/api/documents/${documentId}/telecharger`,
      { headers: this.headers() }
    );
  }

  // ── Upload facture par comptable ──────────────────
  analyser(
    fichier: File,
    clientId: string,
    cabinetId: string,
    documentId?: string,
    minioObject?: string
  ): Observable<FactureCPC> {
    const form = new FormData();
    form.append('fichier',   fichier);
    form.append('clientId',  clientId);
    form.append('cabinetId', cabinetId);
    if (documentId)  form.append('documentId',  documentId);
    if (minioObject) form.append('minioObject', minioObject);
    return this.http.post<FactureCPC>(
      `${this.BASE}/analyser`,
      form,
      { headers: new HttpHeaders({
          'Authorization': `Bearer ${this.authService.getToken()}`
        })
      }
    );
  }

  // ── Modifier écriture ─────────────────────────────
  modifierEcriture(id: string, body: any): Observable<FactureCPC> {
    return this.http.put<FactureCPC>(
      `${this.BASE}/${id}/ecriture`,
      body,
      { headers: this.headers() }
    );
  }

  // ── Confirmer paiement ────────────────────────────
  confirmerPaiement(id: string, body: any): Observable<FactureCPC> {
    return this.http.patch<FactureCPC>(
      `${this.BASE}/${id}/paiement`,
      body,
      { headers: this.headers() }
    );
  }

  // ── Export ────────────────────────────────────────
  exporterCsv(id: string): Observable<Blob> {
    return this.http.get(
      `${this.BASE}/${id}/export?format=csv`,
      { headers: this.headers(), responseType: 'blob' }
    );
  }

  exporterExcel(id: string): Observable<Blob> {
    return this.http.get(
      `${this.BASE}/${id}/export?format=xlsx`,
      { headers: this.headers(), responseType: 'blob' }
    );
  }

  // ── Reclassement immobilisation / charge ──────────
  reclasser(id: string, classification: 'IMMOBILISATION' | 'CHARGE'): Observable<any> {
    return this.http.post<any>(
      `${this.BASE}/${id}/reclasser`,
      { factureId: id, classification },
      { headers: this.headers() }
    );
  }
}
