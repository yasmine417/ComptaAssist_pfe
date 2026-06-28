import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { CabinetService } from '../../../core/services/cabinet.service';
import { DocumentService } from '../../../core/services/document.service';
import { BilanService } from '../../../core/services/bilan.service';
import { ClientResponse } from '../../../core/models/cabinet.models';
import { AnalyseBilanResponse } from '../../../core/models/bilan.models';
import { DocumentResponse } from '../../../core/models/document.models';
import {ComptableSidebarComponent} from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';

@Component({
  selector: 'app-bilan',
  standalone: true,
  imports: [
    ComptableSidebarComponent, DatePipe, DecimalPipe,
    MatButtonModule, MatIconModule, MatProgressBarModule,
    MatSnackBarModule, MatTooltipModule, MatProgressSpinnerModule
  ],
  templateUrl: './bilan.component.html',
  styleUrl: './bilan.component.scss'
})
export class BilanComponent implements OnInit {

  private authService    = inject(AuthService);
  private cabinetService = inject(CabinetService);
  private documentService = inject(DocumentService);
  private bilanService   = inject(BilanService);
  private snackBar       = inject(MatSnackBar);

  currentUser    = this.authService.currentUser;
  clients        = signal<ClientResponse[]>([]);
  selectedClient = signal<ClientResponse | null>(null);
  documents      = signal<DocumentResponse[]>([]);
  analyses       = signal<AnalyseBilanResponse[]>([]);
  selectedAnalyse = signal<AnalyseBilanResponse | null>(null);
  uploading      = signal(false);
  analysing      = signal(false);
  cabinetId      = signal<string>('');


  ngOnInit() {
    const user = this.authService.currentUser();
    if (!user?.cabinetId) return;

    const cabId = user.cabinetId;
    this.cabinetId.set(cabId);

    this.cabinetService.mesClients(cabId).subscribe({
      next: (c) => {
        this.clients.set(c);
        if (c.length) this.selectClient(c[0]);
      }
    });
  }

  selectClient(c: ClientResponse) {
    this.selectedClient.set(c);
    this.documents.set([]);
    this.analyses.set([]);
    this.selectedAnalyse.set(null);

    // Charger documents bilans de ce client
    this.documentService.listerParClient(c.id).subscribe({
      next: (docs) => {
        // Filtrer uniquement les bilans
        this.documents.set(
          docs.filter(d => d.typeDocument === 'BILAN')
        );
      }
    });

    // Charger historique analyses
    this.bilanService.getHistoriqueClient(c.id).subscribe({
      next: (a) => this.analyses.set(a)
    });
  }

  onFileUpload(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file || !this.selectedClient()) return;

    this.uploading.set(true);

    this.documentService.uploader(
      file,
      'BILAN',
      this.cabinetId(),
      this.selectedClient()!.id
    ).subscribe({
      next: () => {
        this.uploading.set(false);
        this.snackBar.open(
          `✅ Bilan "${file.name}" uploadé !`, '',
          { duration: 3000, panelClass: ['success-snack'] }
        );
        // Recharger la liste des documents
        this.documentService.listerParClient(
          this.selectedClient()!.id
        ).subscribe({
          next: (docs) => this.documents.set(
            docs.filter(d => d.typeDocument === 'BILAN')
          )
        });
      },
      error: () => {
        this.uploading.set(false);
        this.snackBar.open(
          'Erreur upload', 'Fermer',
          { duration: 3000, panelClass: ['error-snack'] }
        );
      }
    });
  }

  analyser(doc: DocumentResponse) {
    if (!this.selectedClient()) return;
    this.analysing.set(true);

    this.documentService.getById(doc.id).subscribe({
      next: (docComplet) => {

        if (!docComplet.minioObject) {
          this.analysing.set(false);
          this.snackBar.open(
            'Chemin MinIO introuvable', 'Fermer',
            { duration: 4000, panelClass: ['error-snack'] }
          );
          return;
        }

        // Utiliser nomOriginal ou nomFichier selon ce qui est disponible
        const nomFichier = docComplet.nomOriginal
          || docComplet.nomFichier
          || doc.nomFichier
          || '';

        const exercice = this.extraireExercice(nomFichier);

        this.bilanService.analyser({
          documentId: doc.id,
          clientId:   this.selectedClient()!.id,
          cabinetId:  this.cabinetId(),
          exercice:   exercice,
          cheminPdf:  docComplet.minioObject
        }).subscribe({
          next: (res) => {
            this.analysing.set(false);
            this.selectedAnalyse.set(res);
            this.analyses.update(list => [res, ...list]);
            this.snackBar.open(
              '🎉 Analyse IA terminée !', '',
              { duration: 3000, panelClass: ['success-snack'] }
            );
          },
          error: (err) => {
            this.analysing.set(false);
            this.snackBar.open(
              err?.error?.erreur || 'Erreur analyse IA',
              'Fermer',
              { duration: 5000, panelClass: ['error-snack'] }
            );
          }
        });
      },
      error: () => {
        this.analysing.set(false);
        this.snackBar.open(
          'Erreur récupération document', 'Fermer',
          { duration: 3000, panelClass: ['error-snack'] }
        );
      }
    });
  }

  // Extraire l'exercice depuis le nom du fichier
  // Ex: "bilan_2023.pdf" → 2023, sinon année courante
  private extraireExercice(nomFichier: string | undefined): number {
    if (!nomFichier) return new Date().getFullYear();
    const match = nomFichier.match(/20\d{2}/);
    if (match) return parseInt(match[0]);
    return new Date().getFullYear();
  }



  getRatioKeys(r: Record<string, number>) {
    return Object.keys(r);
  }
  getRatiosDisplay(a: AnalyseBilanResponse) {
    return [
      {
        nom:    'Liquidité générale',
        valeur: a.liquiditeGenerale,
        statut: a.liquiditeGeneraleStatut,
        texte:  a.liquiditeGeneraleTexte
      },
      {
        nom:    'Liquidité immédiate',
        valeur: a.liquiditeImmediate,
        statut: a.liquiditeImmediateStatut,
        texte:  a.liquiditeImmediateTexte
      },
      {
        nom:    'Autonomie financière',
        valeur: a.autonomieFinanciere,
        statut: a.autonomieFinanciereStatut,
        texte:  a.autonomieFinanciereTexte
      },
      {
        nom:    'Taux endettement',
        valeur: a.tauxEndettement,
        statut: a.tauxEndettementStatut,
        texte:  a.tauxEndettementTexte
      },
      {
        nom:    'Couverture emplois',
        valeur: a.couvertureEmplois,
        statut: a.couvertureEmploisStatut,
        texte:  a.couvertureEmploisTexte
      },
      {
        nom:    'Rentabilité commerciale',
        valeur: a.rentabiliteCommerciale,
        statut: a.rentabiliteCommercialeStatut,
        texte:  a.rentabiliteCommercialeTexte
      },
      {
        nom:    'Rentabilité financière',
        valeur: a.rentabiliteFinanciere,
        statut: a.rentabiliteFinanciereStatut,
        texte:  a.rentabiliteFinanciereTexte
      },
    ].filter(r => r.valeur != null);
  }
}
