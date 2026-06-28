// src/app/pages/tva-declaration/tva-declaration.component.ts
import {
  Component, inject, signal, OnInit, computed, effect
} from '@angular/core';
import { CommonModule }         from '@angular/common';
import { FormsModule }          from '@angular/forms';
import { MatIconModule }        from '@angular/material/icon';
import { MatButtonModule }      from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule }     from '@angular/material/tooltip';

import { ComptableSidebarComponent }
  from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';
import { ClientContextService }
  from '../../../core/services/client-context.service';
import { AuthService }
  from '../../../core/services/auth.service';
import { TvaService }
  from '../../../core/services/tva.service';
import {
  DeclarationTva, DashboardTva, ProchainePeriode,
  ClientTvaConfig, RegimeTva
} from '../../../core/models/tva.models';

@Component({
  selector:   'app-tva-declaration',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatIconModule, MatButtonModule,
    MatSnackBarModule, MatProgressBarModule,
    MatTooltipModule, ComptableSidebarComponent,
  ],
  templateUrl: './tva-declaration.component.html',
  styleUrl:    './tva-declaration.component.scss',
})
export class TvaDeclarationComponent implements OnInit {

  private tvaService  = inject(TvaService);
  private authService = inject(AuthService);
  private snackBar    = inject(MatSnackBar);
  clientCtx           = inject(ClientContextService);
  currentUser         = this.authService.currentUser;

  // ── État principal ────────────────────────────
  loading         = signal(false);
  loadingPeriode  = signal(false);
  loadingCalc     = signal(false);
  dashboard       = signal<DashboardTva | null>(null);
  declarations    = signal<DeclarationTva[]>([]);
  declaration     = signal<DeclarationTva | null>(null);

  // ── Config client ─────────────────────────────
  configClient        = signal<ClientTvaConfig | null>(null);
  showConfigRegime    = signal(false);
  regimeChoisi        = signal<RegimeTva>('MENSUEL');

  // ── Période proposée automatiquement ─────────
  prochainePeriode    = signal<ProchainePeriode | null>(null);
  // Le comptable peut ajuster les dates
  dateDebutAjustee    = signal('');
  dateFinAjustee      = signal('');
  periodeModifiee     = signal(false);

  constructor() {
    effect(() => {
      const client = this.clientCtx.clientActif();
      this.declaration.set(null);
      this.prochainePeriode.set(null);
      this.configClient.set(null);
      this.showConfigRegime.set(false);
      if (client) {
        this._chargerDeclarations(client.id);
        this._chargerConfig(client.id);
      } else {
        this.declarations.set([]);
      }
    });
  }

  ngOnInit() {
    const user = this.currentUser();
    if (user?.cabinetId) this._chargerDashboard(user.cabinetId);
  }

  // ── Chargements ───────────────────────────────
  private _chargerDashboard(cabinetId: string) {
    this.tvaService.getDashboard(cabinetId).subscribe({
      next: d => this.dashboard.set(d),
    });
  }

