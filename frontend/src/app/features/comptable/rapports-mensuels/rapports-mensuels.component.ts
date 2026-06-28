import { Component, OnInit, inject, signal, effect, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import {
  RapportMensuel,
  RapportMensuelService
} from '../../../core/services/rapport-mensuel.service';
import { ClientContextService } from '../../../core/services/client-context.service';
import {ComptableSidebarComponent} from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';

@Component({
  selector: 'app-rapports-mensuels',
  standalone: true,
  imports: [CommonModule, MatIconModule, ComptableSidebarComponent],
  templateUrl: './rapports-mensuels.component.html',
  styleUrl: './rapports-mensuels.component.scss'
})
export class RapportsMensuelsComponent implements OnInit {

  private rapportService = inject(RapportMensuelService);
  private sanitizer = inject(DomSanitizer);
  clientCtx = inject(ClientContextService);

  rapports = signal<RapportMensuel[]>([]);
  rapportActif = signal<RapportMensuel | null>(null);
  loading = signal(true);

  iframeUrl = signal<SafeResourceUrl | null>(null);

  constructor() {
    effect(() => {
      const client = this.clientCtx.clientActif();
      if (client) {
        this.chargerRapportsDuClient(client.id);
      } else {
        this.rapports.set([]);
        this.rapportActif.set(null);
      }
    });
  }

  ngOnInit() {
    const client = this.clientCtx.clientActif();
    if (client) {
      this.chargerRapportsDuClient(client.id);
    } else {
      this.loading.set(false);
    }
  }

  chargerRapportsDuClient(clientId: string) {
    this.loading.set(true);
    this.rapportActif.set(null);

    this.rapportService.parClient(clientId).subscribe({
      next: (rapports) => {
        this.rapports.set(rapports);
        this.loading.set(false);

        if (rapports.length > 0) {
          this.ouvrirRapport(rapports[0]);
        }
      },
      error: () => this.loading.set(false)
    });
  }

  ouvrirRapport(rapport: RapportMensuel) {
    this.rapportActif.set(rapport);

    if (rapport.contenuHtml) {
      const blob = new Blob([rapport.contenuHtml], { type: 'text/html' });
      const url = URL.createObjectURL(blob);
      this.iframeUrl.set(
        this.sanitizer.bypassSecurityTrustResourceUrl(url)
      );
    } else {
      this.iframeUrl.set(null);
    }
  }

  formatDate(dateStr: string): string {
    const d = new Date(dateStr);
    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  }

  getInitiale(nom: string): string {
    return nom ? nom.charAt(0).toUpperCase() : 'R';
  }
  fermerDetail() {
    this.rapportActif.set(null);
    this.iframeUrl.set(null);
  }

  telecharger(rapport: RapportMensuel, event: Event) {
    event.stopPropagation();

    this.rapportService.telechargerPdf(rapport.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `rapport-${rapport.moisLabel}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        console.error('Erreur téléchargement PDF:', err);
      }
    });
  }
}
