import {
  Component, inject, signal, computed, effect
} from '@angular/core';
import { CommonModule }         from '@angular/common';
import { FormsModule }          from '@angular/forms';
import { MatIconModule }        from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule }     from '@angular/material/tooltip';

import { ComptableSidebarComponent }
  from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';
import { ClientContextService }
  from '../../../core/services/client-context.service';
import {
  JournalService, CpcData, LigneBalance,
  LigneJournal, LigneGrandLivre
} from '../../../core/services/journal.service';

type Vue = 'cpc' | 'balance' | 'journal' | 'grand-livre';

@Component({
  selector:    'app-cpc-viewer',
  standalone:  true,
  imports: [
    CommonModule, FormsModule,
    MatIconModule, MatProgressBarModule,
    MatSnackBarModule, MatTooltipModule,
    ComptableSidebarComponent,
  ],
  templateUrl: './cpc-viewer.component.html',
  styleUrl:    './cpc-viewer.component.scss',
})
export class CpcViewerComponent {

  private journalService = inject(JournalService);
  private snackBar       = inject(MatSnackBar);
  clientCtx              = inject(ClientContextService);

  // ── État ──────────────────────────────────────
  vue         = signal<Vue>('cpc');
  loading     = signal(false);
  erreur      = signal('');

  // Données
  cpc         = signal<CpcData | null>(null);
  balance     = signal<LigneBalance[]>([]);
  journal     = signal<LigneJournal[]>([]);
  grandLivre  = signal<LigneGrandLivre[]>([]);
  compteGL    = signal('');  // compte saisi pour grand-livre

  // Période
  modePeriode = signal<'exercice' | 'libre'>('exercice');
  exercice    = signal(new Date().getFullYear().toString());
  debut       = signal(`${new Date().getFullYear()}-01-01`);
  fin         = signal(`${new Date().getFullYear()}-12-31`);

  readonly ANNEES = [
    new Date().getFullYear().toString(),
    (new Date().getFullYear() - 1).toString(),
    (new Date().getFullYear() - 2).toString(),
  ];

  readonly TABS: { key: Vue; label: string; icon: string }[] = [
    { key: 'cpc',         label: 'CPC',         icon: 'analytics' },
    { key: 'balance',     label: 'Balance',      icon: 'balance' },
    { key: 'journal',     label: 'Journal',      icon: 'menu_book' },
    { key: 'grand-livre', label: 'Grand-livre',  icon: 'account_tree' },
  ];

  // ── Totaux balance ────────────────────────────
  get totalDebit():  number {
    return this.balance().reduce((s, l) => s + (l.totalDebit  || 0), 0);
  }
  get totalCredit(): number {
    return this.balance().reduce((s, l) => s + (l.totalCredit || 0), 0);
  }
  get totalSolde():  number {
    return this.balance().reduce((s, l) => s + (l.solde       || 0), 0);
  }

  // ── Totaux journal ────────────────────────────
  get totalJournalDebit():  number {
    return this.journal().reduce((s, l) => s + (l.debit  || 0), 0);
  }
  get totalJournalCredit(): number {
    return this.journal().reduce((s, l) => s + (l.credit || 0), 0);
  }

  constructor() {
    effect(() => {
      const _ = this.clientCtx.clientActifId();
      this.cpc.set(null);
      this.balance.set([]);
      this.journal.set([]);
      this.grandLivre.set([]);
      this.erreur.set('');
    });
  }

  // ── Période label ─────────────────────────────
  get periodeLabel(): string {
    if (this.modePeriode() === 'exercice')
      return `Exercice ${this.exercice()}`;
    return `${this.debut()} → ${this.fin()}`;
  }

  // ── Générer ───────────────────────────────────
  generer() {
    const v = this.vue();
    if (v === 'cpc')         this._chargerCpc();
    else if (v === 'balance') this._chargerBalance();
    else if (v === 'journal') this._chargerJournal();
    else if (v === 'grand-livre') this._chargerGrandLivre();
  }

  changerVue(v: Vue) {
    this.vue.set(v);
    this.cpc.set(null);
    this.balance.set([]);
    this.journal.set([]);
    this.grandLivre.set([]);
    this.erreur.set('');
  }

