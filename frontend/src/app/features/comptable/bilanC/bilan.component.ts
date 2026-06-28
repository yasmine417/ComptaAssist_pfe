import {
  Component, inject, signal, OnInit
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ComptableSidebarComponent } from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';
import { AuthService } from '../../../core/services/auth.service';
import { ClientContextService } from '../../../core/services/client-context.service';

@Component({
  selector: 'app-bilan',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatIconModule, MatButtonModule,
    MatProgressSpinnerModule, MatTooltipModule,
    ComptableSidebarComponent
  ],
  templateUrl: './bilan.component.html',
  styleUrl:    './bilan.component.scss'
})
export class BilanComponent implements OnInit {

  private http        = inject(HttpClient);
  private authService = inject(AuthService);
  clientCtx           = inject(ClientContextService);

  currentUser = this.authService.currentUser;
  loading     = signal(false);
  bilan       = signal<any>(null);
  erreur      = signal<string>('');

  exercice  = new Date().getFullYear();
  exercices = [
    new Date().getFullYear(),
    new Date().getFullYear() - 1,
    new Date().getFullYear() - 2
  ];

  ngOnInit() {
    const client = this.clientCtx.clientActif();
    if (client) this.chargerBilan();
  }

  chargerBilan() {
    const client = this.clientCtx.clientActif();
    if (!client) {
      this.erreur.set('Sélectionnez un client dans la sidebar');
      return;
    }

    this.loading.set(true);
    this.erreur.set('');
    this.bilan.set(null);

    // Récupérer le capital social du client
    const capital = (client as any).capitalSocial ?? 0;

    this.http.get<any>(
      `http://localhost:8086/api/bilan/${client.id}`
      + `?exercice=${this.exercice}`
      + `&capitalSocial=${capital}`
    ).subscribe({
      next: b => {
        this.bilan.set(b);
        this.loading.set(false);
      },
      error: () => {
        this.erreur.set('Impossible de charger le bilan');
        this.loading.set(false);
      }
    });
  }

  changerExercice(ex: number) {
    this.exercice = ex;
    this.chargerBilan();
  }

  formatMontant(val: any): string {
    const n = parseFloat(val ?? 0);
    return new Intl.NumberFormat('fr-MA', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(n) + ' MAD';
  }


  modeEdition   = signal(false);
  bilanEditable = signal<any>(null);

  activerEdition() {
    // Deep clone du bilan pour édition locale
    this.bilanEditable.set(
      JSON.parse(JSON.stringify(this.bilan()))
    );
    this.modeEdition.set(true);
  }

  quitterEdition() {
    // Appliquer les modifications sur le bilan principal
    if (this.bilanEditable()) {
      this.bilan.set(this.bilanEditable());
    }
    this.modeEdition.set(false);
    this.bilanEditable.set(null);
  }
  getMontantEditable(
    section: string, cote: 'actif' | 'passif',
    compte: string): number {
    const b = this.bilanEditable() || this.bilan();
    const lignes = b?.[cote]?.[section]?.lignes ?? [];
    return lignes.find((l: any) => l.compte === compte)
      ?.montant ?? 0;
  }

  modifierLigne(
    section: string,
    cote: 'actif' | 'passif',
    compteOriginal: string,
    champ: 'compte' | 'libelle' | 'montant',
    valeur: string) {

    const b = JSON.parse(JSON.stringify(this.bilanEditable()));
    const lignes = b[cote][section].lignes;
    const idx = lignes.findIndex(
      (l: any) => l.compte === compteOriginal);
    if (idx < 0) return;

    if (champ === 'montant') {
      const n = parseFloat(
        valeur.replace(/\s/g, '').replace(',', '.'));
      if (!isNaN(n)) lignes[idx].montant = n;
    } else {
      lignes[idx][champ] = valeur;
    }
    this.bilanEditable.set(b);
  }

  get bilanAffiche() {
    return this.bilanEditable() || this.bilan();
  }



  modifierLibelle(
    section: string, cote: 'actif' | 'passif',
    compte: string, valeur: string) {
    const b = JSON.parse(JSON.stringify(
      this.bilanEditable()));
    const lignes = b[cote][section].lignes;
    const idx = lignes.findIndex(
      (l: any) => l.compte === compte);
    if (idx >= 0) lignes[idx].libelle = valeur;
    this.bilanEditable.set(b);
  }

  ajouterLigne(
    section: string, cote: 'actif' | 'passif') {
    const b = JSON.parse(JSON.stringify(
      this.bilanEditable()));
    const lignes = b[cote][section].lignes;
    lignes.push({
      compte: 'XX',
      libelle: 'Nouvelle ligne',
      montant: 0
    });
    this.bilanEditable.set(b);
  }

  supprimerLigne(
    section: string, cote: 'actif' | 'passif',
    compte: string) {
    const b = JSON.parse(JSON.stringify(
      this.bilanEditable()));
    b[cote][section].lignes =
      b[cote][section].lignes.filter(
        (l: any) => l.compte !== compte);
    this.bilanEditable.set(b);
  }










  exporterExcel() {
    const client = this.clientCtx.clientActif();
    if (!client) return;
    const capital = (client as any).capitalSocial ?? 0;
    const token = this.authService.getToken();
    const url = `http://localhost:8086/api/bilan/${client.id}/export-excel`
      + `?exercice=${this.exercice}&capitalSocial=${capital}`;

    this.http.get(url, {
      headers: { Authorization: `Bearer ${token}` },
      responseType: 'blob'
    }).subscribe(blob => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `bilan-${client.nomEntreprise}-${this.exercice}.xlsx`;
      a.click();
    });
  }


  get totalActif():  number  { return this.bilanAffiche?.actif?.totalActif ?? 0; }
  get totalPassif(): number  { return this.bilanAffiche?.passif?.totalPassif ?? 0; }
  get resultat():    number  { return this.bilanAffiche?.passif?.resultatExercice ?? 0; }
  get equilibre():   boolean { return this.bilanAffiche?.equilibre ?? false; }
}
