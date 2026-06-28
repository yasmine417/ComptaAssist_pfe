import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RapportNotification {
  id: string;
  comptableId: string;
  nomEntreprise: string;
  moisLabel: string;
  message: string;
  lu: boolean;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class RapportNotificationService {

  private base = 'http://localhost:8086/api/notifications/rapport';

  constructor(private http: HttpClient) {}

  getNotifs(comptableId: string): Observable<RapportNotification[]> {
    return this.http.get<RapportNotification[]>(`${this.base}/${comptableId}`);
  }

  marquerLu(id: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/lu`, {});
  }
}
