import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { AuthService } from '../../../core/services/auth.service';
import { RapportNotificationService, RapportNotification } from '../../../core/services/rapport-notification.service';

@Component({
  selector: 'app-rapport-toast',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rapport-toast.component.html',
})
export class RapportToastComponent implements OnInit, OnDestroy {

  private authService   = inject(AuthService);
  private notifService  = inject(RapportNotificationService);

  toastsVisibles: RapportNotification[] = [];
  private sub!: Subscription;
  private comptableId = '';

  ngOnInit() {
    const user = this.authService.currentUser();
    this.comptableId = user?.id || '';
    if (!this.comptableId) return;

    // Charger au démarrage
    this.chargerNotifs();

    // Vérifier toutes les 30 secondes
    this.sub = interval(30000).pipe(
      switchMap(() => this.notifService.getNotifs(this.comptableId))
    ).subscribe(notifs => this.afficherNonLues(notifs));
  }

  chargerNotifs() {
    this.notifService.getNotifs(this.comptableId).subscribe(notifs => {
      this.afficherNonLues(notifs);
    });
  }

  afficherNonLues(notifs: RapportNotification[]) {
    const nonLues = notifs.filter(n =>
      !n.lu && !this.toastsVisibles.find(t => t.id === n.id)
    );

    // Afficher chaque toast avec un délai pour qu'ils soient visibles en même temps
    nonLues.forEach((n, index) => {
      setTimeout(() => {
        this.toastsVisibles.push(n);
        // Fermer après 8 secondes
        setTimeout(() => this.fermer(n), 8000);
      }, index * 500); // 500ms de délai entre chaque toast
    });
  }

  fermer(toast: RapportNotification) {
    this.notifService.marquerLu(toast.id).subscribe();
    this.toastsVisibles = this.toastsVisibles.filter(t => t.id !== toast.id);
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }
}