  // ── Chargements ───────────────────────────────
  private _getPeriode(): { debut: string; fin: string } {
    if (this.modePeriode() === 'exercice') {
      return {
        debut: `${this.exercice()}-01-01`,
        fin:   `${this.exercice()}-12-31`,
      };
    }
    return { debut: this.debut(), fin: this.fin() };
  }

  private _chargerCpc() {
    this.loading.set(true);
    this.erreur.set('');
    const clientId = this.clientCtx.clientActifId() ?? undefined;
    const obs = this.modePeriode() === 'exercice'
      ? this.journalService.getCpc(
        this.exercice(), undefined, undefined, clientId)
      : this.journalService.getCpc(
        undefined, this.debut(), this.fin(), clientId);

    obs.subscribe({
      next:  d => { this.cpc.set(d);    this.loading.set(false); },
      error: () => {
        this.loading.set(false);
        this.erreur.set('Erreur chargement CPC');
      },
    });
  }

  private _chargerBalance() {
    const { debut, fin } = this._getPeriode();
    const clientId = this.clientCtx.clientActifId() ?? undefined;
    this.loading.set(true);
    this.erreur.set('');
    this.journalService.getBalance(debut, fin, clientId).subscribe({
      next:  d => { this.balance.set(d); this.loading.set(false); },
      error: () => {
        this.loading.set(false);
        this.erreur.set('Erreur chargement balance');
      },
    });
  }

  private _chargerJournal() {
    const { debut, fin } = this._getPeriode();
    const clientId = this.clientCtx.clientActifId() ?? undefined;
    this.loading.set(true);
    this.erreur.set('');
    this.journalService.getJournal(debut, fin, clientId).subscribe({
      next:  d => { this.journal.set(d); this.loading.set(false); },
      error: () => {
        this.loading.set(false);
        this.erreur.set('Erreur chargement journal');
      },
    });
  }

  private _chargerGrandLivre() {
    const compte = this.compteGL().trim();
    if (!compte) {
      this.erreur.set('Saisissez un numéro de compte');
      return;
    }
    const { debut, fin } = this._getPeriode();
    const clientId = this.clientCtx.clientActifId() ?? undefined;  // ← ajouter
    this.loading.set(true);
    this.erreur.set('');
    this.journalService.getGrandLivre(compte, debut, fin, clientId).subscribe({  // ← passer clientId
      next:  d => { this.grandLivre.set(d); this.loading.set(false); },
      error: () => {
        this.loading.set(false);
        this.erreur.set('Compte introuvable ou aucune écriture');
      },
    });
  }

  // ── Export CSV ────────────────────────────────
  exporterCsv() {
    const v = this.vue();
    let rows: string[] = [];

    if (v === 'balance') {
      rows = [
        'Compte;Intitulé;Total débit;Total crédit;Solde',
        ...this.balance().map(l =>
          `${l.compte};${l.intitule};${l.totalDebit};${l.totalCredit};${l.solde}`),
        `;TOTAL;${this.totalDebit};${this.totalCredit};${this.totalSolde}`,
      ];
    } else if (v === 'journal') {
      rows = [
        'Journal;Date;Pièce;Compte;Libellé;Débit;Crédit',
        ...this.journal().map(l =>
          `${l.journal};${l.dateEcriture};${l.piece};${l.compte};${l.libelle};${l.debit};${l.credit}`),
      ];
    } else if (v === 'grand-livre') {
      rows = [
        'Date;Pièce;Libellé;Débit;Crédit',
        ...this.grandLivre().map(l =>
          `${l.dateEcriture};${l.piece};${l.libelle};${l.debit};${l.credit}`),
      ];
    }

    if (!rows.length) return;
    const blob = new Blob(
      ['\uFEFF' + rows.join('\n')],
      { type: 'text/csv;charset=utf-8' }
    );
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${v}_${this.periodeLabel}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  // ── Helpers ───────────────────────────────────
  formatMontant(v?: number): string {
    if (!v) return '—';
    return v.toLocaleString('fr-MA', {
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    });
  }

  getSoldeClass(s?: number): string {
    if (!s || s === 0) return '';
    return s > 0 ? 'solde-debiteur' : 'solde-crediteur';
  }

  getResultatClass(v?: number): string {
    if (!v) return '';
    return v >= 0 ? 'positif' : 'negatif';
  }
}
