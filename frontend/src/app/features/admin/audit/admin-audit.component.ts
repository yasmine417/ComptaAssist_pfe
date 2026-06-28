import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../../core/services/auth.service';
import { AdminService, AuditLog } from '../../../core/services/admin.service';

@Component({
  selector: 'app-admin-audit',
  standalone: true,
  imports: [
    CommonModule, RouterLink, FormsModule, DatePipe,
    MatButtonModule, MatIconModule, MatSnackBarModule,
    MatTooltipModule, MatFormFieldModule, MatInputModule
  ],
  templateUrl: './admin-audit.component.html',
  styleUrl:    './admin-audit.component.scss'
})
export class AdminAuditComponent implements OnInit {

  private authService  = inject(AuthService);
  private adminService = inject(AdminService);

  currentUser  = this.authService.currentUser;
  logs         = signal<AuditLog[]>([]);
  loading      = signal(false);
  totalPages   = signal(0);
  currentPage  = signal(0);
  pageSize     = 20;
  searchEmail  = signal('');
  searchAction = signal('');

  actions = [
    'TOUTES',
    // Admin
    'CREER_DIRECTEUR',
    'GENERER_MOT_DE_PASSE',
    'DESACTIVER_COMPTE',
    'REACTIVER_COMPTE',
    'INDEXER_DOCUMENT_RAG',
    // Directeur
    'CREER_CLIENT',
    'MODIFIER_CLIENT',
    'DESACTIVER_CLIENT',
    'AJOUTER_MEMBRE',
    'DESACTIVER_MEMBRE',
    'REACTIVER_MEMBRE',
    'SUPPRIMER_MEMBRE',
    // Comptable
    'CREER_FACTURE',
    'MODIFIER_STATUT_FACTURE',
    'SUPPRIMER_FACTURE',
    // Système
    'LOGIN',
    'LOGOUT',
  ];

  ngOnInit() {
    this.charger();
  }

  charger() {
    this.loading.set(true);

    const email  = this.searchEmail().trim();
    const action = this.searchAction() === 'TOUTES'
      ? '' : this.searchAction();

    let obs;
    if (email) {
      obs = this.adminService.getAuditParEmail(email);
    } else if (action) {
      obs = this.adminService.getAuditParAction(
        action, this.currentPage(), this.pageSize);
    } else {
      obs = this.adminService.getAuditLogs(
        this.currentPage(), this.pageSize);
    }

    obs.subscribe({
      next: (page: any) => {
        this.logs.set(page.content || page);
        this.totalPages.set(page.totalPages || 1);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  rechercher() {
    this.currentPage.set(0);
    this.charger();
  }

  reinitialiser() {
    this.searchEmail.set('');
    this.searchAction.set('TOUTES');
    this.currentPage.set(0);
    this.charger();
  }

  pageSuivante() {
    if (this.currentPage() < this.totalPages() - 1) {
      this.currentPage.update(p => p + 1);
      this.charger();
    }
  }

  pagePrecedente() {
    if (this.currentPage() > 0) {
      this.currentPage.update(p => p - 1);
      this.charger();
    }
  }

  getActionClass(action: string): string {
    if (action?.includes('CREER') ||
      action?.includes('AJOUTER'))    return 'action-create';
    if (action?.includes('SUPPRIMER') ||
      action?.includes('DESACTIVER')) return 'action-delete';
    if (action?.includes('REACTIVER')) return 'action-reactivate';
    if (action?.includes('GENERER') ||
      action?.includes('INDEXER'))    return 'action-generate';
    if (action?.includes('LOGIN'))      return 'action-login';
    return 'action-default';
  }

  getActionIcon(action: string): string {
    if (action?.includes('CREER_CLIENT'))   return 'business';
    if (action?.includes('CREER'))          return 'person_add';
    if (action?.includes('AJOUTER'))        return 'group_add';
    if (action?.includes('SUPPRIMER') ||
      action?.includes('DESACTIVER'))     return 'person_off';
    if (action?.includes('REACTIVER'))      return 'person';
    if (action?.includes('GENERER'))        return 'key';
    if (action?.includes('INDEXER'))        return 'psychology';
    if (action?.includes('LOGIN'))          return 'login';
    return 'info';
  }

  logout() { this.authService.logout(); }
}
