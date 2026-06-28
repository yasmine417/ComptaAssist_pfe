import {
  Component, OnInit, ViewChild, ElementRef,
  inject, signal, effect, computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HttpClient, HttpHeaders } from '@angular/common/http';

import {
  SignatureElectroniqueService, SignatureElectronique
} from '../../../core/services/signature-electronique.service';
import { AuthService }          from '../../../core/services/auth.service';
import { ClientContextService } from '../../../core/services/client-context.service';
import { ComptableSidebarComponent } from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';

interface SignatureAvecLien extends SignatureElectronique {
  lienStatut?: string;
  urlPdfSigne?: string;
  signedAtClient?: string;
}

interface Article {
  id: number;
  titre: string;
  contenu: string;
  fixe: boolean; // true = article de base non supprimable
}

@Component({
  selector: 'app-signatures',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatIconModule, MatButtonModule,
    MatTooltipModule, MatProgressBarModule, ComptableSidebarComponent, MatSnackBarModule
  ],
  templateUrl: './signatures.component.html',
  styleUrl: './signatures.component.scss'
})
export class SignaturesComponent implements OnInit {

  @ViewChild('canvasComptable')
  canvasRef!: ElementRef<HTMLCanvasElement>;
  private ctx!: CanvasRenderingContext2D;
  private isDrawing = false;

  private sigService  = inject(SignatureElectroniqueService);
  private authService = inject(AuthService);
  private clientCtx   = inject(ClientContextService);
  private snackBar    = inject(MatSnackBar);
  private http        = inject(HttpClient);

  signatures   = signal<SignatureAvecLien[]>([]);
  loading      = signal(false);
  envoi        = signal(false);
  showModal    = signal(false);
  typeChoisi   = signal<string>('');

  // ── Pagination historique ──────────────────────────
  pageSize     = 6;
  currentPage  = signal(1);

  signaturesPage = computed(() => {
    const all   = this.signatures();
    const start = (this.currentPage() - 1) * this.pageSize;
    return all.slice(start, start + this.pageSize);
  });

  totalPages = computed(() =>
    Math.max(1, Math.ceil(this.signatures().length / this.pageSize))
  );

  showSignerModal  = signal(false);
  signatureEnCours = signal<SignatureElectronique | null>(null);
  hasDessin        = signal(false);
  envoiSig         = signal(false);

  // ── Articles Lettre de Mission ─────────────────────
  articlesLM = signal<Article[]>([
    {
      id: 1, fixe: true,
      titre: 'Objet de la mission',
      contenu: 'Dans le cadre de la présente lettre de mission, ' +
        'le Prestataire s\'engage à assurer pour le compte du ' +
        'Client les prestations comptables et fiscales suivantes :'
    },
    {
      id: 2, fixe: true,
      titre: 'Honoraires',
      contenu: 'Les honoraires sont payables le 5 de chaque mois ' +
        'par virement bancaire. Toute modification fera l\'objet ' +
        'd\'un avenant signé par les deux parties.'
    },
    {
      id: 3, fixe: true,
      titre: 'Durée',
      contenu: 'La présente lettre de mission est conclue pour une ' +
        'durée d\'un (1) an, renouvelable tacitement par périodes ' +
        'annuelles. Chaque partie peut y mettre fin moyennant un ' +
        'préavis de 30 jours par lettre recommandée.'
    },
    {
      id: 4, fixe: true,
      titre: 'Obligations des parties',
      contenu: 'Le Client s\'engage à fournir au Prestataire tous ' +
        'les documents comptables et justificatifs nécessaires dans ' +
        'les délais convenus. Le Prestataire s\'engage à respecter ' +
        'le secret professionnel.'
    }
  ]);

  // ── Articles Mandat TVA ────────────────────────────
  articlesMTV = signal<Article[]>([
    {
      id: 1, fixe: true,
      titre: 'Objet du Mandat',
      contenu: 'Par la présente, le Mandant autorise expressément ' +
        'le Mandataire à établir et déposer les déclarations de TVA.'
    },
    {
      id: 2, fixe: true,
      titre: 'Engagements des parties',
      contenu: 'Le Mandant s\'engage à communiquer au Mandataire ' +
        'toutes les pièces justificatives dans un délai minimum ' +
        'de 7 jours avant la date limite de dépôt.'
    },
    {
      id: 3, fixe: true,
      titre: 'Durée et révocation',
      contenu: 'Le présent mandat est valable pour l\'exercice ' +
        'fiscal en cours et reconductible tacitement. Il peut être ' +
        'révoqué à tout moment par lettre recommandée.'
    }
  ]);

