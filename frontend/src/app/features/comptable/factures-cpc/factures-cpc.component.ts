import {
  Component, inject, signal, OnInit, effect
} from '@angular/core';
import { CommonModule }         from '@angular/common';
import { FormsModule }          from '@angular/forms';
import { MatIconModule }        from '@angular/material/icon';
import { MatButtonModule }      from '@angular/material/button';
import { MatSnackBar,
  MatSnackBarModule }    from '@angular/material/snack-bar';
import { MatTooltipModule }     from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { DomSanitizer,
  SafeResourceUrl }      from '@angular/platform-browser';

import { AuthService }
  from '../../../core/services/auth.service';
import { FactureCpcService }
  from '../../../core/services/facture-cpc.service';
import {
  FactureCPC, StatutFacture,
  EcritureComptable, FactureItem,
  CpcData, CpcResultats
} from '../../../core/models/facture.models';
import { LienUploadService }
  from '../../../core/services/lien-upload.service';
import { ComptableSidebarComponent }
  from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';
import { CabinetService }
  from '../../../core/services/cabinet.service';
import { ClientContextService }
  from '../../../core/services/client-context.service';
import { ClientResponse }
  from '../../../core/models/cabinet.models';

type Tab =
  | 'TOUS' | 'NOUVEAU' | 'ENREGISTRE'
  | 'APPROUVE' | 'REJETE';

@Component({
  selector:    'app-factures-cpc',
  standalone:  true,
  imports: [
    CommonModule, FormsModule,
    MatIconModule, MatButtonModule,
    MatSnackBarModule, MatTooltipModule,
    MatProgressBarModule,
    ComptableSidebarComponent,
  ],
  templateUrl: './factures-cpc.component.html',
  styleUrl:    './factures-cpc.component.scss',
})
export class FacturesCpcComponent implements OnInit {

  // ── Services ─────────────────────────────────────
  private authService    = inject(AuthService);
  private factureService = inject(FactureCpcService);
  private cabinetService = inject(CabinetService);
  private lienService    = inject(LienUploadService);
  private snackBar       = inject(MatSnackBar);
  private sanitizer      = inject(DomSanitizer);
  clientCtx              = inject(ClientContextService);

  currentUser = this.authService.currentUser;
  loading     = signal(false);

  showModifierCpcModal = signal(false);
  cpcModifie = signal<any>(null);
  // ── Données ───────────────────────────────────────
  toutesFactures      = signal<FactureCPC[]>([]);
  factureSelectionnee = signal<FactureCPC | null>(null);
  urlPdfSafe          = signal<SafeResourceUrl | null>(null);
  loadingDetail       = signal(false);

  stats = signal({
    nouveau: 0, enregistre: 0, approuve: 0, rejete: 0
  });

  // ── Tabs ──────────────────────────────────────────
  tabActif = signal<Tab>('TOUS');
  tabs: { key: Tab; label: string; color: string }[] = [
    { key: 'NOUVEAU',    label: 'Nouveaux',     color: '#f59e0b' },
    { key: 'ENREGISTRE', label: 'Enregistrées', color: '#3b82f6' },
    { key: 'APPROUVE',   label: 'Approuvées',   color: '#22c55e' },
    { key: 'REJETE',     label: 'Rejetées',     color: '#ef4444' },
    { key: 'TOUS',       label: 'Toutes',       color: '#6b7280' },
  ];

  recherche         = signal('');
  clients           = signal<ClientResponse[]>([]);
  clientSelectionne = signal<ClientResponse | null>(null);
  lienGenere        = signal('');
  showLienModal     = signal(false);
  cabinetId         = signal('');

  // ── Modals ────────────────────────────────────────
  showModifierModal   = signal(false);
  ecrituresModifiees  = signal<any[]>([]);
  modificationEnCours = signal(false);

  showPaiementModal = signal(false);
  paiementForm = signal({
    montantPaye:       0,
    modePaiement:      'VIREMENT',
    referenceVirement: '',
  });
  erreurPaiement = signal('');

  // ── Modal confirmation immobilisation/charge ───────
  showConfirmationModal    = signal(false);
  factureAConfirmer        = signal<FactureCPC | null>(null);
  reclassementEnCours      = signal(false);

  // ── Lifecycle ─────────────────────────────────────

  constructor() {
    effect(() => {
      const clientId = this.clientCtx.clientActifId();
      const nomClient = this.clientCtx.clientActif()?.nomEntreprise;
      console.log(`[FacturesCPC] Client changé → ${nomClient} (${clientId})`);
      this.factureSelectionnee.set(null);
      this.urlPdfSafe.set(null);
      this._chargerPourClient(clientId ?? undefined);
    }, { allowSignalWrites: true });
  }

