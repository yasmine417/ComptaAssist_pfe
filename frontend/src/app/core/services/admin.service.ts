import { Injectable, inject } from '@angular/core';

import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CreateDirecteurRequest {
  nom:    string;
  prenom: string;
  email:  string;
}

export interface UserAdmin {
  id:        string;
  nom:       string;
  prenom:    string;
  email:     string;
  role:      string;
  actif:     boolean;
  statut:    string;
  cabinetId?: string;
}

export interface AuditLog {
  id:         string;
  userEmail:  string;
  userRole:   string;
  action:     string;
  objetType:  string;
  objetId:    string;
  details:    string;
  createdAt:  string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {

  private readonly BASE = 'http://localhost:8081/api/admin';
  private http = inject(HttpClient);

  // Utilisateurs
  getTousUtilisateurs(): Observable<UserAdmin[]> {
    return this.http.get<UserAdmin[]>(
      `${this.BASE}/utilisateurs`);
  }

  getDirecteurs(): Observable<UserAdmin[]> {
    return this.http.get<UserAdmin[]>(
      `${this.BASE}/directeurs`);
  }

  getEnAttente(): Observable<UserAdmin[]> {
    return this.http.get<UserAdmin[]>(
      `${this.BASE}/en-attente`);
  }

  creerDirecteur(req: CreateDirecteurRequest): Observable<UserAdmin> {
    return this.http.post<UserAdmin>(
      `${this.BASE}/directeurs`, req);
  }

  genererMotDePasse(id: string): Observable<UserAdmin> {
    return this.http.post<UserAdmin>(
      `${this.BASE}/utilisateurs/${id}/generer-mdp`, {});
  }

  desactiver(id: string): Observable<void> {
    return this.http.delete<void>(
      `${this.BASE}/utilisateurs/${id}`);
  }

  reactiver(id: string): Observable<void> {
    return this.http.patch<void>(
      `${this.BASE}/utilisateurs/${id}/reactiver`, {});
  }

  // Audit
  getAuditLogs(page = 0, size = 20): Observable<any> {
    return this.http.get(
      `${this.BASE}/audit?page=${page}&size=${size}`);
  }

  getAuditParEmail(email: string): Observable<any> {
    return this.http.get(
      `${this.BASE}/audit?email=${email}`);
  }

  // RAG
  indexerDocument(cheminPdf: string, nomDocument: string): Observable<any> {
    return this.http.post(
      `${this.BASE}/rag/indexer`,
      null,
      { params: { cheminPdf, nomDocument } }
    );
  }
// Ajoute cette méthode dans AdminService après indexerDocument()
  uploadEtIndexer(fichier: File, nomDocument: string, forcer: boolean = false): Observable<any> {
    const token = localStorage.getItem('token') ||
      sessionStorage.getItem('token') || '';

    const formData = new FormData();
    formData.append('fichier',       fichier);
    formData.append('nom_document',  nomDocument);
    formData.append('forcer',        String(forcer));

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });

    return this.http.post(
      `http://localhost:8085/api/fiscal/upload-indexer`,
      formData,
      { headers }
    );
  }
  getStatutRag(): Observable<any> {
    return this.http.get(`${this.BASE}/rag/statut`);
  }
  getHistoriqueIndexation(): Observable<any[]> {
    return this.http.get<any[]>(
      `http://localhost:8085/api/fiscal/historique`
    );
  }
  getAuditParAction(action: string, page = 0, size = 20): Observable<any> {
    return this.http.get(
      `${this.BASE}/audit?action=${action}&page=${page}&size=${size}`
    );
  }
}
