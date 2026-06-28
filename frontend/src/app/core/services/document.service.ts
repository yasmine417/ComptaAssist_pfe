import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentResponse, DocumentUploadResponse } from '../models/document.models';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly BASE = 'http://localhost:8083/api/documents';
  private http = inject(HttpClient);

  uploader(fichier: File, typeDocument: string, cabinetId: string, clientId?: string): Observable<DocumentUploadResponse> {
    const form = new FormData();
    form.append('fichier', fichier);
    form.append('typeDocument', typeDocument);
    form.append('cabinetId', cabinetId);
    if (clientId) form.append('clientId', clientId);
    return this.http.post<DocumentUploadResponse>(this.BASE, form);
  }

  listerParClient(clientId: string): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(`${this.BASE}/client/${clientId}`);
  }

  listerParCabinet(cabinetId: string): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(`${this.BASE}/cabinet/${cabinetId}`);
  }

  listerParType(cabinetId: string, type: string): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(`${this.BASE}/cabinet/${cabinetId}/type/${type}`);
  }

  getById(id: string): Observable<DocumentResponse> {
    return this.http.get<DocumentResponse>(`${this.BASE}/${id}`);
  }

  telecharger(id: string): Observable<DocumentResponse> {
    return this.http.get<DocumentResponse>(`${this.BASE}/${id}/telecharger`);
  }

  marquerAnalyse(id: string): Observable<void> {
    return this.http.patch<void>(`${this.BASE}/${id}/analyser`, {});
  }

  supprimer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/${id}`);
  }
}
