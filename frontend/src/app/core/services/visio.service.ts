import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface VisioCredentials {
  token: string;
  url: string;
  roomName: string;
}

@Injectable({ providedIn: 'root' })
export class VisioService {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private base = 'http://localhost:8082/api/visio';

  // ── Comptable démarre la visio ──────────────────────
  demarrerAvecClient(
    conversationId: string,
    nomComptable: string
  ): Observable<VisioCredentials> {
    const params = new URLSearchParams({
      conversationId,
      nomComptable
    });

    return this.http.post<VisioCredentials>(
      `${this.base}/demarrer-client?${params.toString()}`,
      {},
      {
        headers: new HttpHeaders({
          Authorization: `Bearer ${this.authService.getToken()}`
        })
      }
    );
  }

  // ── N'importe qui rejoint (client sans JWT, ou comptable) ──
  rejoindre(
    roomName: string,
    nomAffiche: string,
    identityId?: string
  ): Observable<VisioCredentials> {
    const params = new URLSearchParams({
      roomName,
      nomAffiche
    });
    if (identityId) {
      params.append('identityId', identityId);
    }

    return this.http.post<VisioCredentials>(
      `${this.base}/rejoindre?${params.toString()}`,
      {}
    );
  }
}
