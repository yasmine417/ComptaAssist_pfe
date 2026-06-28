import {
  Component, inject, signal,
  ElementRef, ViewChild, AfterViewChecked, OnInit
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';
import { FiscalService } from '../../../core/services/fiscal.service';
import { ComptableSidebarComponent } from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';

interface Message {
  id:        number;
  role:      'user' | 'assistant';
  content:   string;
  sources?:  string[];
  timestamp: Date;
}

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    ComptableSidebarComponent, CommonModule, FormsModule,
    MatButtonModule, MatIconModule,
    MatTooltipModule, MatProgressSpinnerModule
  ],
  templateUrl: './chat.component.html',
  styleUrl:    './chat.component.scss'
})
export class ChatComponent implements AfterViewChecked, OnInit {

  @ViewChild('messagesEnd')
  private messagesEnd!: ElementRef;

  private authService   = inject(AuthService);
  private fiscalService = inject(FiscalService);
  private http          = inject(HttpClient);

  currentUser       = this.authService.currentUser;
  loading           = signal(false);
  inputText         = signal('');
  afficherHist      = signal(false);
  historique        = signal<any[]>([]);
  clients           = signal<any[]>([]);
  clientSelectionne = signal<string | null>(null);
  moisSelectionne   = signal<string>('');

  messages = signal<Message[]>([{
    id:        0,
    role:      'assistant',
    content:   'Bonjour ! Je suis votre assistant fiscal IA. ' +
      'Posez-moi vos questions sur la TVA, l\'IS, l\'IR ' +
      'ou tout autre aspect du Code Général des Impôts marocain. ' +
      'Vous pouvez aussi sélectionner un client pour analyser ' +
      'ses données financières.',
    timestamp: new Date()
  }]);

  suggestions = [
    'Quel taux de TVA pour les prestations de conseil ?',
    'Comment calculer l\'IS pour une SARL au Maroc ?',
    'Quelles sont les charges déductibles de l\'IS ?',
    'Délai de déclaration de la TVA mensuelle ?',
  ];

  suggestionsClient = [
    'Quel est le chiffre d\'affaires ce mois ?',
    'Analyse la situation financière de ce client',
    'Y a-t-il des factures impayées ?',
    'Quelle est la TVA à payer ?',
  ];

  ngOnInit() {
    this.chargerHistorique();
    this.chargerClients();

    const now = new Date();
    now.setMonth(now.getMonth() - 1);
    this.moisSelectionne.set(
      `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    );
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  private scrollToBottom() {
    try {
      this.messagesEnd?.nativeElement
        ?.scrollIntoView({ behavior: 'smooth' });
    } catch {}
  }

  chargerHistorique() {
    this.fiscalService.getHistoriqueConversations().subscribe({
      next: (h) => this.historique.set(h),
      error: () => {}
    });
  }

  chargerClients() {
    // Récupérer le cabinetId depuis les factures du comptable
    this.http.get<any[]>('http://localhost:8086/api/factures-cpc/mes-factures')
      .subscribe({
        next: (factures) => {
          if (factures && factures.length > 0) {
            const cabinetId = factures[0].cabinetId;
            this.http.get<any[]>(
              `http://localhost:8082/api/cabinets/${cabinetId}/clients/mes-clients`
            ).subscribe({
              next: (clients) => this.clients.set(clients),
              error: () => {}
            });
          }
        },
        error: () => {}
      });
  }

  toggleHistorique() {
    this.afficherHist.update(v => !v);
  }

  chargerDepuisHistorique(conv: any) {
    this.messages.set([
      {
        id: 0, role: 'assistant',
        content: 'Bonjour ! Je suis votre assistant fiscal IA.',
        timestamp: new Date()
      },
      {
        id: 1, role: 'user',
        content: conv.question,
        timestamp: new Date(conv.poseeA)
      },
      {
        id: 2, role: 'assistant',
        content: conv.reponse,
        sources: conv.sources,
        timestamp: new Date(conv.poseeA)
      }
    ]);
    this.afficherHist.set(false);
  }

  selectionnerClient(clientId: string | null) {
    this.clientSelectionne.set(clientId);
    if (clientId) {
      const client = this.clients().find(c => c.id === clientId);
      const nom = client?.nomEntreprise || 'ce client';
      this.messages.update(m => [...m, {
        id:        Date.now(),
        role:      'assistant',
        content:   `✅ Mode analyse client activé pour ${nom}. ` +
          `Je vais maintenant répondre en combinant ses données financières ` +
          `(${this.moisSelectionne()}) avec les lois fiscales marocaines.`,
        timestamp: new Date()
      }]);
    } else {
      this.messages.update(m => [...m, {
        id:        Date.now(),
        role:      'assistant',
        content:   '🌐 Mode lois fiscales activé. Je réponds sur le CGI et les textes fiscaux marocains.',
        timestamp: new Date()
      }]);
    }
  }

  send(text?: string) {
    const msg = (text ?? this.inputText()).trim();
    if (!msg || this.loading()) return;

    this.messages.update(m => [...m, {
      id:        Date.now(),
      role:      'user',
      content:   msg,
      timestamp: new Date()
    }]);

    this.inputText.set('');
    this.loading.set(true);

    const obs = this.clientSelectionne()
      ? this.fiscalService.poserQuestionClient(
        msg,
        this.clientSelectionne()!,
        this.moisSelectionne())
      : this.fiscalService.poserQuestion(msg);

    obs.subscribe({
      next: (res) => {
        this.loading.set(false);
        this.messages.update(m => [...m, {
          id:        Date.now(),
          role:      'assistant',
          content:   res.reponse,
          sources:   res.sources,
          timestamp: new Date()
        }]);
        this.chargerHistorique();
      },
      error: () => {
        this.loading.set(false);
        this.messages.update(m => [...m, {
          id:        Date.now(),
          role:      'assistant',
          content:   '❌ Service fiscal IA indisponible. ' +
            'Vérifiez que fiscal-rag-service (port 8085) ' +
            'et rag_api.py (port 8001) sont démarrés.',
          timestamp: new Date()
        }]);
      }
    });
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  nouvellConversation() {
    this.clientSelectionne.set(null);
    this.messages.set([{
      id:        0,
      role:      'assistant',
      content:   'Bonjour ! Je suis votre assistant fiscal IA. ' +
        'Posez-moi vos questions sur la TVA, l\'IS, l\'IR ' +
        'ou tout autre aspect du Code Général des Impôts marocain. ' +
        'Vous pouvez aussi sélectionner un client pour analyser ' +
        'ses données financières.',
      timestamp: new Date()
    }]);
  }

  get suggestionsActuelles() {
    return this.clientSelectionne()
      ? this.suggestionsClient
      : this.suggestions;
  }
}
