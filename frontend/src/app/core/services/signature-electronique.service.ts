import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

export interface SignatureElectronique {
  id: string;
  cabinetId: string;
  clientId: string;
  clientEmail: string;
  clientNom: string;
  typeDocument: 'LETTRE_MISSION' | 'MANDAT_TVA' | 'APPROBATION_BILAN';
  openSignStatus: string;
  statut: 'ENVOYE' | 'EN_ATTENTE_CLIENT' | 'SIGNE' | 'REFUSE' | 'EXPIRE';
  signedDocumentUrl?: string;
  createdAt: string;
  signedAt?: string;
  expiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class SignatureElectroniqueService {

  private http        = inject(HttpClient);
  private authService = inject(AuthService);
  private BASE        = 'http://localhost:8082/api/signatures';

  private headers(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authService.getToken()}`
    });
  }

  envoyer(payload: {
    cabinetId: string; clientId: string; clientEmail: string;
    clientNom: string; typeDocument: string;
    formData: Record<string, string>;
  }): Observable<SignatureElectronique> {
    return this.http.post<SignatureElectronique>(
      `${this.BASE}/envoyer`, payload, { headers: this.headers() });
  }

  // Comptable signe le document
  signerComptable(
    id: string,
    signature: string,
    comptableNom: string
  ): Observable<any> {
    return this.http.post<any>(
      `${this.BASE}/${id}/signer-comptable`,
      { signature, comptableNom },
      { headers: this.headers() }
    );
  }

  listerParCabinet(cabinetId: string): Observable<SignatureElectronique[]> {
    return this.http.get<SignatureElectronique[]>(
      `${this.BASE}/cabinet/${cabinetId}`, { headers: this.headers() });
  }

  listerParClient(clientId: string): Observable<SignatureElectronique[]> {
    return this.http.get<SignatureElectronique[]>(
      `${this.BASE}/client/${clientId}`, { headers: this.headers() });
  }

  synchroniser(id: string): Observable<SignatureElectronique> {
    return this.http.post<SignatureElectronique>(
      `${this.BASE}/${id}/sync`, {}, { headers: this.headers() });
  }

  renvoyerEmail(id: string): Observable<void> {
    return this.http.post<void>(
      `${this.BASE}/${id}/renvoyer`, {}, { headers: this.headers() });
  }

  revoquer(id: string): Observable<SignatureElectronique> {
    return this.http.delete<SignatureElectronique>(
      `${this.BASE}/${id}/revoquer`, { headers: this.headers() });
  }
}
