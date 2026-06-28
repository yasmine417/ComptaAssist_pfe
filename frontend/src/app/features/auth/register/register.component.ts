import { Component, inject, signal } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    ReactiveFormsModule, RouterLink,
    MatButtonModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatSelectModule, MatProgressSpinnerModule, MatSnackBarModule
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);

  loading = signal(false);
  hidePassword = signal(true);

  form = this.fb.group({
    nom: ['', [Validators.required, Validators.minLength(2)]],
    prenom: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: ['DIRECTEUR']
  });

  onSubmit() {
    if (this.form.invalid) return;
    this.loading.set(true);

    this.authService.register(this.form.value as any).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.snackBar.open('Compte créé avec succès !', '', {
          duration: 3000,
          panelClass: ['success-snack']
        });
        switch (res.user.role) {
          case 'ADMIN':     this.router.navigate(['/admin']); break;
          case 'DIRECTEUR': this.router.navigate(['/directeur']); break;
          case 'COMPTABLE': this.router.navigate(['/comptable']); break;
        }
      },
      error: (err) => {
        this.loading.set(false);
        const msg = err?.error?.message || 'Erreur lors de la création du compte';
        this.snackBar.open(msg, 'Fermer', {
          duration: 4000,
          panelClass: ['error-snack']
        });
      }
    });
  }
}
