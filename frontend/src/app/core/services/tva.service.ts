// src/app/core/services/tva.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import {
  DashboardTva, ProchainePeriode, DeclarationTva,
  ClientTvaConfig, ConfirmerPeriodeRequest,
  ConfigurerRegimeRequest
} from '../models/tva.models';

@Injectable({ providedIn: 'root' })
export class TvaService {

  private http        = inject(HttpClient);
  private authService = inject(AuthService);
  private BASE = 'http://localhost:8087/api/tva';

  private h(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authService.getToken()}`
    });
  }

  getDashboard(cabinetId: string): Observable<DashboardTva> {
    return this.http.get<DashboardTva>(
      `${this.BASE}/dashboard?cabinetId=${cabinetId}`,
      { headers: this.h() });
  }

  /** Calcule automatiquement la prochaine période à déclarer */
  getProchainePeriode(clientId: string): Observable<ProchainePeriode> {
    return this.http.get<ProchainePeriode>(
      `${this.BASE}/prochaine-periode?clientId=${clientId}`,
      { headers: this.h() });
  }

  /** Config régime du client (mensuel/trimestriel) */
  getConfig(clientId: string): Observable<ClientTvaConfig> {
    return this.http.get<ClientTvaConfig>(
      `${this.BASE}/config/${clientId}`,
      { headers: this.h() });
  }

  /** Configurer le régime d'un client — une seule fois */
  configurerRegime(req: ConfigurerRegimeRequest): Observable<ClientTvaConfig> {
    return this.http.post<ClientTvaConfig>(
      `${this.BASE}/configurer-regime`, req,
      { headers: this.h() });
  }

  /** Calculer la TVA après confirmation de la période */
  calculer(req: ConfirmerPeriodeRequest): Observable<DeclarationTva> {
    return this.http.post<DeclarationTva>(
      `${this.BASE}/calculer`, req,
      { headers: this.h() });
  }

  soumettre(id: string): Observable<DeclarationTva> {
    return this.http.post<DeclarationTva>(
      `${this.BASE}/${id}/soumettre`, {},
      { headers: this.h() });
  }

  parClient(clientId: string): Observable<DeclarationTva[]> {
    return this.http.get<DeclarationTva[]>(
      `${this.BASE}/client/${clientId}`,
      { headers: this.h() });
  }

  getById(id: string): Observable<DeclarationTva> {
    return this.http.get<DeclarationTva>(
      `${this.BASE}/${id}`,
      { headers: this.h() });
  }
  getDeclarationsEnRetard(cabinetId: string): Observable<DeclarationTva[]> {
    return this.http.get<DeclarationTva[]>(
      `${this.BASE}/alertes?cabinetId=${cabinetId}`,
      { headers: this.h() });
  }
}
