import {
  Component, inject, signal,
  OnInit, OnDestroy, effect
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../../core/services/auth.service';
import { CabinetService } from '../../../core/services/cabinet.service';
import { ClientContextService } from '../../../core/services/client-context.service';
import { ClientResponse } from '../../../core/models/cabinet.models';
import { ComptableSidebarComponent } from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-comptable-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    MatButtonModule, MatIconModule, MatTooltipModule,
    ComptableSidebarComponent
  ],
  templateUrl: './comptable-dashboard.component.html',
  styleUrl: './comptable-dashboard.component.scss'
})
export class ComptableDashboardComponent
  implements OnInit, OnDestroy {

  private authService    = inject(AuthService);
  private cabinetService = inject(CabinetService);
  private http           = inject(HttpClient);
  clientCtx              = inject(ClientContextService);

  currentUser    = this.authService.currentUser;
  clients        = signal<ClientResponse[]>([]);
  selectedClient = signal<ClientResponse | null>(null);
  loading        = signal(true);
  erreur         = signal('');
  statsLoading   = signal(false);
  stats          = signal<any>(null);

  private chartCA!:       Chart;
  private chartCharges!:  Chart;
  private chartCADetail!: Chart;
  private chartDoughnut!: Chart;
  private chartStatuts!:  Chart;

  private currentRequestId = 0;
  private initialized      = false;

  constructor() {
    effect(() => {
      const client = this.clientCtx.clientActif();
      if (client && this.initialized) {
        this.selectedClient.set(client);
        this.chargerStats(client.id);
      }
    });
  }

  ngOnInit() {
    const user = this.currentUser();
    if (!user?.cabinetId) {
      this.erreur.set('Pas de cabinet — contactez votre directeur.');
      this.loading.set(false);
      return;
    }
    this.cabinetService.mesClients(user.cabinetId).subscribe({
      next: (c) => {
        this.clients.set(c);
        this.loading.set(false);
        if (c.length > 0) {
          this.selectedClient.set(c[0]);
          this.initialized = true;
          this.clientCtx.setClientActif(c[0]);
        } else {
          this.initialized = true;
          this.chargerStats();
        }
      },
      error: () => {
        this.erreur.set('Erreur chargement clients.');
        this.loading.set(false);
        this.initialized = true;
      }
    });
  }

  ngOnDestroy() {
    this.detruireCharts();
  }

  private detruireCharts() {
    this.chartCA?.destroy();
    this.chartCharges?.destroy();
    this.chartCADetail?.destroy();
    this.chartDoughnut?.destroy();
    this.chartStatuts?.destroy();
  }

  selectClient(c: ClientResponse) {
    this.clientCtx.setClientActif(c);
  }

  chargerStats(clientId?: string) {
    this.statsLoading.set(true);
    this.detruireCharts();

    const requestId = ++this.currentRequestId;
    const token = this.authService.getToken();
    const url = clientId
      ? `http://localhost:8086/api/factures-cpc/dashboard-stats`
      + `?clientId=${clientId}`
      : `http://localhost:8086/api/factures-cpc/dashboard-stats`;

    this.http.get<any>(url, {
      headers: { Authorization: `Bearer ${token}` }
    }).subscribe({
      next: (data) => {
        if (requestId !== this.currentRequestId) return;
        this.stats.set(data);
        this.statsLoading.set(false);
        setTimeout(() => {
          if (requestId !== this.currentRequestId) return;
          this.recreerCanvas('chartCA');
          this.recreerCanvas('chartCharges');
          this.recreerCanvas('chartCADetail');
          this.recreerCanvas('chartDoughnut');
          this.recreerCanvas('chartStatuts');
          this.initCharts(data);
        }, 100);
      },
      error: () => {
        if (requestId !== this.currentRequestId) return;
        this.statsLoading.set(false);
      }
    });
  }

  private recreerCanvas(id: string) {
    const old = document.getElementById(id);
    if (old && old.parentNode) {
      const c = document.createElement('canvas');
      c.id = id;
      old.parentNode.replaceChild(c, old);
    }
  }

  private getCanvas(id: string): HTMLCanvasElement | null {
    return document.getElementById(id) as HTMLCanvasElement;
  }

  private initCharts(data: any) {
    this.detruireCharts();

    const labels = Object.keys(data.caMensuel || {});
    const values = Object.values(data.caMensuel || {}) as number[];
    const produits = data.totalProduits || 0;
    const charges  = data.totalCharges  || 0;

    // ── 1. Courbe CA (ligne 1 gauche) ─────────────
    const canvasCA = this.getCanvas('chartCA');
    if (canvasCA) {
      this.chartCA = new Chart(canvasCA, {
        type: 'line',
        data: {
          labels,
          datasets: [{
            data: values,
            borderColor: '#1e40af',
            backgroundColor: 'rgba(30,64,175,0.08)',
            borderWidth: 2,
            pointRadius: 3,
            pointBackgroundColor: '#1e40af',
            fill: true,
            tension: 0.4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: {
                label: (ctx: any) =>
                  ` ${(ctx.parsed?.y ?? 0).toLocaleString('fr-MA')} MAD`
              }
            }
          },
          scales: {
            x: {
              display: true,
              grid: { display: false },
              ticks: {
                font: { size: 10 },
                color: '#94a3b8'
              }
            },
            y: {
              display: true,
              beginAtZero: true,
              grid: { color: 'rgba(0,0,0,0.04)' },
              ticks: {
                font: { size: 10 },
                color: '#94a3b8',
                callback: (v: any) =>
                  `${Number(v).toLocaleString('fr-MA')}`
              }
            }
          }





        }
      });
    }

    // ── 2. Courbe Charges (ligne 1 droite) ─────────
    const canvasCharges = this.getCanvas('chartCharges');
    if (canvasCharges) {
      const nbMois = labels.length || 6;
      const chargeParMois = Array(nbMois).fill(charges / nbMois);
      this.chartCharges = new Chart(canvasCharges, {
        type: 'line',
        data: {
          labels,
          datasets: [{
            data: chargeParMois,
            borderColor: '#dc2626',
            backgroundColor: 'rgba(220,38,38,0.08)',
            borderWidth: 2,
            pointRadius: 3,
            pointBackgroundColor: '#dc2626',
            fill: true,
            tension: 0.4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: {
                label: (ctx: any) =>
                  ` ${(ctx.parsed?.y ?? 0).toLocaleString('fr-MA')} MAD`
              }
            }
          },
          scales: {
            x: { display: false },
            y: { display: false, beginAtZero: true }
          }
        }
      });
    }

    // ── 3. CA Détail barres (ligne 3) ──────────────
    const canvasCADetail = this.getCanvas('chartCADetail');
    if (canvasCADetail) {
      this.chartCADetail = new Chart(canvasCADetail, {
        type: 'bar',
        data: {
          labels,
          datasets: [{
            label: 'CA HT',
            data: values,
            backgroundColor: '#6366f1',
            borderRadius: 6,
            borderSkipped: false
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            y: {
              beginAtZero: true,
              grid: { color: 'rgba(0,0,0,0.04)' },
              ticks: {
                callback: (v: any) =>
                  `${Number(v).toLocaleString('fr-MA')}`
              }
            },
            x: { grid: { display: false } }
          }
        }
      });
    }

    // ── 4. Doughnut Résultat (ligne 3) ─────────────
    const canvasDoughnut = this.getCanvas('chartDoughnut');
    if (canvasDoughnut) {
      this.chartDoughnut = new Chart(canvasDoughnut, {
        type: 'doughnut',
        data: {
          labels: ['Produits', 'Charges'],
          datasets: [{
            data: [produits, charges],
            backgroundColor: ['#10b981', '#f43f5e'],
            borderWidth: 0,
            hoverOffset: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '68%',
          plugins: {
            legend: {
              position: 'bottom',
              labels: {
                padding: 12,
                font: { size: 11 },
                usePointStyle: true
              }
            },
            tooltip: {
              callbacks: {
                label: (ctx: any) =>
                  ` ${(ctx.parsed ?? 0).toLocaleString('fr-MA')} MAD`
              }
            }
          }
        }
      });
    }

    // ── 5. Barres Statuts (ligne 3) ────────────────
    const canvasStatuts = this.getCanvas('chartStatuts');
    if (canvasStatuts) {
      const statuts = data.statuts || {};
      const sLabels = Object.keys(statuts).map(s => {
        const m: Record<string, string> = {
          BROUILLON: 'Brouillon', VALIDE: 'Validé',
          PAYE: 'Payé', EN_ATTENTE_PAIEMENT: 'En attente',
          PAIEMENT_PARTIEL: 'Partiel', ANNULE: 'Annulé'
        };
        return m[s] || s;
      });
      const sValues = Object.values(statuts) as number[];
      const colors  = [
        '#94a3b8', '#6366f1', '#10b981',
        '#f59e0b', '#8b5cf6', '#f43f5e'
      ];
      this.chartStatuts = new Chart(canvasStatuts, {
        type: 'bar',
        data: {
          labels: sLabels,
          datasets: [{
            data: sValues,
            backgroundColor: colors.slice(0, sLabels.length),
            borderRadius: 6,
            borderSkipped: false
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            y: {
              beginAtZero: true,
              grid: { color: 'rgba(0,0,0,0.04)' },
              ticks: { stepSize: 1 }
            },
            x: { grid: { display: false } }
          }
        }
      });
    }
  }

  get heure(): string {
    const h = new Date().getHours();
    if (h < 12) return 'Bonjour';
    if (h < 18) return 'Bon après-midi';
    return 'Bonsoir';
  }

  get dateAujourdhui(): string {
    return new Date().toLocaleDateString('fr-MA', {
      weekday: 'long', day: 'numeric',
      month: 'long', year: 'numeric'
    });
  }

  formatMontant(val: number): string {
    return new Intl.NumberFormat('fr-MA', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(val ?? 0) + ' MAD';
  }
}