  // ── Formulaires ────────────────────────────────────
  formLettre = {
    honoraires:      '',
    jourPaiement:    '5',
    dateDebut:       new Date().toISOString().split('T')[0],
    tenue_comptable: true,
    declaration_tva: true,
    declaration_is:  true,
    declaration_ir:  false,
    bilan_annuel:    true,
    conseil_fiscal:  false,
    audit_interne:   false,
    services_custom: '',
    cabinetNom:      '',
    comptableNom:    '',
    cabinetEmail:    '',
  };

  formMandat = {
    periodicite:  'trimestrielle',
    cabinetNom:   '',
    comptableNom: '',
    cabinetEmail: '',
    clientIf:     '',
  };

  typesDocs = [
    {
      key: 'LETTRE_MISSION',
      label: 'Lettre de mission',
      desc: 'Contrat honoraires comptable ↔ client',
      icon: 'description', color: '#4338ca'
    },
    {
      key: 'MANDAT_TVA',
      label: 'Mandat TVA',
      desc: 'Autorisation dépôt déclarations TVA',
      icon: 'receipt', color: '#b45309'
    }
  ];

  ngOnInit() {
    this.remplirInfosCabinet();
  }

  constructor() {
    // Recharge l'historique chaque fois que le client actif change
    // (sélection initiale ET changement depuis la sidebar)
    effect(() => {
      const client = this.clientCtx.clientActif();
      this.currentPage.set(1);
      if (client) {
        this.charger();
      } else {
        this.signatures.set([]);
      }
    });

    // Évite de rester bloqué sur une page qui n'existe plus
    // (ex: après révocation d'un document)
    effect(() => {
      const total = this.totalPages();
      if (this.currentPage() > total) {
        this.currentPage.set(total);
      }
    });
  }

  // ── Navigation pagination ──────────────────────────
  pagePrecedente() {
    if (this.currentPage() > 1) {
      this.currentPage.set(this.currentPage() - 1);
    }
  }

  pageSuivante() {
    if (this.currentPage() < this.totalPages()) {
      this.currentPage.set(this.currentPage() + 1);
    }
  }

  remplirInfosCabinet() {
    const user = this.authService.currentUser();
    if (!user) return;

    // Infos comptable
    this.formLettre.comptableNom =
      `${user.prenom || ''} ${user.nom || ''}`.trim();
    this.formLettre.cabinetEmail = user.email || '';
    this.formMandat.comptableNom = this.formLettre.comptableNom;
    this.formMandat.cabinetEmail = this.formLettre.cabinetEmail;

    // ← Récupérer le vrai nom du cabinet depuis l'API
    if (user.cabinetId) {
      const token = this.authService.getToken();
      this.http.get<any>(
        `http://localhost:8082/api/cabinets/${user.cabinetId}`,
        { headers: { Authorization: `Bearer ${token}` } }
      ).subscribe({
        next: (cabinet) => {
          const nomCabinet = cabinet.nom || 'Cabinet';
          this.formLettre.cabinetNom = nomCabinet;
          this.formMandat.cabinetNom = nomCabinet;
        },
        error: () => {
          this.formLettre.cabinetNom = 'Cabinet';
          this.formMandat.cabinetNom = 'Cabinet';
        }
      });
    }
  }
  // ── Gestion articles LM ────────────────────────────
  updateArticleLM(
    id: number,
    field: 'titre' | 'contenu',
    value: string) {
    this.articlesLM.set(
      this.articlesLM().map(a =>
        a.id === id ? { ...a, [field]: value } : a
      )
    );
  }

  ajouterArticleLM() {
    const arts = this.articlesLM();
    const newId = Math.max(...arts.map(a => a.id)) + 1;
    this.articlesLM.set([
      ...arts,
      { id: newId, titre: '', contenu: '', fixe: false }
    ]);
  }


