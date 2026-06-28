import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../../core/services/auth.service';
import { CabinetService } from '../../../core/services/cabinet.service';
import {
  CabinetResponse, ClientResponse, MembreResponse,
  AvancementDossier
} from '../../../core/models/cabinet.models';
import { computed } from '@angular/core';
@Component({
  selector: 'app-directeur-dashboard',
  standalone: true,
  imports: [
    RouterLink, RouterLinkActive, ReactiveFormsModule,
    MatButtonModule, MatIconModule, MatTooltipModule,
    MatSnackBarModule, MatFormFieldModule, MatInputModule
  ],
  templateUrl: './directeur-dashboard.component.html',
  styleUrl: './directeur-dashboard.component.scss'
})
export class DirecteurDashboardComponent implements OnInit {
  private authService    = inject(AuthService);
  private cabinetService = inject(CabinetService);
  private fb             = inject(FormBuilder);
  private snackBar       = inject(MatSnackBar);

  currentUser        = this.authService.currentUser;
  cabinet            = signal<CabinetResponse | null>(null);
  clients            = signal<ClientResponse[]>([]);
  membres            = signal<MembreResponse[]>([]);
  avancements        = signal<AvancementDossier[]>([]);
  loadingAvancement  = signal(true);
  loading            = signal(true);
  showCreateCabinet  = signal(false);
  creatingCabinet    = signal(false);

  navItems = [
    { icon: 'dashboard',     label: 'Tableau de bord', route: '/directeur' },
    { icon: 'people',        label: 'Mon équipe',       route: '/directeur/membres' },
    { icon: 'folder_shared', label: 'Mes clients',      route: '/directeur/clients' },
    { icon: 'summarize',     label: 'Rapports',         route: '/directeur/rapports' },
  ];

  cabinetForm = this.fb.group({
    nom:       ['', Validators.required],
    email:     ['', [Validators.required, Validators.email]],
    telephone: [''],
    adresse:   ['']
  });

  ngOnInit() {
    this.cabinetService.getMonCabinet().subscribe({
      next: (cab) => {
        this.cabinet.set(cab);
        this.loadData(cab.id);
      },
      error: () => {
        this.loading.set(false);
        this.showCreateCabinet.set(true);
      }
    });
  }
  dossiersParComptable = computed(() => {
    const groupes = new Map<string, AvancementDossier[]>();
    for (const d of this.avancements()) {
      const liste = groupes.get(d.comptableNom) || [];
      liste.push(d);
      groupes.set(d.comptableNom, liste);
    }
    return Array.from(groupes.entries()).map(([comptable, dossiers]) => ({
      comptable,
      dossiers
    }));
  });
  creerCabinet() {
    if (this.cabinetForm.invalid) return;
    this.creatingCabinet.set(true);

    this.cabinetService.creerCabinet(
      this.cabinetForm.value as any
    ).subscribe({
      next: (cab) => {
        this.cabinet.set(cab);
        this.showCreateCabinet.set(false);
        this.creatingCabinet.set(false);
        this.snackBar.open(
          'Cabinet créé avec succès !', '',
          { duration: 3000, panelClass: ['success-snack'] }
        );
        this.loadData(cab.id);
      },
      error: (err) => {
        this.creatingCabinet.set(false);
        this.snackBar.open(
          err?.error?.erreur || 'Erreur lors de la création',
          'Fermer',
          { duration: 4000, panelClass: ['error-snack'] }
        );
      }
    });
  }

  private loadData(cabinetId: string) {
    this.cabinetService.listerClients(cabinetId).subscribe({
      next: (c) => { this.clients.set(c); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
    this.cabinetService.listerMembres(cabinetId).subscribe({
      next: (m) => this.membres.set(m)
    });

    // ← Charger l'avancement automatique des dossiers
    this.loadingAvancement.set(true);
    this.cabinetService.listerAvancement(cabinetId).subscribe({
      next: (a) => {
        this.avancements.set(a);
        this.loadingAvancement.set(false);
      },
      error: () => this.loadingAvancement.set(false)
    });
  }

  // ── Helpers pour le template ──────────────────────────────
  getAvancement(clientId: string): AvancementDossier | undefined {
    return this.avancements().find(a => a.clientId === clientId);
  }


  getStatutClass(statut: string): string {
    if (statut === 'TERMINÉ') return 'ok';
    if (statut === 'EN COURS') return 'warn';
    return 'danger';
  }
  get dossiersEnRetard(): number {
    return this.avancements()
      .filter(a => a.statutCalcule === 'EN RETARD').length;
  }

  logout() { this.authService.logout(); }
}
