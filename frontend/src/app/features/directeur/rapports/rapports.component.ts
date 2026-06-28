import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { DatePipe , SlicePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { CabinetService } from '../../../core/services/cabinet.service';
import { BilanService } from '../../../core/services/bilan.service';
import { DocumentService } from '../../../core/services/document.service';
import { AnalyseBilanResponse } from '../../../core/models/bilan.models';

@Component({
  selector: 'app-rapports',
  standalone: true,
  imports: [
    RouterLink, RouterLinkActive, DatePipe,
    MatButtonModule, MatIconModule, SlicePipe,
    MatTooltipModule, MatSnackBarModule
  ],
  templateUrl: './rapports.component.html',
  styleUrl: './rapports.component.scss'
})
export class RapportsComponent implements OnInit {

  private authService    = inject(AuthService);
  private cabinetService = inject(CabinetService);
  private bilanService   = inject(BilanService);
  private documentService = inject(DocumentService);
  private snackBar       = inject(MatSnackBar);
  private http           = inject(HttpClient);

  currentUser = this.authService.currentUser;
  analyses    = signal<AnalyseBilanResponse[]>([]);
  cabinetId   = signal<string>('');
  downloading = signal<string>(''); // id de l'analyse en cours

  navItems = [
    { icon: 'dashboard',     label: 'Tableau de bord', route: '/directeur' },
    { icon: 'people',        label: 'Mon équipe',       route: '/directeur/membres' },
    { icon: 'folder_shared', label: 'Mes clients',      route: '/directeur/clients' },
    { icon: 'summarize',     label: 'Rapports',         route: '/directeur/rapports' },
  ];

  ngOnInit() {
    this.cabinetService.getMonCabinet().subscribe({
      next: (cab) => {
        this.cabinetId.set(cab.id);
        this.bilanService.getByCabinet(cab.id).subscribe({
          next: (a) => this.analyses.set(a)
        });
      }
    });
  }

  telechargerRapport(analyse: AnalyseBilanResponse) {
    if (!analyse.documentId) {
      this.snackBar.open(
        'Document introuvable', 'Fermer',
        { duration: 3000, panelClass: ['error-snack'] }
      );
      return;
    }

    this.downloading.set(analyse.id);

    // Récupérer le minioObject du document
    this.documentService.getById(analyse.documentId).subscribe({
      next: (doc) => {
        if (!doc.minioObject) {
          this.downloading.set('');
          this.snackBar.open(
            'Chemin PDF introuvable', 'Fermer',
            { duration: 3000, panelClass: ['error-snack'] }
          );
          return;
        }

        const token = this.authService.getToken();
        const headers = new HttpHeaders({
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        });

        // Appel Python pour générer le rapport texte
        this.http.post(
          'http://localhost:8000/analyser-bilan/rapport',
          { chemin_pdf: doc.minioObject },
          { headers, responseType: 'text' }
        ).subscribe({
          next: (rapportTexte) => {
            this.downloading.set('');
            // Créer un fichier texte et le télécharger
            const blob = new Blob([rapportTexte as string],
              { type: 'text/plain;charset=utf-8' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `rapport_bilan_${analyse.exercice ?? 'analyse'}.txt`;
            a.click();
            window.URL.revokeObjectURL(url);

            this.snackBar.open(
              '✅ Rapport téléchargé !', '',
              { duration: 3000, panelClass: ['success-snack'] }
            );
          },
          error: (err) => {
            this.downloading.set('');
            this.snackBar.open(
              'Erreur génération rapport',
              'Fermer',
              { duration: 4000, panelClass: ['error-snack'] }
            );
          }
        });
      },
      error: () => {
        this.downloading.set('');
        this.snackBar.open(
          'Erreur récupération document', 'Fermer',
          { duration: 3000, panelClass: ['error-snack'] }
        );
      }
    });
  }

  logout() { this.authService.logout(); }
}
