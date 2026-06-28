import {
  Component, inject, signal,
  OnInit, OnDestroy, computed, effect
} from '@angular/core';
import { CommonModule }       from '@angular/common';
import { MatIconModule }      from '@angular/material/icon';
import { MatButtonModule }    from '@angular/material/button';
import { MatTooltipModule }   from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ClientContextService }
  from '../../../core/services/client-context.service';
import { ComptableSidebarComponent }
  from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';
import { TresorerieService }
  from '../../../core/services/tresorerie.service';
import {
  DashboardTresorerie, KpisTresorerie,
  EvolutionMois, TresorerieMois,
  AgingCreances, TopTiers, PrevisionMois,
  Mouvement, FactureRetard
} from '../../../core/models/tresorerie.models';

import { Subscription, interval, switchMap, startWith } from 'rxjs';

@Component({
  selector:    'app-dashboard-tresorerie',
  standalone:  true,
  imports: [
    CommonModule,
    MatIconModule, MatButtonModule,
    MatTooltipModule, MatProgressBarModule,
    ComptableSidebarComponent,
  ],
  templateUrl: './dashboard-tresorerie.component.html',
  styleUrl:    './dashboard-tresorerie.component.scss'
})
export class DashboardTresorerieComponent
  implements OnInit, OnDestroy {

  private tresorerieService = inject(TresorerieService);
  clientCtx              = inject(ClientContextService);

  // ── État ──────────────────────────────────────────
  loading    = signal(true);
  dashboard  = signal<DashboardTresorerie | null>(null);
  dernierMAJ = signal<Date | null>(null);
  erreur     = signal('');

  // Onglets graphiques
  ongletGraphique = signal<'ca' | 'tresorerie' | 'previsions'>('ca');

  // Onglets tableaux
  ongletTableau = signal<'retards' | 'encaissements' | 'topClients' | 'topFournisseurs'>('retards');

  private pollingSubscription?: Subscription;
  private readonly POLLING_INTERVAL = 30_000; // 30 secondes

  // ── Getters raccourcis ────────────────────────────
  get kpis():    KpisTresorerie   | null { return this.dashboard()?.kpis ?? null; }
  get aging():   AgingCreances    | null { return this.dashboard()?.agingCreances ?? null; }
  get previsions(): PrevisionMois[]     { return this.dashboard()?.previsionsTresorerie ?? []; }
  get retards(): FactureRetard[]        { return this.dashboard()?.facturesEnRetard ?? []; }
  get encaissements(): Mouvement[]      { return this.dashboard()?.derniersEncaissements ?? []; }
  get topClients(): TopTiers[]          { return this.dashboard()?.topClients ?? []; }
  get topFournisseurs(): TopTiers[]     { return this.dashboard()?.topFournisseurs ?? []; }

  get evolutionCa(): EvolutionMois[]    { return this.dashboard()?.evolutionCa ?? []; }
  get evolutionTreso(): TresorerieMois[]{ return this.dashboard()?.evolutionTresorerie ?? []; }

  constructor() {
    effect(() => {
      // S'exécute à chaque changement de clientActifId
      // y compris au premier rendu
      const clientId = this.clientCtx.clientActifId();
      this.pollingSubscription?.unsubscribe();
      this.pollingSubscription = interval(this.POLLING_INTERVAL)
        .pipe(
          startWith(0),
          switchMap(() =>
            this.tresorerieService.getDashboard(clientId ?? undefined))
        )
        .subscribe({
          next: data => {
            this.dashboard.set(data);
            this.dernierMAJ.set(new Date());
            this.loading.set(false);
            this.erreur.set('');
          },
          error: () => {
            this.loading.set(false);
            this.erreur.set('Erreur de chargement des données');
          }
        });
    });
  }

  // ── Lifecycle ─────────────────────────────────────
  ngOnInit() {
    // Le polling est démarré dans le constructor via effect()
  }

  ngOnDestroy() {
    this.pollingSubscription?.unsubscribe();
  }

  // ── Polling toutes les 30s ─────────────────────────
  demarrerPolling() {
    this.pollingSubscription = interval(this.POLLING_INTERVAL)
      .pipe(
        startWith(0),
        switchMap(() => this.tresorerieService.getDashboard(this.clientCtx.clientActifId() ?? undefined))
      )
      .subscribe({
        next: data => {
          this.dashboard.set(data);
          this.dernierMAJ.set(new Date());
          this.loading.set(false);
          this.erreur.set('');
        },
        error: () => {
          this.loading.set(false);
          this.erreur.set(
            'Erreur de chargement des données');
        }
      });
  }

  rafraichir() {
    this.loading.set(true);
    this.tresorerieService.getDashboard(this.clientCtx.clientActifId() ?? undefined).subscribe({
      next: data => {
        this.dashboard.set(data);
        this.dernierMAJ.set(new Date());
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  // ── Formatage ─────────────────────────────────────
  formatMontant(val?: number, devise = 'MAD'): string {
    if (val == null) return '—';
    if (Math.abs(val) >= 1_000_000)
      return `${(val / 1_000_000).toFixed(2)} M`;
    if (Math.abs(val) >= 1_000)
      return `${(val / 1_000).toFixed(1)} K`;
    return `${val.toFixed(2)}`;
  }

  formatMontantComplet(val?: number, devise = 'MAD'): string {
    if (val == null) return '—';
    return `${val.toLocaleString('fr-FR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })} ${devise}`;
  }

  formatPct(val?: number): string {
    if (val == null) return '—';
    return `${val.toFixed(1)}%`;
  }

  formatDate(d?: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR');
  }

  formatHeure(d?: Date): string {
    if (!d) return '—';
    return d.toLocaleTimeString('fr-FR',
      { hour: '2-digit', minute: '2-digit' });
  }

  // ── Graphiques (barres CSS) ───────────────────────
  // On utilise des barres CSS pures — pas de dépendance Chart.js
  // pour rester léger. Les données sont normalisées 0-100%.

  maxEvolutionCa(): number {
    return Math.max(
      ...this.evolutionCa.map(m =>
        Math.max(m.caFacture, m.caEncaisse)),
      1
    );
  }

  maxEvolutionTreso(): number {
    return Math.max(
      ...this.evolutionTreso.map(m =>
        Math.max(m.encaissements, m.decaissements)),
      1
    );
  }

  maxPrevisions(): number {
    return Math.max(
      ...this.previsions.map(m =>
        Math.max(m.encaissementsPrevu, m.decaissementsPrevu)),
      1
    );
  }

  pct(val: number, max: number): number {
    return max > 0 ? Math.min((val / max) * 100, 100) : 0;
  }

  // Aging : pourcentage de chaque tranche sur le total
  agingPct(val: number): number {
    const total = this.aging?.totalEnRetard ?? 0;
    return total > 0 ? (val / total) * 100 : 0;
  }

  // Top tiers : pourcentage sur le max
  topPct(val: number, list: TopTiers[]): number {
    const max = Math.max(...list.map(t => t.montantFacture), 1);
    return (val / max) * 100;
  }

  // Couleur retard
  retardClass(jours: number): string {
    if (jours <= 30)  return 'retard-faible';
    if (jours <= 60)  return 'retard-moyen';
    if (jours <= 90)  return 'retard-fort';
    return 'retard-critique';
  }

  // Couleur solde trésorerie
  soldeClass(val: number): string {
    return val >= 0 ? 'positif' : 'negatif';
  }
}
