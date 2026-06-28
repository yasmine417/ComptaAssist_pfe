import {
  Component, inject, signal, OnInit, OnDestroy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink }   from '@angular/router';
import { TvaService }   from '../../../core/services/tva.service';
import { AuthService }  from '../../../core/services/auth.service';
import { DeclarationTva } from '../../../core/models/tva.models';

@Component({
  selector:   'app-tva-notification',
  standalone: true,
  imports:    [CommonModule, MatIconModule, RouterLink],
  templateUrl: './tva-notification.component.html',
  styleUrl:    './tva-notification.component.scss',
})
export class TvaNotificationComponent implements OnInit, OnDestroy {

  private tvaService  = inject(TvaService);
  private authService = inject(AuthService);

  notifications = signal<Array<{
    id:           string;
    periodeLabel: string;
    joursRetard:  number;
  }>>([]);

  private fermes    = new Set<string>(
    JSON.parse(sessionStorage.getItem('tva_notifs_fermees') || '[]')
  );
  private intervalId?: ReturnType<typeof setInterval>;

  ngOnInit() {
    this._charger();
    this.intervalId = setInterval(() => this._charger(), 5 * 60 * 1000);
  }

  ngOnDestroy() {
    if (this.intervalId) clearInterval(this.intervalId);
  }

  private _charger() {
    const user = this.authService.currentUser();
    if (!user?.cabinetId) return;

    this.tvaService.getDeclarationsEnRetard(user.cabinetId)
      .subscribe({
        next: decls => {
          const notifs = decls
            .filter(d => !this.fermes.has(d.id))
            .map(d => ({
              id:           d.id,
              periodeLabel: d.periodeLabel,
              joursRetard:  this._joursRetard(d.dateLimite),
            }));
          this.notifications.set(notifs);
        },
        error: () => {},
      });
  }

  fermer(id: string) {
    this.fermes.add(id);
    sessionStorage.setItem(
      'tva_notifs_fermees',
      JSON.stringify([...this.fermes])
    );
    this.notifications.update(n => n.filter(x => x.id !== id));
  }

  private _joursRetard(dateLimite: string): number {
    const diff = Date.now() - new Date(dateLimite).getTime();
    return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24)));
  }
}