  private _chargerDeclarations(clientId: string) {
    this.loading.set(true);
    this.tvaService.parClient(clientId).subscribe({
      next:  d => { this.declarations.set(d); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  private _chargerConfig(clientId: string) {
    this.tvaService.getConfig(clientId).subscribe({
      next: c => {
        this.configClient.set(c);
        this.showConfigRegime.set(false);
        // Config trouvée → calculer la prochaine période
        this._chargerProchainePeriode(clientId);
      },
      error: (err) => {
        if (err.status === 404) {
          // Pas encore de config → afficher l'écran de configuration
          this.configClient.set(null);
          this.showConfigRegime.set(true);
        } else {
          // Autre erreur réseau → ne pas bloquer, tenter quand même
          this.configClient.set(null);
          this._chargerProchainePeriode(clientId);
        }
      },
    });
  }

  private _chargerProchainePeriode(clientId: string) {
    this.loadingPeriode.set(true);
    this.tvaService.getProchainePeriode(clientId).subscribe({
      next: p => {
        this.prochainePeriode.set(p);
        // Initialiser les dates ajustables avec les dates proposées
        this.dateDebutAjustee.set(p.dateDebut);
        this.dateFinAjustee.set(p.dateFin);
        this.periodeModifiee.set(false);
        this.loadingPeriode.set(false);
      },
      error: () => this.loadingPeriode.set(false),
    });
  }

  // ── Configurer régime TVA (une seule fois) ────
  configurerRegime() {
    const client = this.clientCtx.clientActif();
    const user   = this.currentUser();
    if (!client || !user?.cabinetId) return;

    this.tvaService.configurerRegime({
      clientId:  client.id,
      cabinetId: user.cabinetId,
      regime:    this.regimeChoisi(),
    }).subscribe({
      next: c => {
        this.configClient.set(c);
        this.showConfigRegime.set(false);
        this._chargerProchainePeriode(client.id);
        this.snackBar.open(
          `✅ Régime ${c.regime === 'MENSUEL' ? 'mensuel' : 'trimestriel'} configuré`,
          '', { duration: 3000, panelClass: ['success-snack'] });
      },
    });
  }

  // ── Calculer TVA ──────────────────────────────
  calculer() {
    const client = this.clientCtx.clientActif();
    const user   = this.currentUser();
    if (!client || !user?.cabinetId) return;

    this.loadingCalc.set(true);
    this.tvaService.calculer({
      clientId:  client.id,
      cabinetId: user.cabinetId,
      dateDebut: this.dateDebutAjustee(),
      dateFin:   this.dateFinAjustee(),
    }).subscribe({
      next: d => {
        this.declaration.set(d);
        this.loadingCalc.set(false);
        this._chargerDeclarations(client.id);
        this._chargerDashboard(user.cabinetId!);
        this.snackBar.open(
          '✅ TVA calculée depuis vos factures IA', '',
          { duration: 3000, panelClass: ['success-snack'] });
      },
      error: (err) => {
        this.loadingCalc.set(false);
        const msg = err?.error?.message || 'Erreur calcul TVA';
        this.snackBar.open(msg, 'Fermer',
          { duration: 5000, panelClass: ['error-snack'] });
      },
    });
  }

  // ── Ajuster la période ────────────────────────
  onDateDebutChange(val: string) {
    this.dateDebutAjustee.set(val);
    this.periodeModifiee.set(
      val !== this.prochainePeriode()?.dateDebut ||
      this.dateFinAjustee() !== this.prochainePeriode()?.dateFin);
  }

  onDateFinChange(val: string) {
    this.dateFinAjustee.set(val);
    this.periodeModifiee.set(
      this.dateDebutAjustee() !== this.prochainePeriode()?.dateDebut ||
      val !== this.prochainePeriode()?.dateFin);
  }

  reinitialiserPeriode() {
    const p = this.prochainePeriode();
    if (!p) return;
    this.dateDebutAjustee.set(p.dateDebut);
    this.dateFinAjustee.set(p.dateFin);
    this.periodeModifiee.set(false);
  }

  // ── Soumettre ─────────────────────────────────
  soumettre() {
    const d = this.declaration();
    if (!d) return;
    this.tvaService.soumettre(d.id).subscribe({
      next: updated => {
        this.declaration.set(updated);
        const client = this.clientCtx.clientActif();
        const user   = this.currentUser();
        if (client) {
          this._chargerDeclarations(client.id);
          this._chargerProchainePeriode(client.id);
        }
        if (user?.cabinetId) this._chargerDashboard(user.cabinetId);
        this.snackBar.open('✅ Déclaration soumise', '',
          { duration: 3000, panelClass: ['success-snack'] });
      },
    });
  }

  selectionner(d: DeclarationTva) { this.declaration.set(d); }

  // ── Helpers ───────────────────────────────────
  formatMontant(v?: number): string {
    return (v || 0).toLocaleString('fr-MA', {
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    });
  }

  statutClass(s?: string): string {
    return ({ BROUILLON: 'st-brouillon', SOUMISE: 'st-soumise',
        VALIDEE: 'st-validee', EN_RETARD: 'st-retard' })[s || '']
      ?? 'st-brouillon';
  }

  statutLabel(s?: string): string {
    return ({ BROUILLON: 'Brouillon', SOUMISE: 'Soumise',
      VALIDEE: 'Validée', EN_RETARD: 'En retard' })[s || ''] ?? s ?? '';
  }

  estEnRetard(dateLimite?: string): boolean {
    if (!dateLimite) return false;
    return new Date(dateLimite) < new Date();
  }

  regimeLabel(r?: string): string {
    return r === 'TRIMESTRIEL' ? 'Trimestriel' : 'Mensuel';
  }

  getMontantAffiche(d: DeclarationTva): number {
    // Si crédit TVA → afficher le crédit, sinon la TVA nette à payer
    return (d.creditTvaReporte && d.creditTvaReporte > 0)
      ? d.creditTvaReporte
      : d.tvaNette;
  }

  getLabelMontant(d: DeclarationTva): string {
    return (d.creditTvaReporte && d.creditTvaReporte > 0)
      ? 'Crédit' : 'À payer';
  }
}
