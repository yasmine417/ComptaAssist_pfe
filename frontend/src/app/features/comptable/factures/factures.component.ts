import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DatePipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { CabinetService } from '../../../core/services/cabinet.service';
import { DocumentService } from '../../../core/services/document.service';
import { ClientResponse } from '../../../core/models/cabinet.models';
import { DocumentResponse } from '../../../core/models/document.models';
import {ComptableSidebarComponent} from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';

interface Anomalie {
  type: 'DOUBLON' | 'ERREUR_TVA' | 'MONTANT';
  message: string;
  factures: string[];
}

@Component({
  selector: 'app-factures',
  standalone: true,
  imports: [
    ComptableSidebarComponent, DatePipe,
    MatButtonModule, MatIconModule, MatProgressBarModule, MatSnackBarModule
  ],
  templateUrl: './factures.component.html',
  styleUrl: './factures.component.scss'
})
export class FacturesComponent implements OnInit {
  private authService = inject(AuthService);
  private cabinetService = inject(CabinetService);
  private documentService = inject(DocumentService);
  private snackBar = inject(MatSnackBar);

  currentUser = this.authService.currentUser;
  clients = signal<ClientResponse[]>([]);
  selectedClient = signal<ClientResponse | null>(null);
  factures = signal<DocumentResponse[]>([]);
  anomalies = signal<Anomalie[]>([]);
  uploading = signal(false);
  scanning = signal(false);
  cabinetId = signal('');



  ngOnInit() {
    const user = this.authService.currentUser();
    if (!user?.cabinetId) return;

    const cabId = user.cabinetId;
    this.cabinetId.set(cabId);

    // Charger les clients assignés à CE comptable
    this.cabinetService.mesClients(cabId).subscribe({
      next: (c) => {
        this.clients.set(c);
        if (c.length) this.selectClient(c[0]);
      }
    });
  }

  selectClient(c: ClientResponse) {
    this.selectedClient.set(c);
    this.factures.set([]);
    this.anomalies.set([]);
    this.documentService.listerParType(this.cabinetId(), 'FACTURE').subscribe({
      next: (docs) => this.factures.set(docs.filter(d => d.clientId === c.id))
    });
  }

  onFilesUpload(event: Event) {
    const files = Array.from((event.target as HTMLInputElement).files || []);
    if (!files.length || !this.selectedClient()) return;
    this.uploading.set(true);
    let done = 0;
    files.forEach(f => {
      this.documentService.uploader(f, 'FACTURE', this.cabinetId(), this.selectedClient()!.id).subscribe({
        next: (res) => {
          done++;
          if (done === files.length) {
            this.uploading.set(false);
            this.snackBar.open(`${done} facture(s) uploadée(s) !`, '', { duration: 3000, panelClass: ['success-snack'] });
            this.selectClient(this.selectedClient()!);
          }
        },
        error: () => { done++; if (done === files.length) this.uploading.set(false); }
      });
    });
  }

  detecterAnomalies() {
    if (!this.factures().length) return;
    this.scanning.set(true);
    // Simulation côté frontend (le vrai traitement est Python)
    setTimeout(() => {
      this.scanning.set(false);
      const mock: Anomalie[] = [];
      if (this.factures().length > 1) {
        mock.push({ type: 'DOUBLON', message: 'Facture potentiellement dupliquée détectée', factures: [this.factures()[0].nomFichier, this.factures()[1].nomFichier] });
      }
      mock.push({ type: 'ERREUR_TVA', message: 'Taux TVA non conforme (attendu 20%)', factures: [this.factures()[0]?.nomFichier ?? ''] });
      this.anomalies.set(mock);
      this.snackBar.open(`${mock.length} anomalie(s) détectée(s)`, '', { duration: 3000, panelClass: mock.length ? ['error-snack'] : ['success-snack'] });
    }, 2500);
  }


}
