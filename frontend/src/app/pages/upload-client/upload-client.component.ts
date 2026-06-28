import {
  Component, inject, signal, OnInit
} from '@angular/core';
import { ActivatedRoute }   from '@angular/router';
import { CommonModule }     from '@angular/common';
import { HttpClient }       from '@angular/common/http';
import { MatIconModule }    from '@angular/material/icon';
import { MatButtonModule }  from '@angular/material/button';
import { MatProgressBarModule }
  from '@angular/material/progress-bar';
import { MatSnackBar,
  MatSnackBarModule }
  from '@angular/material/snack-bar';
import { LienUploadService }
  from '../../core/services/lien-upload.service';
import {ChatClientComponent} from '../../features/chat-client/chat-client.component';

interface FichierUpload {
  nom:     string;
  taille:  number;
  statut:  'ATTENTE' | 'UPLOADING' | 'OK' | 'ERREUR';
  message?: string;
}

@Component({
  selector:    'app-upload-client',
  standalone:  true,
  imports: [
    CommonModule, MatIconModule,
    MatButtonModule, MatProgressBarModule,ChatClientComponent,
    MatSnackBarModule
  ],
  templateUrl: './upload-client.component.html',
  styleUrl:    './upload-client.component.scss'
})
export class UploadClientComponent implements OnInit {

  private route        = inject(ActivatedRoute);
  private http         = inject(HttpClient);
  private lienService  = inject(LienUploadService);
  private snackBar     = inject(MatSnackBar);

  private BASE =
    'http://localhost:8086/api/factures-cpc';

  // État du lien
  token        = signal('');
  nomClient    = signal('');
  expiresAt    = signal('');
  lienValide   = signal<boolean | null>(null);
  lienMessage  = signal('');

  // Upload
  fichiers     = signal<FichierUpload[]>([]);
  uploading    = signal(false);
  dragOver     = signal(false);
  totalUploade = signal(0);

  nonLusClient = signal(0);

  ngOnInit() {
    const token = this.route.snapshot.paramMap
      .get('token') || '';
    this.token.set(token);
    this.validerLien(token);
  }
  clientId = signal('');
  showChat = signal(false);
  validerLien(token: string) {
    this.lienService.validerToken(token).subscribe({
      next: (r: any) => {  // ← ajoute ': any'
        this.lienValide.set(r.valide);
        if (r.valide) {
          this.nomClient.set(r.nomClient);
          this.expiresAt.set(r.expiresAt);
          this.clientId.set(r.clientId || '');
          this.demarrerPollingNonLus(r.clientId);
        } else {
          this.lienMessage.set(
            r.message || 'Lien invalide ou expiré');
        }
      },
      error: () => {
        this.lienValide.set(false);
        this.lienMessage.set('Lien invalide');
      }
    });
  }

  private pollingNonLus: any;

  demarrerPollingNonLus(clientId: string) {
    if (!clientId) return;

    this.pollingNonLus = setInterval(() => {
      // Si le chat est ouvert → badge = 0 toujours
      if (this.showChat()) {
        this.nonLusClient.set(0);
        return;
      }

      // Si fermé depuis moins de 5 secondes → badge = 0
      const maintenant = Date.now();
      if ((maintenant - this.chatOuvertDepuis) < 5000) {
        this.nonLusClient.set(0);
        return;
      }

      this.http.get<any[]>(
        `http://localhost:8088/api/chat/conversations/client/${clientId}`
      ).subscribe(convs => {
        const total = convs.reduce(
          (sum: number, c: any) =>
            sum + (c.nonLusClient || 0), 0);
        this.nonLusClient.set(total);
      });
    }, 3000);
  }

  ngOnDestroy() {
    if (this.pollingNonLus) {
      clearInterval(this.pollingNonLus);
    }
  }
  private chatOuvertDepuis: number = 0;
  toggleChat() {
    const wasOpen = this.showChat();
    this.showChat.set(!wasOpen);

    const clientId = this.clientId();

    if (!wasOpen) {
      // On ouvre le chat
      this.nonLusClient.set(0);
      this.chatOuvertDepuis = Date.now();

      if (clientId) {
        this.http.get<any[]>(
          `http://localhost:8088/api/chat/conversations/client/${clientId}`
        ).subscribe(convs => {
          convs.forEach(conv => {
            this.http.put(
              `http://localhost:8088/api/chat/conversations/${conv.id}/lire?clientId=${clientId}`,
              {}
            ).subscribe();
          });
        });
      }
    } else {
      // On ferme le chat — marquer aussi comme lus
      this.nonLusClient.set(0);
      this.chatOuvertDepuis = Date.now();

      if (clientId) {
        this.http.get<any[]>(
          `http://localhost:8088/api/chat/conversations/client/${clientId}`
        ).subscribe(convs => {
          convs.forEach(conv => {
            this.http.put(
              `http://localhost:8088/api/chat/conversations/${conv.id}/lire?clientId=${clientId}`,
              {}
            ).subscribe();
          });
        });
      }
    }
  }



