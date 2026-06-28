import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DatePipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { CabinetService } from '../../../core/services/cabinet.service';
import { BilanService } from '../../../core/services/bilan.service';
import { TendanceResponse } from '../../../core/models/bilan.models';
import {ComptableSidebarComponent} from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';

@Component({
  selector: 'app-alertes',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatSnackBarModule,ComptableSidebarComponent],
  templateUrl: './alertes.component.html',
  styleUrl: './alertes.component.scss'
})
export class AlertesComponent implements OnInit {
  private authService = inject(AuthService);
  private cabinetService = inject(CabinetService);
  private bilanService = inject(BilanService);
  private snackBar = inject(MatSnackBar);

  currentUser = this.authService.currentUser;
  alertes = signal<TendanceResponse[]>([]);
  filterNiveau = signal<string>('TOUTES');



  get filteredAlertes() {
    const f = this.filterNiveau();
    return f === 'TOUTES'
      ? this.alertes()
      : this.alertes().filter(a => a.niveau === f);
  }

  get countCritical() { return this.alertes().filter(a => a.niveau === 'CRITICAL').length; }
  get countWarning()  { return this.alertes().filter(a => a.niveau === 'WARNING').length; }
  get countInfo()     { return this.alertes().filter(a => a.niveau === 'INFO').length; }

  ngOnInit() {
    const user = this.authService.currentUser();
    if (!user?.cabinetId) return;

    this.bilanService.getAlertesCabinet(user.cabinetId).subscribe({
      next: (a) => this.alertes.set(a)
    });
  }

  marquerTraite(id: string) {
    this.bilanService.marquerTraite(id).subscribe({
      next: () => {
        this.alertes.update(list => list.filter(a => a.id !== id));
        this.snackBar.open('Alerte marquée comme traitée', '', { duration: 2000 });
      }
    });
  }

  getNiveauClass(n: string) {
    return n === 'CRITICAL' ? 'badge-danger' : n === 'WARNING' ? 'badge-warning' : 'badge-info';
  }

  getNiveauIcon(n: string) {
    return n === 'CRITICAL' ? 'error' : n === 'WARNING' ? 'warning' : 'info';
  }


}