  ngOnInit() {
    this.chargerClients();
  }



  // ══════════════════════════════════════════════════════════════
// AJOUTER dans factures-cpc.component.ts
// ══════════════════════════════════════════════════════════════

// 1. Ajouter ces signals en haut de la classe :


// 2. Ajouter ces méthodes :

  ouvrirModificationCpc() {
    const f = this.factureSelectionnee();
    if (!f) return;
    // Cloner le CPC pour modification
    const cpcActuel = JSON.parse(JSON.stringify(f.cpc || {}));
    this.cpcModifie.set(cpcActuel);
    this.showModifierCpcModal.set(true);
  }

  modifierLigneCpc(rubriqueIndex: number, ligneIndex: number, champ: string, val: any) {
    this.cpcModifie.update(cpc => {
      const copy = JSON.parse(JSON.stringify(cpc));
      if (copy.rubriques?.[rubriqueIndex]?.lignes?.[ligneIndex]) {
        copy.rubriques[rubriqueIndex].lignes[ligneIndex][champ] = val;
        // Recalculer le total de la rubrique
        copy.rubriques[rubriqueIndex].total = copy.rubriques[rubriqueIndex].lignes
          .reduce((sum: number, l: any) => sum + (parseFloat(l.montant) || 0), 0);
      }
      return copy;
    });
  }

  sauvegarderCpc() {
    const f = this.factureSelectionnee();
    if (!f) return;
    this.modificationEnCours.set(true);
    this.factureService.modifierEcriture(f.id, {
      cpc: this.cpcModifie(),
    }).subscribe({
      next: updated => {
        this.toutesFactures.update(list =>
          list.map(x => x.id === updated.id ? updated : x));
        this.factureSelectionnee.set(updated);
        this.showModifierCpcModal.set(false);
        this.modificationEnCours.set(false);
        this.snackBar.open('✅ CPC modifié', '', { duration: 3000, panelClass: ['success-snack'] });
      },
      error: () => this.modificationEnCours.set(false),
    });
  }



