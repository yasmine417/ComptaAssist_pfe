// src/app/core/services/fiscal.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ReponseRAG {
  reponse:    string;
  sources:    string[];
  nbExtraits: number;
  extraits:   string[];
}

@Injectable({ providedIn: 'root' })
export class FiscalService {

  private readonly BASE =
    'http://localhost:8085/api/fiscal';
  private http = inject(HttpClient);

  poserQuestion(question: string): Observable<ReponseRAG> {
    return this.http.post<ReponseRAG>(
      `${this.BASE}/question`,
      { question }
    );
  }

  getStatut(): Observable<any> {
    return this.http.get(`${this.BASE}/statut`);
  }
  getHistoriqueConversations(): Observable<any[]> {
    return this.http.get<any[]>(
      `http://localhost:8085/api/fiscal/historique-conversations`
    );
  }
  poserQuestionClient(
    question: string,
    clientId: string,
    mois?: string
  ): Observable<any> {
    const params: any = { clientId };
    if (mois) params['mois'] = mois;

    return this.http.post(
      `http://localhost:8085/api/fiscal/question-client`,
      { question },
      { params }
    );
  }
}
