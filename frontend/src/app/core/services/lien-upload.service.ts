import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface LienUploadResponse {
  token:              string;
  url:                string;
  expiresAt:          string;
  nomClient:          string;
  emailClient?:       string;
  actif:              boolean;
  nbFichiersUploades?: number;
}

@Injectable({ providedIn: 'root' })
export class LienUploadService {

  private http        = inject(HttpClient);
  private authService = inject(AuthService);
  private BASE = 'http://localhost:8086/api/liens-upload';

  private headers(): HttpHeaders {
    return new HttpHeaders({
      'Authorization':
        `Bearer ${this.authService.getToken()}`
    });
  }

  // Générer lien pour un client
  genererLien(
    clientId:    string,
    nomClient:   string,
    emailClient: string,
    cabinetId:   string
  ): Observable<LienUploadResponse> {
    const params = new URLSearchParams({
      clientId, nomClient, emailClient, cabinetId
    });
    return this.http.post<LienUploadResponse>(
      `${this.BASE}/generer?${params.toString()}`,
      {},
      { headers: this.headers() }
    );
  }

  // Valider token (sans auth)
  validerToken(token: string): Observable<{
    valide:    boolean;
    nomClient: string;
    expiresAt: string;
    clientId?: string;
    message?:  string;
  }> {
    return this.http.get<any>(
      `${this.BASE}/valider/${token}`
    );
  }

  // Lister mes liens
  mesLiens(): Observable<LienUploadResponse[]> {
    return this.http.get<LienUploadResponse[]>(
      `${this.BASE}/mes-liens`,
      { headers: this.headers() }
    );
  }
}
