import { Component, inject, signal, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { AdminService } from '../../../core/services/admin.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink,
    MatButtonModule, MatIconModule,
    MatProgressBarModule, MatSnackBarModule,
    MatTooltipModule
  ],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss'
})
export class AdminDashboardComponent implements OnInit {

  private authService  = inject(AuthService);
  private adminService = inject(AdminService);
  private snackBar     = inject(MatSnackBar);
  private http         = inject(HttpClient);

  currentUser  = this.authService.currentUser;
  uploading    = signal(false);
  indexing     = signal(false);
  dragOver     = signal(false);
  statutRag    = signal<any>(null);

  // ← ajout du champ file pour garder l'objet File original
  fichiersEnAttente = signal<{
    nom:         string;
    nomDocument: string;
    minioObject: string;
    file:        File;
    statut: 'UPLOADING' | 'ATTENTE' | 'INDEXE' | 'ERREUR'
  }[]>([]);
  historique = signal<any[]>([]);
  ngOnInit() {
    this.chargerStatutRag();
    this.chargerHistorique();
  }
  chargerHistorique() {
    this.adminService.getHistoriqueIndexation().subscribe({
      next: (h) => this.historique.set(h),
      error: () => {}
    });
  }
  chargerStatutRag() {
    this.adminService.getStatutRag().subscribe({
      next: (s) => this.statutRag.set(s),
      error: () => {}
    });
  }

  onFileSelect(event: Event) {
    const files = Array.from(
      (event.target as HTMLInputElement).files || []);
    this.uploadFichiers(files);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(false);
    const files = Array.from(
      event.dataTransfer?.files || []);
    this.uploadFichiers(files);
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave() { this.dragOver.set(false); }

  uploadFichiers(files: File[]) {
    const pdfs = files.filter(f => f.type === 'application/pdf');
    if (!pdfs.length) {
      this.snackBar.open('Seuls les PDFs sont acceptés', 'OK',
        { duration: 3000 });
      return;
    }

    const nouveaux = pdfs.map(file => {
      const nomDocument = file.name
        .replace(/\.pdf$/i, '')
        .replace(/\s*\(\d+\)\s*/g, '')
        .replace(/\s+/g, '_')
        .trim();

      return {
        nom:         file.name,
        nomDocument: nomDocument,
        minioObject: '',
        file:        file,
        statut:      'ATTENTE' as const
      };
    });

    this.fichiersEnAttente.update(list => [...list, ...nouveaux]);

    this.snackBar.open(
      `✅ ${pdfs.length} fichier(s) prêt(s) à indexer`,
      '', { duration: 3000, panelClass: ['success-snack'] });
  }

  indexer() {
    const attente = this.fichiersEnAttente()
      .filter(f => f.statut === 'ATTENTE');

    if (!attente.length) {
      this.snackBar.open(
        'Aucun document prêt à indexer', 'OK',
        { duration: 3000 });
      return;
    }

    this.indexing.set(true);
    let done = 0;

    attente.forEach(f => {
      // ← utilise uploadEtIndexer avec le vrai File
      this.adminService.uploadEtIndexer(
        f.file, f.nomDocument
      ).subscribe({
        next: (res: any) => {
          done++;

          if (res?.statut === 'deja_indexe') {
            this.fichiersEnAttente.update(list =>
              list.map(item =>
                item.nom === f.nom
                  ? { ...item, statut: 'INDEXE' as const }
                  : item
              )
            );
            this.snackBar.open(
              `ℹ️ "${f.nomDocument}" déjà indexé`,
              '', { duration: 4000 });

          } else if (res?.statut === 'ok') {
            this.fichiersEnAttente.update(list =>
              list.map(item =>
                item.nom === f.nom
                  ? { ...item, statut: 'INDEXE' as const }
                  : item
              )
            );
            this.snackBar.open(
              `✅ "${f.nomDocument}" indexé !`,
              '', { duration: 4000,
                panelClass: ['success-snack'] });

          } else {
            this.fichiersEnAttente.update(list =>
              list.map(item =>
                item.nom === f.nom
                  ? { ...item, statut: 'ERREUR' as const }
                  : item
              )
            );
            this.snackBar.open(
              `❌ Erreur : ${res?.message || 'inconnue'}`,
              'Fermer',
              { duration: 5000,
                panelClass: ['error-snack'] });
          }

          if (done === attente.length) {
            this.indexing.set(false);
            this.chargerStatutRag();
          }
        },
        error: (err) => {
          done++;
          this.fichiersEnAttente.update(list =>
            list.map(item =>
              item.nom === f.nom
                ? { ...item, statut: 'ERREUR' as const }
                : item
            )
          );
          this.snackBar.open(
            `❌ Erreur indexation "${f.nomDocument}"`,
            'Fermer',
            { duration: 5000,
              panelClass: ['error-snack'] });

          if (done === attente.length) {
            this.indexing.set(false);
          }
        }
      });
    });
  }

  reindexer(f: any) {
    this.fichiersEnAttente.update(list =>
      list.map(item =>
        item.nom === f.nom
          ? { ...item, statut: 'UPLOADING' as const }
          : item
      )
    );

    this.adminService.uploadEtIndexer(f.file, f.nomDocument, true)
      .subscribe({
        next: (res: any) => {
          this.fichiersEnAttente.update(list =>
            list.map(item =>
              item.nom === f.nom
                ? { ...item, statut: 'INDEXE' as const }
                : item
            )
          );
          this.snackBar.open(
            `✅ "${f.nomDocument}" réindexé !`,
            '', { duration: 4000,
              panelClass: ['success-snack'] });
          this.chargerStatutRag();
          this.chargerHistorique();
        },
        error: () => {
          this.fichiersEnAttente.update(list =>
            list.map(item =>
              item.nom === f.nom
                ? { ...item, statut: 'ERREUR' as const }
                : item
            )
          );
          this.snackBar.open(
            `❌ Erreur réindexation "${f.nomDocument}"`,
            'Fermer', { duration: 5000 });
        }
      });
  }


  supprimerFichier(nom: string) {
    this.fichiersEnAttente.update(
      list => list.filter(f => f.nom !== nom));
  }

  logout() { this.authService.logout(); }

  get fichiersIndexes(): number {
    return this.fichiersEnAttente()
      .filter(f => f.statut === 'INDEXE').length;
  }

  get fichiersAttente(): number {
    return this.fichiersEnAttente()
      .filter(f => f.statut === 'ATTENTE').length;
  }
}