  private _chargerPourClient(clientId?: string) {
    console.log(`[FacturesCPC] _chargerPourClient avec clientId=${clientId}`);
    this.loading.set(true);
    this.factureService.mesFactures(clientId).subscribe({
      next:  f => {
        console.log(`[FacturesCPC] ${f.length} factures reçues pour clientId=${clientId}`);
        this.toutesFactures.set(f);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.factureService.stats(clientId).subscribe({
      next: s => this.stats.set(s as any),
    });
  }

  chargerFactures() {
    this._chargerPourClient(this.clientCtx.clientActifId() ?? undefined);
  }

  chargerStats() {
    this.factureService.stats(this.clientCtx.clientActifId() ?? undefined).subscribe({
      next: s => this.stats.set(s as any),
    });
  }

  chargerClients() {
    const user = this.currentUser();
    if (!user?.cabinetId) return;
    this.cabinetId.set(user.cabinetId);

    // Toujours charger depuis le serveur pour avoir la liste complète
    this.cabinetService.mesClients(user.cabinetId).subscribe({
      next: c => {
        this.clients.set(c);              // pour la modal lien
        this.clientCtx.setClients(c);     // pour la sidebar et le reste de l'app
      },
      error: () => {},
    });
  }

  // ══════════════════════════════════════════════════
  // CONFIRMATION IMMOBILISATION / CHARGE
  // ══════════════════════════════════════════════════

  /**
   * Appelé quand le comptable clique sur une facture
   * avec confirmationRequise = true dans la liste.
   */
  ouvrirConfirmation(f: FactureCPC) {
    this.factureAConfirmer.set(f);
    this.showConfirmationModal.set(true);
  }

  /**
   * Appelé quand le comptable choisit
   * "Immobilisation" ou "Charge" dans la popup.
   */
  confirmerClassification(choix: 'IMMOBILISATION' | 'CHARGE') {
    const f = this.factureSelectionnee();
    if (!f) return;

    this.reclassementEnCours.set(true);

    this.factureService.reclasser(f.id, choix).subscribe({
      next: () => {
        this.reclassementEnCours.set(false);

        // Mettre à jour la facture — plus de confirmation requise
        const updated = { ...f, confirmationRequise: false,
          typeEcriture: choix };
        this.toutesFactures.update(list =>
          list.map(x => x.id === f.id ? updated as FactureCPC : x));
        this.factureSelectionnee.set(updated as FactureCPC);

        const msg = choix === 'IMMOBILISATION'
          ? '✅ Classé en immobilisation (Bilan)'
          : '✅ Classé en charge (CPC)';
        this.snackBar.open(msg, '', {
          duration: 4000, panelClass: ['success-snack'],
        });

        // Recharger pour avoir les écritures mises à jour
        this.chargerFactures();
      },
      error: () => {
        this.reclassementEnCours.set(false);
        this.snackBar.open('Erreur lors du reclassement', 'Fermer', {
          duration: 3000, panelClass: ['error-snack'],
        });
      },
    });
  }

  fermerConfirmation() {
    this.showConfirmationModal.set(false);
    this.factureAConfirmer.set(null);
  }

  // ══════════════════════════════════════════════════
  // DÉTECTION VENTE / ACHAT
  // ══════════════════════════════════════════════════
  estVente(f: FactureCPC): boolean {
    if (!f) return false;
    const op = (f.typeOperation || '').toUpperCase();
    return ['VENTE', 'PRESTATION_CLIENT', 'AVOIR_CLIENT',
      'VENTE_SERVICE', 'VENTE_MARCHANDISE', 'VENTE_TRAVAUX']
      .some(m => op.includes(m));
  }

  // ── Filtres ───────────────────────────────────────
  get facturesFiltrees(): FactureCPC[] {
    let liste = this.toutesFactures();
    const tab = this.tabActif();
    if (tab !== 'TOUS')
      liste = liste.filter(f => f.statut === tab);
    const q = this.recherche().toLowerCase();
    if (q)
      liste = liste.filter(f =>
        f.fournisseur?.toLowerCase().includes(q) ||
        f.numeroFacture?.toLowerCase().includes(q) ||
        f.client?.toLowerCase().includes(q) ||
        f.typeOperation?.toLowerCase().includes(q));
    return liste;
  }

  get countTab(): (tab: Tab) => number {
    return (tab: Tab) => {
      if (tab === 'TOUS') return this.toutesFactures().length;
      return this.toutesFactures().filter(f => f.statut === tab).length;
    };
  }

  // ── Sélection ─────────────────────────────────────
  selectionner(facture: FactureCPC) {
    // Toujours ouvrir le détail — la bannière de confirmation
    // s'affiche dans le panneau si confirmationRequise = true
    this.factureSelectionnee.set(facture);
    this.urlPdfSafe.set(null);

    if (facture.urlTelechargement) {
      this.urlPdfSafe.set(
        this.sanitizer.bypassSecurityTrustResourceUrl(
          facture.urlTelechargement));
      return;
    }
    if (facture.documentId) {
      this.loadingDetail.set(true);
      this.factureService.getUrlPdf(facture.documentId)
        .subscribe({
          next: (doc: any) => {
            if (doc?.urlTelechargement)
              this.urlPdfSafe.set(
                this.sanitizer.bypassSecurityTrustResourceUrl(
                  doc.urlTelechargement));
            this.loadingDetail.set(false);
          },
          error: () => this.loadingDetail.set(false),
        });
    }
  }

  fermerDetail() {
    this.factureSelectionnee.set(null);
    this.urlPdfSafe.set(null);
  }

  // ── Changer statut ────────────────────────────────
  changerStatut(facture: FactureCPC, statut: StatutFacture) {
    this.factureService.changerStatut(facture.id, statut)
      .subscribe({
        next: updated => {
          this.toutesFactures.update(list =>
            list.map(f => f.id === updated.id ? updated : f));
          if (this.factureSelectionnee()?.id === updated.id)
            this.factureSelectionnee.set(updated);
          this.chargerStats();
          this.snackBar.open(`Statut : ${statut}`, '', {
            duration: 3000, panelClass: ['success-snack'],
          });
        },
      });
  }

  // ── Labels / classes statut ───────────────────────
  getStatutLabel(statut?: StatutFacture): string {
    const map: Record<string, string> = {
      NOUVEAU: 'Nouveau', ENREGISTRE: 'Enregistrée',
      APPROUVE: 'Approuvée', REJETE: 'Rejetée',
      PAIEMENT_PARTIEL: 'Paiement partiel', PAYE: 'Payée ✓',
    };
    return map[statut || ''] || 'Nouveau';
  }

  getStatutClass(statut?: StatutFacture): string {
    const map: Record<string, string> = {
      NOUVEAU: 'badge-warning', ENREGISTRE: 'badge-info',
      APPROUVE: 'badge-success', REJETE: 'badge-danger',
      PAIEMENT_PARTIEL: 'badge-partial', PAYE: 'badge-paye',
    };
    return map[statut || ''] || 'badge-warning';
  }

  // ── Modal lien ────────────────────────────────────
  ouvrirModal() {
    this.lienGenere.set('');
    this.clientSelectionne.set(null);
    this.showLienModal.set(true);
  }

  genererLien() {
    const client = this.clientSelectionne();
    const user   = this.currentUser();
    if (!client || !user) return;
    this.lienService.genererLien(
      client.id, client.nomEntreprise || '',
      client.email || '', user.cabinetId || ''
    ).subscribe({
      next: r => {
        this.lienGenere.set(r.url);
        navigator.clipboard.writeText(r.url)
          .then(() => this.snackBar.open('✅ Lien copié !', '',
            { duration: 4000, panelClass: ['success-snack'] }));
      },
      error: () => this.snackBar.open('Erreur génération lien', 'Fermer',
        { duration: 3000, panelClass: ['error-snack'] }),
    });
  }

  copierLien() {
    navigator.clipboard.writeText(this.lienGenere())
      .then(() => this.snackBar.open('✅ Lien copié !', '',
        { duration: 3000, panelClass: ['success-snack'] }));
  }

  // ── Getters détail ────────────────────────────────
  get fraisPortAffiche(): number {
    return this.factureSelectionnee()?.fraisPortHt || 0;
  }

  get confianceAffichee(): number {
    const f = this.factureSelectionnee();
    if (f?.confianceMontants != null && f.confianceMontants > 0)
      return f.confianceMontants;
    return f?.scoreConfiance || 0;
  }

  get ecrituresTypees(): any[] {
    return (this.factureSelectionnee()?.ecritureComptable || []) as any[];
  }


  get schemaEcritures(): { compte: string; libelle: string; sens: string; classe: string }[] {
    const ecritures = this.ecrituresTypees;
    if (!ecritures.length) return [];
    return ecritures.map(e => ({
      compte:  e.compte  || '',
      libelle: e.libelle || '',
      sens:    (e.debit  || 0) > 0 ? 'DÉBIT' : 'CRÉDIT',
      classe:  (e.debit  || 0) > 0 ? 'schema-debit' : 'schema-credit',
    }));
  }





  get itemsTypees(): FactureItem[] {
    const items = this.factureSelectionnee()?.items || [];
    return items.map(item => ({
      description:  item.description,
      quantite:     item.quantite,
      prixUnitaire: item.prixUnitaire  ?? item.prix_unitaire  ?? 0,
      tvaLigne:     item.tvaLigne      ?? item.tva_ligne      ?? 0,
      remiseLigne:  item.remiseLigne   ?? item.remise_ligne   ?? 0,
      totalLigneHt: item.totalLigneHt  ?? item.total_ligne_ht ?? 0,
    }));
  }

  get cpcData(): CpcData | null {
    return this.factureSelectionnee()?.cpc || null;
  }

  get cpcResultats(): CpcResultats | null {
    return this.cpcData?.resultats || null;
  }

  get regleLabel(): string {
    const map: Record<string, string> = {
      'A': 'HT + TVA + Port → TTC', 'B': 'HT + TTC → TVA déduite',
      'C': 'TTC + TVA → HT calculé', 'D': 'TTC seul (exonéré)',
      'coherent': 'Cohérent (3 valeurs lues)', 'calcul_ttc': 'TTC calculé',
      'F_ttc_prioritaire': 'TTC facture prioritaire',
      'ht_only': 'HT seul lu', 'ttc_only': 'TTC seul (exonéré)',
      'conserve': 'Valeurs conservées',
    };
    const r = this.factureSelectionnee()?.regleResolution || '';
    return map[r] || r || '—';
  }

  // ── Helpers ───────────────────────────────────────
  getClientAvatar(nom?: string): string {
    return (nom && nom.length > 0) ? nom[0].toUpperCase() : 'C';
  }

  getAvatarLetter(f: FactureCPC): string {
    if (!f) return 'F';
    const nom = this.estVente(f) ? (f.client || f.fournisseur) : f.fournisseur;
    return (nom && nom.length > 0) ? nom[0].toUpperCase() : 'F';
  }

  formatMontant(val?: number, devise?: string): string {
    if (val == null) return '—';
    if (val === 0)   return `0.00 ${devise || 'MAD'}`;
    return `${val.toFixed(2)} ${devise || 'MAD'}`;
  }

  isEchue(date?: string): boolean {
    if (!date) return false;
    return new Date(date) < new Date();
  }

  formatResultat(val?: number): string {
    if (val == null) return '0.00';
    return `${val >= 0 ? '+' : ''}${val.toFixed(2)}`;
  }


  ajouterLigne() {
    this.ecrituresModifiees.update(list => [
      ...list,
      { num: list.length + 1, compte: '', libelle: '', debit: 0, credit: 0 }
    ]);
  }

// Supprimer une ligne
  supprimerLigne(i: number) {
    this.ecrituresModifiees.update(list => list.filter((_, idx) => idx !== i));
  }



  // ── Modifier écriture ─────────────────────────────
  ouvrirModification() {
    const ecritures = this.factureSelectionnee()?.ecritureComptable || [];
    this.ecrituresModifiees.set(JSON.parse(JSON.stringify(ecritures)));
    this.showModifierModal.set(true);
  }

  modifierLigneEcriture(i: number, champ: string, val: any) {
    this.ecrituresModifiees.update(list => {
      const copy = [...list];
      copy[i] = { ...copy[i], [champ]: val };
      return copy;
    });
  }

  sauvegarderModification() {
    const f = this.factureSelectionnee();
    if (!f) return;
    this.modificationEnCours.set(true);
    this.factureService.modifierEcriture(f.id, {
      ecritureComptable: this.ecrituresModifiees(),
      regenererEcrituresComptables: true,  // ← dire au backend de régénérer
    }).subscribe({
      next: updated => {
        this.toutesFactures.update(list =>
          list.map(x => x.id === updated.id ? updated : x));
        this.factureSelectionnee.set(updated);
        this.showModifierModal.set(false);
        this.modificationEnCours.set(false);
        this.snackBar.open('✅ Écriture modifiée — CPC/Balance mis à jour', '',
          { duration: 3000, panelClass: ['success-snack'] });
      },
      error: () => this.modificationEnCours.set(false),
    });
  }

  // ── Paiement ─────────────────────────────────────
  ouvrirPaiement() {
    const f = this.factureSelectionnee();
    this.erreurPaiement.set('');
    this.paiementForm.set({
      montantPaye:       f?.resteAPayer ?? f?.montantTtc ?? 0,
      modePaiement:      'VIREMENT',
      referenceVirement: '',
    });
    this.showPaiementModal.set(true);
  }

  setPaiementMontant(val: number) {
    this.paiementForm.update(f => ({ ...f, montantPaye: val }));
  }
  setPaiementMode(val: string) {
    this.paiementForm.update(f => ({ ...f, modePaiement: val }));
  }
  setPaiementRef(val: string) {
    this.paiementForm.update(f => ({ ...f, referenceVirement: val }));
  }

  confirmerPaiement() {
    const f    = this.factureSelectionnee();
    if (!f) return;
    const form = this.paiementForm();
    if (form.montantPaye <= 0) {
      this.erreurPaiement.set('Le montant doit être supérieur à 0'); return;
    }
    if (!form.referenceVirement.trim()) {
      this.erreurPaiement.set('La référence du paiement est obligatoire'); return;
    }
    this.erreurPaiement.set('');
    this.factureService.confirmerPaiement(f.id, form).subscribe({
      next: updated => {
        this.toutesFactures.update(list =>
          list.map(x => x.id === updated.id ? updated : x));
        this.factureSelectionnee.set(updated);
        this.chargerStats();
        this.showPaiementModal.set(false);
        const vente = this.estVente(updated);
        if (updated.paiementPartiel) {
          this.snackBar.open(
            `⚠️ ${vente ? 'Encaissement' : 'Paiement'} partiel — Reste : ${updated.resteAPayer?.toFixed(2)} ${f.devise || 'MAD'}`,
            'OK', { duration: 6000, panelClass: ['warning-snack'] });
        } else {
          this.snackBar.open(
            vente ? '✅ Encaissement enregistré !' : '✅ Paiement enregistré !',
            '', { duration: 4000, panelClass: ['success-snack'] });
        }
      },
      error: () => this.snackBar.open('Erreur confirmation paiement', 'Fermer',
        { duration: 3000, panelClass: ['error-snack'] }),
    });
  }

  // ── Export ────────────────────────────────────────
  exporterCsv() {
    const f = this.factureSelectionnee();
    if (!f) return;
    this.factureService.exporterCsv(f.id).subscribe(blob =>
      this._telecharger(blob, `ecriture_${f.numeroFacture || f.id}.csv`));
  }

  exporterExcel() {
    const f = this.factureSelectionnee();
    if (!f) return;
    this.factureService.exporterExcel(f.id).subscribe(blob =>
      this._telecharger(blob, `ecriture_${f.numeroFacture || f.id}.xlsx`));
  }

  private _telecharger(blob: Blob, nom: string) {
    const url = URL.createObjectURL(blob);
    const a   = document.createElement('a');
    a.href = url; a.download = nom; a.click();
    URL.revokeObjectURL(url);
  }
}
