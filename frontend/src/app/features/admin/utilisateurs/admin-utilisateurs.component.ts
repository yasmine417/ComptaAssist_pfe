import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../../core/services/auth.service';
import {
  AdminService,
  UserAdmin,
  CreateDirecteurRequest
} from '../../../core/services/admin.service';

@Component({
  selector: 'app-admin-utilisateurs',
  standalone: true,
  imports: [
    CommonModule, RouterLink, ReactiveFormsModule,
    MatButtonModule, MatIconModule, MatSnackBarModule,
    MatTooltipModule, MatProgressSpinnerModule,
    MatFormFieldModule, MatInputModule
  ],
  templateUrl: './admin-utilisateurs.component.html',
  styleUrl:    './admin-utilisateurs.component.scss'
})
export class AdminUtilisateursComponent implements OnInit {

  private authService  = inject(AuthService);
  private adminService = inject(AdminService);
  private snackBar     = inject(MatSnackBar);
  private fb           = inject(FormBuilder);

  currentUser  = this.authService.currentUser;
  loading      = signal(false);
  showForm     = signal(false);
  activeTab    = signal<'directeurs' | 'attente' | 'tous'>(
    'directeurs');

  directeurs   = signal<UserAdmin[]>([]);
  enAttente    = signal<UserAdmin[]>([]);
  tousUsers    = signal<UserAdmin[]>([]);
  generatingId = signal<string>('');

  form = this.fb.group({
    nom:    ['', [Validators.required, Validators.minLength(2)]],
    prenom: ['', [Validators.required, Validators.minLength(2)]],
    email:  ['', [Validators.required, Validators.email]]
  });

  ngOnInit() {
    this.chargerTout();
  }

  chargerTout() {
    this.loading.set(true);

    this.adminService.getDirecteurs().subscribe({
      next: (d) => this.directeurs.set(d),
      error: () => {}
    });

    this.adminService.getEnAttente().subscribe({
      next: (e) => this.enAttente.set(e),
      error: () => {}
    });

    this.adminService.getTousUtilisateurs().subscribe({
      next: (u) => {
        this.tousUsers.set(u);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  creerDirecteur() {
    if (this.form.invalid) return;
    this.loading.set(true);

    this.adminService.creerDirecteur(
      this.form.value as CreateDirecteurRequest
    ).subscribe({
      next: (d) => {
        this.directeurs.update(list => [d, ...list]);
        this.showForm.set(false);
        this.form.reset();
        this.loading.set(false);
        this.snackBar.open(
          `✅ Directeur créé ! Email envoyé à ${d.email}`,
          '', { duration: 5000,
            panelClass: ['success-snack'] });
      },
      error: (err) => {
        this.loading.set(false);
        this.snackBar.open(
          err?.error?.erreur || 'Erreur création',
          'Fermer',
          { duration: 4000,
            panelClass: ['error-snack'] });
      }
    });
  }

  genererMdp(user: UserAdmin) {
    this.generatingId.set(user.id);

    this.adminService.genererMotDePasse(user.id).subscribe({
      next: (u) => {
        this.generatingId.set('');
        // Retirer de la liste EN_ATTENTE
        this.enAttente.update(
          list => list.filter(x => x.id !== user.id));
        // Mettre à jour dans tousUsers
        this.tousUsers.update(list =>
          list.map(x => x.id === u.id ? u : x));
        this.snackBar.open(
          `✅ Mot de passe envoyé à ${u.email}`,
          '', { duration: 5000,
            panelClass: ['success-snack'] });
      },
      error: () => {
        this.generatingId.set('');
        this.snackBar.open(
          'Erreur génération mot de passe',
          'Fermer',
          { duration: 3000,
            panelClass: ['error-snack'] });
      }
    });
  }

  desactiver(user: UserAdmin) {
    if (!confirm(
      `Désactiver le compte de ${user.prenom} ${user.nom} ?`
    )) return;

    this.adminService.desactiver(user.id).subscribe({
      next: () => {
        // Recharger tout depuis le backend
        this.chargerTout();
        this.snackBar.open(
          'Compte désactivé', '',
          { duration: 3000 });
      },
      error: () => {
        this.snackBar.open(
          'Erreur désactivation', 'Fermer',
          { duration: 3000,
            panelClass: ['error-snack'] });
      }
    });
  }

  reactiver(user: UserAdmin) {
    this.adminService.reactiver(user.id).subscribe({
      next: () => {
        this.chargerTout();
        this.snackBar.open(
          '✅ Compte réactivé', '',
          { duration: 3000 });
      }
    });
  }

  getStatutClass(statut: string): string {
    if (statut === 'ACTIF')      return 'badge-success';
    if (statut === 'EN_ATTENTE') return 'badge-warning';
    return 'badge-danger';
  }

  logout() { this.authService.logout(); }
}
