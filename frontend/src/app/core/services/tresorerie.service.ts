import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import {
  DashboardTresorerie,
  KpisTresorerie,
  AgingCreances,
  EvolutionMois,
  FactureRetard,
  PrevisionMois
} from '../models/tresorerie.models';

@Injectable({ providedIn: 'root' })
export class TresorerieService {

  private http        = inject(HttpClient);
  private authService = inject(AuthService);

  private BASE = 'http://localhost:8086/api/tresorerie';

  private headers(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authService.getToken()}`
    });
  }

  /** Dashboard complet — utilisé au chargement + polling */
  getDashboard(clientId?: string): Observable<DashboardTresorerie> {
    const params = clientId ? `?clientId=${clientId}` : '';
    return this.http.get<DashboardTresorerie>(
      `${this.BASE}/dashboard${params}`,
      { headers: this.headers() }
    );
  }

  /** KPIs seuls — rafraîchissement rapide */
  getKpis(): Observable<KpisTresorerie> {
    return this.http.get<KpisTresorerie>(
      `${this.BASE}/kpis`,
      { headers: this.headers() }
    );
  }

  /** Évolution CA sur N mois */
  getEvolutionCa(mois = 6): Observable<EvolutionMois[]> {
    return this.http.get<EvolutionMois[]>(
      `${this.BASE}/evolution-ca?mois=${mois}`,
      { headers: this.headers() }
    );
  }

  /** Aging créances */
  getAging(): Observable<AgingCreances> {
    return this.http.get<AgingCreances>(
      `${this.BASE}/aging`,
      { headers: this.headers() }
    );
  }

  /** Prévisions trésorerie */
  getPrevisions(mois = 3): Observable<PrevisionMois[]> {
    return this.http.get<PrevisionMois[]>(
      `${this.BASE}/previsions?mois=${mois}`,
      { headers: this.headers() }
    );
  }

  /** Factures en retard */
  getRetards(limit = 20): Observable<FactureRetard[]> {
    return this.http.get<FactureRetard[]>(
      `${this.BASE}/retards?limit=${limit}`,
      { headers: this.headers() }
    );
  }
}
