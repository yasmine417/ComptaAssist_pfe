import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  AvancementDossier,
  CabinetRequest,
  CabinetResponse, ClientDetailComplet,
  ClientRequest,
  ClientResponse,
  MembreRequest,
  MembreResponse
} from '../models/cabinet.models';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CabinetService {
  private readonly BASE = 'http://localhost:8082/api/cabinets';
  private http = inject(HttpClient);

  // Cabinet
  creerCabinet(req: CabinetRequest): Observable<CabinetResponse> {
    return this.http.post<CabinetResponse>(this.BASE, req);
  }

  getMonCabinet(): Observable<CabinetResponse> {
    return this.http.get<CabinetResponse>(`${this.BASE}/mon-cabinet`);
  }

  getCabinetById(id: string): Observable<CabinetResponse> {
    return this.http.get<CabinetResponse>(`${this.BASE}/${id}`);
  }

  modifierCabinet(id: string, req: CabinetRequest): Observable<CabinetResponse> {
    return this.http.put<CabinetResponse>(`${this.BASE}/${id}`, req);
  }

  // Clients
  creerClient(cabinetId: string, req: ClientRequest): Observable<ClientResponse> {
    return this.http.post<ClientResponse>(`${this.BASE}/${cabinetId}/clients`, req);
  }

  listerClients(cabinetId: string): Observable<ClientResponse[]> {
    return this.http.get<ClientResponse[]>(`${this.BASE}/${cabinetId}/clients`);
  }

  // src/app/core/services/cabinet.service.ts

  mesClients(cabinetId: string): Observable<ClientResponse[]> {
    return this.http.get<ClientResponse[]>(
      `${this.BASE}/${cabinetId}/clients/mes-clients`
    );
  }

  getClientById(cabinetId: string, clientId: string): Observable<ClientResponse> {
    return this.http.get<ClientResponse>(`${this.BASE}/${cabinetId}/clients/${clientId}`);
  }

  modifierClient(cabinetId: string, clientId: string, req: ClientRequest): Observable<ClientResponse> {
    return this.http.put<ClientResponse>(`${this.BASE}/${cabinetId}/clients/${clientId}`, req);
  }

  desactiverClient(cabinetId: string, clientId: string): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/${cabinetId}/clients/${clientId}`);
  }

  // Membres
  ajouterMembre(cabinetId: string, req: MembreRequest): Observable<MembreResponse> {
    return this.http.post<MembreResponse>(`${this.BASE}/${cabinetId}/membres`, req);
  }

  listerMembres(cabinetId: string): Observable<MembreResponse[]> {
    return this.http.get<MembreResponse[]>(`${this.BASE}/${cabinetId}/membres`);
  }


  desactiverMembre(cabinetId: string,
                   membreId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.BASE}/${cabinetId}/membres/${membreId}`
    );
  }

  supprimerMembre(cabinetId: string,
                  membreId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.BASE}/${cabinetId}/membres/${membreId}/supprimer`
    );
  }
  reactiverMembre(cabinetId: string,
                  membreId: string): Observable<void> {
    return this.http.patch<void>(
      `${this.BASE}/${cabinetId}/membres/${membreId}/reactiver`,
      {}
    );
  }
  listerAvancement(cabinetId: string): Observable<AvancementDossier[]> {
    return this.http.get<AvancementDossier[]>(
      `${this.BASE}/${cabinetId}/avancement`
    );
  }


  getDetailComplet(
    cabinetId: string,
    clientId: string
  ): Observable<ClientDetailComplet> {
    return this.http.get<ClientDetailComplet>(
      `${this.BASE}/${cabinetId}/clients/${clientId}/detail`
    );
  }
}
