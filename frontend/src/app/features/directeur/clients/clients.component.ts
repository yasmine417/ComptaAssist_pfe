import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../../core/services/auth.service';
import { CabinetService } from '../../../core/services/cabinet.service';
import {ClientDetailComplet, ClientResponse, MembreResponse} from '../../../core/models/cabinet.models';
import { CommonModule } from '@angular/common';
@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [
    CommonModule, RouterLink, RouterLinkActive, ReactiveFormsModule,
    MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTooltipModule
  ],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.scss'
})
export class ClientsComponent implements OnInit {
  private authService    = inject(AuthService);
  private cabinetService = inject(CabinetService);
  private fb             = inject(FormBuilder);
  private snackBar       = inject(MatSnackBar);

  currentUser = this.authService.currentUser;
  clients     = signal<ClientResponse[]>([]);
  membres     = signal<MembreResponse[]>([]);
  cabinetId   = signal<string>('');
  showForm    = signal(false);
  loading     = signal(false);
  search      = signal('');

  navItems = [
    { icon: 'dashboard',     label: 'Tableau de bord', route: '/directeur' },
    { icon: 'people',        label: 'Mon équipe',       route: '/directeur/membres' },
    { icon: 'folder_shared', label: 'Mes clients',      route: '/directeur/clients' },
    { icon: 'summarize',     label: 'Rapports',         route: '/directeur/rapports' },
  ];

  // Formulaire adapté au backend
  form = this.fb.group({
    nomEntreprise: ['', Validators.required],
    numeroFiscal:  ['', Validators.required],
    ice:           [''],
    capitalSocial: [null],
    email:         ['', Validators.email],
    telephone:     [''],
    adresse:       [''],
    secteur:       [''],
    comptableId:   ['']   // optionnel
  });

  get filteredClients() {
    const q = this.search().toLowerCase();
    return this.clients().filter(c =>
      c.nomEntreprise.toLowerCase().includes(q) ||
      c.numeroFiscal.toLowerCase().includes(q)
    );
  }

  ngOnInit() {
    this.cabinetService.getMonCabinet().subscribe({
      next: (cab) => {
        this.cabinetId.set(cab.id);
        this.cabinetService.listerClients(cab.id).subscribe({
          next: (c) => this.clients.set(c)
        });
        this.cabinetService.listerMembres(cab.id).subscribe({
          next: (m) => this.membres.set(m)
        });
      }
    });
  }

  creer() {
    if (this.form.invalid || !this.cabinetId()) return;
    this.loading.set(true);
    const val = this.form.value;
    // Supprimer comptableId si vide
    const payload: any = { ...val };
    if (!payload.comptableId) delete payload.comptableId;

    this.cabinetService.creerClient(this.cabinetId(), payload).subscribe({
      next: (c) => {
        this.clients.update(list => [c, ...list]);
        this.showForm.set(false);
        this.form.reset();
        this.loading.set(false);
        this.snackBar.open('Client créé !', '', {
          duration: 3000,
          panelClass: ['success-snack']
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.snackBar.open(
          err?.error?.erreur || 'Erreur',
          'Fermer',
          { duration: 4000, panelClass: ['error-snack'] }
        );
      }
    });
  }

  desactiver(id: string) {
    this.cabinetService.desactiverClient(this.cabinetId(), id).subscribe({
      next: () => {
        this.clients.update(list => list.filter(c => c.id !== id));
        this.snackBar.open('Client désactivé', '', { duration: 3000 });
      }
    });
  }
  getDocStatutClass(statut: string): string {
    switch (statut) {
      case 'PAYE':              return 'paye';
      case 'APPROUVE':          return 'approuve';
      case 'PAIEMENT_PARTIEL':  return 'partiel';
      case 'REJETE':            return 'rejete';
      default:                  return 'nouveau';
    }
  }
  getTvaStatutClass(statut: string): string {
    switch (statut) {
      case 'VALIDEE':   return 'paye';
      case 'SOUMISE':   return 'approuve';
      case 'EN_RETARD':  return 'rejete';
      default:           return 'nouveau';
    }
  }
  detailClient    = signal<ClientDetailComplet | null>(null);
  showDetailModal = signal(false);
  loadingDetail   = signal(false);

  ouvrirDetail(clientId: string) {
    if (!this.cabinetId()) return;

    this.showDetailModal.set(true);
    this.loadingDetail.set(true);

    this.cabinetService.getDetailComplet(this.cabinetId(), clientId)
      .subscribe({
        next: (data) => {
          this.detailClient.set(data);
          this.loadingDetail.set(false);
        },
        error: () => this.loadingDetail.set(false)
      });
  }

  fermerDetail() {
    this.showDetailModal.set(false);
    this.detailClient.set(null);
  }

  logout() { this.authService.logout(); }
}
