import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { AuthService } from './auth.service';
import { Observable } from 'rxjs';

export interface Conversation {
  id: string;
  comptableId: string;
  comptableNom: string;
  clientId: string;
  clientNom: string;
  clientEmail: string;
  cabinetId: string;
  dernierMessage: string;
  dateDernierMessage: string;
  nonLusComptable: number;
  nonLusClient: number;
  active: boolean;
}

export interface Message {
  id: string;
  conversationId: string;
  expediteurType: 'COMPTABLE' | 'CLIENT';
  expediteurId: string;
  expediteurNom: string;
  contenu: string;
  lu: boolean;
  typeMessage: 'TEXTE' | 'FICHIER' | 'NOTIFICATION'| 'IMAGE';
  urlFichier?: string;
  nomFichier?: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class ChatService {

  private http        = inject(HttpClient);
  private authService = inject(AuthService);

  private base = 'http://localhost:8088/api/chat';

  private headers(): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${this.authService.getToken()}`
    });
  }


  ouvrirConversation(
    clientId: string,
    clientNom: string,
    clientEmail: string,
    cabinetId: string,
    comptableNom: string = ''
  ): Observable<Conversation> {
    return this.http.post<Conversation>(
      `${this.base}/conversations`,
      { clientId, clientNom, clientEmail,
        cabinetId, comptableNom },
      { headers: this.headers() }
    );
  }

  mesConversations(): Observable<Conversation[]> {
    return this.http.get<Conversation[]>(
      `${this.base}/conversations/comptable`,
      { headers: this.headers() }
    );
  }

  conversationsClient(clientId: string): Observable<Conversation[]> {
    return this.http.get<Conversation[]>(
      `${this.base}/conversations/client/${clientId}`,
      { headers: this.headers() }
    );
  }

  getMessages(conversationId: string): Observable<Message[]> {
    return this.http.get<Message[]>(
      `${this.base}/conversations/${conversationId}/messages`,
      { headers: this.headers() }
    );
  }

  envoyerMessage(
    conversationId: string, contenu: string
  ): Observable<Message> {
    return this.http.post<Message>(
      `${this.base}/conversations/${conversationId}/messages`,
      { conversationId, contenu, typeMessage: 'TEXTE' },
      { headers: this.headers() }
    );
  }

  marquerLus(conversationId: string,
             clientId?: string): Observable<void> {
    const headers = this.headers();
    const params = clientId
      ? `?clientId=${clientId}` : '';
    return this.http.put<void>(
      `${this.base}/conversations/${conversationId}/lire${params}`,
      {},
      { headers }
    );
  }

  uploadFichier(
    conversationId: string,
    file: File
  ): Observable<{url: string, nom: string, type: string}> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(
      `${this.base}/conversations/${conversationId}/upload`,
      formData,
      { headers: new HttpHeaders({
          Authorization: `Bearer ${this.authService.getToken() || ''}`
        })
      }
    );
  }
  envoyerMessageRest(
    conversationId: string,
    contenu: string,
    typeMessage: 'TEXTE' | 'FICHIER' | 'IMAGE' | 'NOTIFICATION',
    urlFichier?: string,
    nomFichier?: string,
    expediteurId?: string  // ← ajoute
  ): Observable<Message> {
    const token = this.authService.getToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    return this.http.post<Message>(
      `${this.base}/conversations/${conversationId}/messages`,
      { conversationId, contenu, typeMessage,
        urlFichier, nomFichier, expediteurId },
      { headers: new HttpHeaders(headers) }
    );
  }
}