  onFileSelect(event: Event) {
    const files = Array.from(
      (event.target as HTMLInputElement).files || []);
    this.ajouterFichiers(files);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(false);
    const files = Array.from(
      event.dataTransfer?.files || []);
    this.ajouterFichiers(files);
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave() { this.dragOver.set(false); }

  ajouterFichiers(files: File[]) {
    const valides = files.filter(f =>
      f.type === 'application/pdf' ||
      f.type.startsWith('image/')
    );

    if (!valides.length) {
      this.snackBar.open(
        'Seuls PDF et images acceptés', 'OK',
        { duration: 3000 });
      return;
    }

    const nouveaux: FichierUpload[] = valides.map(f => ({
      nom:    f.name,
      taille: f.size,
      statut: 'ATTENTE' as const
    }));

    this.fichiers.update(list => [
      ...list, ...nouveaux]);
  }

  uploaderTout() {
    const fileInput = document.querySelector(
      'input[type=file]') as HTMLInputElement;
    const filesMap  = new Map<string, File>();

    // Reconstruire map des fichiers
    if (fileInput?.files) {
      Array.from(fileInput.files).forEach(f => {
        filesMap.set(f.name, f);
      });
    }

    const enAttente = this.fichiers()
      .filter(f => f.statut === 'ATTENTE');

    if (!enAttente.length) {
      this.snackBar.open(
        'Aucun fichier à envoyer', 'OK',
        { duration: 3000 });
      return;
    }

    this.uploading.set(true);
    let done = 0;

    enAttente.forEach(fInfo => {
      const file = filesMap.get(fInfo.nom);
      if (!file) {
        done++;
        return;
      }
      this._uploadFichier(file, fInfo.nom, () => {
        done++;
        if (done === enAttente.length) {
          this.uploading.set(false);
          const ok = this.fichiers()
            .filter(f => f.statut === 'OK').length;
          this.totalUploade.update(n => n + ok);
          this.snackBar.open(
            `✅ ${ok} facture(s) envoyée(s) !`,
            '', { duration: 4000,
              panelClass: ['success-snack'] });
        }
      });
    });
  }

  private _uploadFichier(
    file:     File,
    nom:      string,
    callback: () => void) {

    // Mettre en UPLOADING
    this.fichiers.update(list =>
      list.map(f =>
        f.nom === nom
          ? { ...f, statut: 'UPLOADING' as const }
          : f));

    const formData = new FormData();
    formData.append('fichier', file);

    this.http.post<any>(
      `${this.BASE}/upload-client/${this.token()}`,
      formData
    ).subscribe({
      next: (res) => {
        this.fichiers.update(list =>
          list.map(f =>
            f.nom === nom
              ? { ...f,
                statut:  'OK' as const,
                message: 'Envoyé avec succès' }
              : f));
        callback();
      },
      error: (err) => {
        this.fichiers.update(list =>
          list.map(f =>
            f.nom === nom
              ? { ...f,
                statut:  'ERREUR' as const,
                message: err?.error?.message
                  || 'Erreur envoi' }
              : f));
        callback();
      }
    });
  }

  supprimerFichier(nom: string) {
    this.fichiers.update(list =>
      list.filter(f => f.nom !== nom));
  }

  formatSize(bytes: number): string {
    if (bytes < 1024)
      return `${bytes} o`;
    if (bytes < 1024 * 1024)
      return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  get progression(): number {
    const total = this.fichiers().length;
    if (!total) return 0;
    const ok = this.fichiers()
      .filter(f => f.statut === 'OK').length;
    return Math.round((ok / total) * 100);
  }
  get nbEnAttente(): number {
    return this.fichiers()
      .filter(f => f.statut === 'ATTENTE').length;
  }

  get aFichiersEnAttente(): boolean {
    return this.fichiers()
      .some(f => f.statut === 'ATTENTE');
  }

  get toutTermine(): boolean {
    return this.totalUploade() > 0
      && !this.fichiers()
        .some(f => f.statut === 'ATTENTE'
          || f.statut === 'UPLOADING');
  }
}