  appliquerFormat(
    id: number,
    format: 'bold' | 'italic' | 'underline',
    type: 'LM' | 'MTV') {

    const textareaId = `textarea-${type}-${id}`;
    const textarea = document.getElementById(textareaId) as HTMLTextAreaElement;
    if (!textarea) return;

    const start = textarea.selectionStart ?? 0;
    const end   = textarea.selectionEnd   ?? 0;
    const sel   = textarea.value.substring(start, end);
    if (!sel) return;

    const tags: Record<string, [string, string]> = {
      bold:      ['<strong>', '</strong>'],
      italic:    ['<em>',     '</em>'],
      underline: ['<u>',      '</u>']
    };
    const [open, close] = tags[format];
    const newVal =
      textarea.value.substring(0, start) +
      open + sel + close +
      textarea.value.substring(end);

    textarea.value = newVal;

    if (type === 'LM') {
      this.updateArticleLM(id, 'contenu', newVal);
    } else {
      this.updateArticleMTV(id, 'contenu', newVal);
    }
  }

  supprimerArticleLM(id: number) {
    this.articlesLM.set(
      this.articlesLM().filter(a => a.id !== id)
    );
  }

  // ── Gestion articles MTV ───────────────────────────
  updateArticleMTV(
    id: number,
    field: 'titre' | 'contenu',
    value: string) {
    this.articlesMTV.set(
      this.articlesMTV().map(a =>
        a.id === id ? { ...a, [field]: value } : a
      )
    );
  }

  ajouterArticleMTV() {
    const arts = this.articlesMTV();
    const newId = Math.max(...arts.map(a => a.id)) + 1;
    this.articlesMTV.set([
      ...arts,
      { id: newId, titre: '', contenu: '', fixe: false }
    ]);
  }

  supprimerArticleMTV(id: number) {
    this.articlesMTV.set(
      this.articlesMTV().filter(a => a.id !== id)
    );
  }

  // ── Charger / Modal ────────────────────────────────
  charger() {
    const client = this.clientCtx.clientActif();
    if (!client) return;
    this.loading.set(true);
    this.sigService.listerParClient(client.id).subscribe({
      next: sigs => {
        const sigsAvecLien: SignatureAvecLien[] = sigs;
        let completed = 0;
        if (sigs.length === 0) {
          this.signatures.set([]);
          this.loading.set(false);
          return;
        }
        sigs.forEach((sig, i) => {
          this.chargerLien(sig.id).then(lienInfo => {
            sigsAvecLien[i] = { ...sig, ...lienInfo };
            completed++;
            if (completed === sigs.length) {
              this.signatures.set([...sigsAvecLien]);
              this.loading.set(false);
            }
          });
        });
      },
      error: () => this.loading.set(false)
    });
  }

  private chargerLien(
    sigId: string): Promise<Partial<SignatureAvecLien>> {
    const token = this.authService.getToken();
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
    return this.http.get<any>(
      `http://localhost:8082/api/signatures/${sigId}/lien`,
      { headers }
    ).toPromise().then(data => ({
      lienStatut:     data?.statut        || '',
      urlPdfSigne:    data?.urlPdfSigne   || '',
      signedAtClient: data?.signedAtClient || '',
    })).catch(() => ({}));
  }

  ouvrirModal(type: string) {
    this.typeChoisi.set(type);
    this.showModal.set(true);
  }

  envoyer() {
    const client = this.clientCtx.clientActif();
    const user   = this.authService.currentUser();
    if (!client || !user) return;

    if (!client.email) {
      this.snackBar.open(
        '❌ Ce client n\'a pas d\'email', '', { duration: 3000 });
      return;
    }
    if (this.typeChoisi() === 'LETTRE_MISSION'
      && !this.formLettre.honoraires) {
      this.snackBar.open(
        '❌ Veuillez saisir les honoraires', '', { duration: 3000 });
      return;
    }

    this.envoi.set(true);
    const formData = this.typeChoisi() === 'LETTRE_MISSION'
      ? this.buildFormDataLettre(client)
      : this.buildFormDataMandat(client);

    this.sigService.envoyer({
      cabinetId:    user.cabinetId!,
      clientId:     client.id,
      clientEmail:  client.email,
      clientNom:    client.nomEntreprise,
      typeDocument: this.typeChoisi(),
      formData
    }).subscribe({
      next: sig => {
        this.showModal.set(false);
        this.envoi.set(false);
        this.snackBar.open(
          '✅ Document créé ! Signez-le maintenant.',
          '', { duration: 5000 });
        this.ouvrirSignerModal(sig);
        this.charger();
      },
      error: err => {
        this.envoi.set(false);
        this.snackBar.open(
          '❌ ' + (err.error?.message || 'Erreur'),
          '', { duration: 4000 });
      }
    });
  }

