import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AnalyseBilanResponse, DemandeAnalyseRequest, TendanceResponse } from '../models/bilan.models';

@Injectable({ providedIn: 'root' })
export class BilanService {
  private readonly BILAN_URL = 'http://localhost:8084/api/bilans';
  private readonly TENDANCE_URL = 'http://localhost:8084/api/tendances';
  private http = inject(HttpClient);

  analyser(req: DemandeAnalyseRequest): Observable<AnalyseBilanResponse> {
    return this.http.post<AnalyseBilanResponse>(`${this.BILAN_URL}/analyser`, req);
  }

  getHistoriqueClient(clientId: string): Observable<AnalyseBilanResponse[]> {
    return this.http.get<AnalyseBilanResponse[]>(`${this.BILAN_URL}/client/${clientId}`);
  }

  getById(id: string): Observable<AnalyseBilanResponse> {
    return this.http.get<AnalyseBilanResponse>(`${this.BILAN_URL}/${id}`);
  }

  getByCabinet(cabinetId: string): Observable<AnalyseBilanResponse[]> {
    return this.http.get<AnalyseBilanResponse[]>(`${this.BILAN_URL}/cabinet/${cabinetId}`);
  }

  supprimer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.BILAN_URL}/${id}`);
  }

  getTendancesClient(clientId: string): Observable<TendanceResponse[]> {
    return this.http.get<TendanceResponse[]>(`${this.TENDANCE_URL}/client/${clientId}`);
  }

  getAlertesClient(clientId: string): Observable<TendanceResponse[]> {
    return this.http.get<TendanceResponse[]>(`${this.TENDANCE_URL}/client/${clientId}/alertes`);
  }

  getAlertesCabinet(cabinetId: string): Observable<TendanceResponse[]> {
    return this.http.get<TendanceResponse[]>(`${this.TENDANCE_URL}/cabinet/${cabinetId}/alertes`);
  }

  marquerTraite(id: string): Observable<void> {
    return this.http.patch<void>(`${this.TENDANCE_URL}/${id}/traiter`, {});
  }
}
