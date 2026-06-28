import { Component, inject, signal } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule, RouterLink,
    MatButtonModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatProgressSpinnerModule, MatSnackBarModule
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);

  loading = signal(false);
  hidePassword = signal(true);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  onSubmit() {
    if (this.form.invalid) return;
    this.loading.set(true);

    this.authService.login(this.form.value as any).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.redirectByRole(res.user.role);
      },
      error: (err) => {
        this.loading.set(false);
        const msg = err?.error?.message || 'Email ou mot de passe incorrect';
        this.snackBar.open(msg, 'Fermer', {
          duration: 4000,
          panelClass: ['error-snack']
        });
      }
    });
  }

  private redirectByRole(role: string) {
    switch (role) {
      case 'ADMIN':      this.router.navigate(['/admin']); break;
      case 'DIRECTEUR':  this.router.navigate(['/directeur']); break;
      case 'COMPTABLE':  this.router.navigate(['/comptable']); break;
      default:           this.router.navigate(['/']);
    }
  }
}