  // ── Signature comptable ────────────────────────────
  ouvrirSignerModal(sig: SignatureElectronique) {
    this.signatureEnCours.set(sig);
    this.showSignerModal.set(true);
    setTimeout(() => this.initCanvas(), 200);
  }

  initCanvas() {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;
    this.ctx = canvas.getContext('2d')!;
    this.ctx.strokeStyle = '#16213a';
    this.ctx.lineWidth   = 2.5;
    this.ctx.lineCap     = 'round';
    this.ctx.lineJoin    = 'round';
    this.dessinerLigneGuide();
    canvas.addEventListener('mousedown',
      e => this.startDraw(e));
    canvas.addEventListener('mousemove',
      e => this.draw(e));
    canvas.addEventListener('mouseup',
      () => this.stopDraw());
    canvas.addEventListener('mouseleave',
      () => this.stopDraw());
    canvas.addEventListener('touchstart',
      e => this.startDrawTouch(e), { passive: false });
    canvas.addEventListener('touchmove',
      e => this.drawTouch(e),      { passive: false });
    canvas.addEventListener('touchend',
      () => this.stopDraw());
  }

  // Ligne de base discrète façon "signez ici" sur un document papier
  private dessinerLigneGuide() {
    if (!this.ctx) return;
    const canvas = this.canvasRef.nativeElement;
    this.ctx.save();
    this.ctx.strokeStyle = '#cbd5e1';
    this.ctx.lineWidth = 1;
    this.ctx.setLineDash([4, 4]);
    this.ctx.beginPath();
    this.ctx.moveTo(24, canvas.height - 28);
    this.ctx.lineTo(canvas.width - 24, canvas.height - 28);
    this.ctx.stroke();
    this.ctx.restore();
  }

  startDraw(e: MouseEvent) {
    this.isDrawing = true;
    const r = this.canvasRef.nativeElement.getBoundingClientRect();
    this.ctx.beginPath();
    this.ctx.moveTo(e.clientX - r.left, e.clientY - r.top);
  }
  draw(e: MouseEvent) {
    if (!this.isDrawing) return;
    const r = this.canvasRef.nativeElement.getBoundingClientRect();
    this.ctx.lineTo(e.clientX - r.left, e.clientY - r.top);
    this.ctx.stroke();
    this.hasDessin.set(true);
  }
  startDrawTouch(e: TouchEvent) {
    e.preventDefault();
    this.isDrawing = true;
    const r = this.canvasRef.nativeElement.getBoundingClientRect();
    const t = e.touches[0];
    this.ctx.beginPath();
    this.ctx.moveTo(t.clientX - r.left, t.clientY - r.top);
  }
  drawTouch(e: TouchEvent) {
    e.preventDefault();
    if (!this.isDrawing) return;
    const r = this.canvasRef.nativeElement.getBoundingClientRect();
    const t = e.touches[0];
    this.ctx.lineTo(t.clientX - r.left, t.clientY - r.top);
    this.ctx.stroke();
    this.hasDessin.set(true);
  }
  stopDraw() { this.isDrawing = false; }

  effacerCanvas() {
    const canvas = this.canvasRef.nativeElement;
    this.ctx.clearRect(0, 0, canvas.width, canvas.height);
    this.dessinerLigneGuide();
    this.hasDessin.set(false);
  }

  confirmerSignatureComptable() {
    const sig = this.signatureEnCours();
    if (!sig || !this.hasDessin()) return;
    const user      = this.authService.currentUser();
    const signature = this.canvasRef.nativeElement
      .toDataURL('image/png');
    const comptableNom =
      `${user?.prenom || ''} ${user?.nom || ''}`.trim();
    this.envoiSig.set(true);
    this.sigService.signerComptable(
      sig.id, signature, comptableNom).subscribe({
      next: () => {
        this.showSignerModal.set(false);
        this.envoiSig.set(false);
        this.hasDessin.set(false);
        this.snackBar.open(
          '✅ Signé ! Email envoyé au client.',
          '', { duration: 5000 });
        this.charger();
      },
      error: err => {
        this.envoiSig.set(false);
        this.snackBar.open(
          '❌ ' + (err.error?.message || 'Erreur'),
          '', { duration: 4000 });
      }
    });
  }

  voirPdfSigne(sig: SignatureAvecLien) {
    if (sig.urlPdfSigne) window.open(sig.urlPdfSigne, '_blank');
  }

