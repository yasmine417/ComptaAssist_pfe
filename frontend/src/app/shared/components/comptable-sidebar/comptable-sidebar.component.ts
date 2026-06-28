import {
  Component, inject, signal, OnInit
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule }              from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule }             from '@angular/material/icon';
import { MatTooltipModule }          from '@angular/material/tooltip';

import { AuthService }               from '../../../core/services/auth.service';
import { CabinetService }            from '../../../core/services/cabinet.service';
import { ClientContextService }      from '../../../core/services/client-context.service';
import { ClientResponse }            from '../../../core/models/cabinet.models';
import { TvaNotificationComponent }  from '../../../features/comptable/tva-notification/tva-notification.component';
import { RapportToastComponent } from '../../../features/comptable/rapport/rapport-toast.component';
@Component({
  selector:    'app-comptable-sidebar',
  standalone:  true,
  imports: [
    CommonModule, RouterLink, RouterLinkActive,
    MatIconModule, MatTooltipModule,FormsModule,
    TvaNotificationComponent,RapportToastComponent
  ],
  templateUrl: './comptable-sidebar.component.html',
  styleUrl:    './comptable-sidebar.component.scss',
})
export class ComptableSidebarComponent implements OnInit {

  private authService    = inject(AuthService);
  private cabinetService = inject(CabinetService);
  clientCtx              = inject(ClientContextService);

  currentUser = this.authService.currentUser;
  loading     = signal(false);

  navItems = [
    { icon: 'dashboard',              label: 'Tableau de bord', route: '/comptable' },
    { icon: 'draw', label: 'Signatures', route: '/comptable/signatures' },
    { icon: 'receipt_long',           label: 'Factures CPC',    route: '/comptable/factures-cpc' },
    { icon: 'bar_chart',              label: 'CPC & Journal',   route: '/comptable/cpc' },
    { icon: 'account_balance', label: 'Bilan comptable', route: '/comptable/bilanC' },
    { icon: 'account_balance_wallet', label: 'Trésorerie',      route: '/comptable/tresorerie' },
    { icon: 'receipt',                label: 'Déclaration TVA', route: '/comptable/tva' },
    { icon: 'chat',                   label: 'Messagerie',       route: '/comptable/chat1' },       // ← nouveau

    { icon: 'smart_toy',              label: 'Assistant IA',    route: '/comptable/chat' },
    { icon: 'description',            label: 'Rapports mensuels', route: '/comptable/rapports-mensuels' }, // ← ajouté
      ];

  ngOnInit() {
    const user = this.currentUser();
    if (!user?.cabinetId) return;
    this.loading.set(true);
    this.cabinetService.mesClients(user.cabinetId).subscribe({
      next:  c => { this.clientCtx.setClients(c); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  // ── Gestion du changement de select ───────────────────────────
  onSelectChange(id: string) {
    const client = this.clientCtx.clients().find(c => c.id === id);
    if (client) {
      this.clientCtx.setClientActif(client);
    }
  }

  getAvatar(nom?: string): string {
    return nom ? nom[0].toUpperCase() : 'C';
  }

  deconnecter() {
    this.authService.logout();
  }
}
