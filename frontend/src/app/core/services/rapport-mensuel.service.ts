import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { AuthService } from './auth.service';

export interface RapportMensuel {
  id: string;
  clientId: string;
  cabinetId: string;
  comptableId: string;
  nomEntreprise: string;
  moisLabel: string;
  contenuHtml: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class RapportMensuelService {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private base = 'http://localhost:8082/api/rapports-mensuels';

  private headers() {
    return new HttpHeaders({
      Authorization: `Bearer ${this.authService.getToken()}`
    });
  }

  mesRapports() {
    return this.http.get<RapportMensuel[]>(
      `${this.base}/mes-rapports`,
      { headers: this.headers() }
    );
  }

  parClient(clientId: string) {
    return this.http.get<RapportMensuel[]>(
      `${this.base}/par-client/${clientId}`,
      { headers: this.headers() }
    );
  }

  getById(id: string) {
    return this.http.get<RapportMensuel>(
      `${this.base}/${id}`,
      { headers: this.headers() }
    );
  }
  telechargerPdf(id: string) {
    return this.http.get(
      `${this.base}/${id}/pdf`,
      {
        headers: this.headers(),
        responseType: 'blob'
      }
    );
  }
}