  synchroniser(sig: SignatureElectronique) {
    this.charger();
    this.snackBar.open('Statut mis à jour', '', { duration: 2000 });
  }

  revoquer(sig: SignatureElectronique) {
    if (!confirm('Révoquer ce document ?')) return;
    this.sigService.revoquer(sig.id).subscribe({
      next: () => this.charger()
    });
  }

  // ── Build formData ─────────────────────────────────
  private buildFormDataLettre(
    client: any): Record<string, string> {
    const data: Record<string, string> = {
      honoraires:      this.formLettre.honoraires,
      jourPaiement:    this.formLettre.jourPaiement,
      dateDebut:       this.formatDateFr(this.formLettre.dateDebut),
      tenue_comptable: String(this.formLettre.tenue_comptable),
      declaration_tva: String(this.formLettre.declaration_tva),
      declaration_is:  String(this.formLettre.declaration_is),
      declaration_ir:  String(this.formLettre.declaration_ir),
      bilan_annuel:    String(this.formLettre.bilan_annuel),
      conseil_fiscal:  String(this.formLettre.conseil_fiscal),
      audit_interne:   String(this.formLettre.audit_interne),
      services_custom: this.formLettre.services_custom || '',
      cabinetNom:      this.formLettre.cabinetNom,
      comptableNom:    this.formLettre.comptableNom,
      cabinetEmail:    this.formLettre.cabinetEmail,
      clientNom:       client.nomEntreprise,
      clientIce:       client.ice || '—',
      clientEmail:     client.email,
    };

    // Ajouter les articles
    this.articlesLM().forEach(a => {
      if (a.id <= 4) {
        data[`article${a.id}_contenu`] = a.contenu;
      } else {
        data[`article${a.id}_titre`]   = a.titre;
        data[`article${a.id}_contenu`] = a.contenu;
      }
    });

    return data;
  }

  private buildFormDataMandat(
    client: any): Record<string, string> {
    const data: Record<string, string> = {
      periodicite:  this.formMandat.periodicite,
      cabinetNom:   this.formMandat.cabinetNom,
      comptableNom: this.formMandat.comptableNom,
      cabinetEmail: this.formMandat.cabinetEmail,
      clientNom:    client.nomEntreprise,
      clientIce:    client.ice || '—',
      clientIf:     this.formMandat.clientIf,
      clientEmail:  client.email,
    };

    // Ajouter les articles
    this.articlesMTV().forEach(a => {
      if (a.id <= 3) {
        data[`article${a.id}_contenu`] = a.contenu;
      } else {
        data[`article${a.id}_titre`]   = a.titre;
        data[`article${a.id}_contenu`] = a.contenu;
      }
    });

    return data;
  }

  private formatDateFr(d: string): string {
    if (!d) return '';
    const [y, m, day] = d.split('-');
    return `${day}/${m}/${y}`;
  }

  // ── Labels / helpers ───────────────────────────────
  getStatutLabel(sig: SignatureAvecLien): string {
    const l = sig.lienStatut;
    if (l === 'SIGNE')               return 'Signé ✓';
    if (l === 'EN_ATTENTE_CLIENT')    return 'En attente client';
    if (l === 'EN_ATTENTE_COMPTABLE') return 'À signer';
    if (sig.statut === 'REFUSE')      return 'Refusé';
    if (sig.statut === 'EXPIRE')      return 'Expiré';
    return sig.statut;
  }

  getStatutClass(sig: SignatureAvecLien): string {
    const l = sig.lienStatut;
    if (l === 'SIGNE')               return 'statut-signe';
    if (l === 'EN_ATTENTE_CLIENT')    return 'statut-vu';
    if (l === 'EN_ATTENTE_COMPTABLE') return 'statut-envoye';
    return 'statut-expire';
  }

  getTypeLabel(t: string) {
    return this.typesDocs.find(d => d.key === t)?.label || t;
  }
  getTypeIcon(t: string) {
    return this.typesDocs.find(d => d.key === t)?.icon
      || 'description';
  }
  getTypeColor(t: string) {
    return this.typesDocs.find(d => d.key === t)?.color
      || '#4338ca';
  }
  typeChoisiInfo() {
    return this.typesDocs.find(t => t.key === this.typeChoisi());
  }
  get clientActif() { return this.clientCtx.clientActif(); }
}
