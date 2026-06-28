import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../../core/services/auth.service';
import { CabinetService } from '../../../core/services/cabinet.service';
import { MembreResponse } from '../../../core/models/cabinet.models';

@Component({
  selector: 'app-membres',
  standalone: true,
  imports: [
    RouterLink, RouterLinkActive, ReactiveFormsModule,
    MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatSnackBarModule, MatTooltipModule
  ],
  templateUrl: './membres.component.html',
  styleUrl: './membres.component.scss'
})
export class MembresComponent implements OnInit {
  private authService   = inject(AuthService);
  private cabinetService = inject(CabinetService);
  private fb            = inject(FormBuilder);
  private snackBar      = inject(MatSnackBar);

  currentUser = this.authService.currentUser;
  membres     = signal<MembreResponse[]>([]);
  cabinetId   = signal<string>('');
  showForm    = signal(false);
  loading     = signal(false);

  navItems = [
    { icon: 'dashboard',      label: 'Tableau de bord', route: '/directeur' },
    { icon: 'people',         label: 'Mon équipe',       route: '/directeur/membres' },
    { icon: 'folder_shared',  label: 'Mes clients',      route: '/directeur/clients' },
    { icon: 'summarize',      label: 'Rapports',         route: '/directeur/rapports' },
  ];

  // Formulaire adapté au backend — nom/prenom/email/role
  form = this.fb.group({
    nom:    ['', [Validators.required, Validators.minLength(2)]],
    prenom: ['', [Validators.required, Validators.minLength(2)]],
    email:  ['', [Validators.required, Validators.email]],
    role:   ['COMPTABLE']
  });

  ngOnInit() {
    this.cabinetService.getMonCabinet().subscribe({
      next: (cab) => {
        this.cabinetId.set(cab.id);
        this.cabinetService.listerMembres(cab.id).subscribe({
          next: (m) => this.membres.set(m)
        });
      }
    });
  }

  ajouter() {
    if (this.form.invalid || !this.cabinetId()) return;
    this.loading.set(true);

    this.cabinetService.ajouterMembre(
      this.cabinetId(), this.form.value as any
    ).subscribe({
      next: (m) => {
        this.membres.update(list => [m, ...list]);
        this.showForm.set(false);
        this.form.reset({ role: 'COMPTABLE' });
        this.loading.set(false);
        this.snackBar.open(
          `✅ Compte créé pour ${m.prenom} ${m.nom} — ` +
          `L'administrateur va générer et envoyer le mot de passe.`,
          '',
          { duration: 6000, panelClass: ['success-snack'] }
        );
      },
      error: (err) => {
        this.loading.set(false);
        this.snackBar.open(
          err?.error?.erreur || 'Erreur',
          'Fermer',
          { duration: 4000, panelClass: ['error-snack'] }
        );
      }
    });
  }






  desactiver(id: string) {
    if (!confirm('Désactiver ce comptable ?')) return;

    this.cabinetService.desactiverMembre(
      this.cabinetId(), id
    ).subscribe({
      next: () => {
        this.membres.update(list =>
          list.map(m =>
            m.id === id ? { ...m, actif: false } : m
          )
        );
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

  supprimer(id: string, nom: string) {
    if (!confirm(
      `Supprimer définitivement ${nom} ? ` +
      `Cette action est irréversible.`
    )) return;

    this.cabinetService.supprimerMembre(
      this.cabinetId(), id
    ).subscribe({
      next: () => {
        this.membres.update(
          list => list.filter(m => m.id !== id));
        this.snackBar.open(
          '🗑️ Membre supprimé définitivement', '',
          { duration: 3000 });
      },
      error: () => {
        this.snackBar.open(
          'Erreur suppression', 'Fermer',
          { duration: 3000,
            panelClass: ['error-snack'] });
      }
    });
  }

  reactiver(id: string) {
    this.cabinetService.reactiverMembre(
      this.cabinetId(), id
    ).subscribe({
      next: () => {
        this.membres.update(list =>
          list.map(m =>
            m.id === id ? { ...m, actif: true } : m
          )
        );
        this.snackBar.open(
          '✅ Compte réactivé', '',
          { duration: 3000 });
      }
    });
  }



  logout() { this.authService.logout(); }
}
