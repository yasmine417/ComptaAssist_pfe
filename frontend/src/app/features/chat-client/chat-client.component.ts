import {
  Component, OnInit, OnDestroy, inject,
  signal, ViewChild, ElementRef, AfterViewChecked
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute } from '@angular/router';
import {ChatService, Conversation, Message} from '../../core/services/chat.service';
import {WebSocketService} from '../../core/services/websocket.service';
import {AuthService} from '../../core/services/auth.service';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import { Input, OnChanges, SimpleChanges } from '@angular/core';
@Component({
  selector: 'app-chat-client',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatIconModule, MatSnackBarModule,MatTooltipModule
  ],
  templateUrl: './chat-client.component.html',
  styleUrl: './chat-client.component.scss'
})
export class ChatClientComponent
  implements OnInit, OnDestroy, AfterViewChecked {

  @ViewChild('messagesEnd')
  messagesEnd!: ElementRef;
  private snackBar = inject(MatSnackBar);
  private chatService  = inject(ChatService);
  private wsService    = inject(WebSocketService);
  private authService  = inject(AuthService);
  private route        = inject(ActivatedRoute);

  currentUser       = this.authService.currentUser;
  conversations     = signal<Conversation[]>([]);
  messages          = signal<Message[]>([]);
  convActive        = signal<Conversation | null>(null);
  loading           = signal(false);
  loadingMessages   = signal(false);
  texte             = '';
  connected         = signal(false);
  private shouldScroll = false;
  @Input() clientIdInput: string = '';
  // clientId passé en query param ou récupéré depuis le token
  private clientId = '';



  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      // Priorité 1 : Input depuis portail upload
      // Priorité 2 : query param direct
      this.clientId = this.clientIdInput
        || params['clientId']
        || '';

      if (!this.clientId) return;

      this.wsService.connectAsClient(this.clientId);
      this.chargerConversations();

      this.wsService.connected$.subscribe(connected => {
        this.connected.set(connected);
        if (connected) {
          this.abonnerMessages();
        }
      });
    });
  }

  private abonnerMessages() {
    const dest = `/user/${this.clientId}/queue/messages`;
    this.wsService.unsubscribe(dest);

    this.wsService.subscribe(dest, (msg: Message) => {
      const conv = this.convActive();

      if (conv && msg.conversationId === conv.id
        && msg.expediteurType === 'COMPTABLE') {
        this.messages.update(m => [...m, msg]);
        this.shouldScroll = true;
      } else if (msg.expediteurType === 'COMPTABLE') {
        // ← Notification
        this.snackBar.open(
          `Nouveau message de ${msg.expediteurNom} : ${msg.contenu.length > 45 ? msg.contenu.substring(0, 45) + '...' : msg.contenu}`,
          'Voir',
          {
            duration: 4000,
            panelClass: ['chat-notif'],
            horizontalPosition: 'right',
            verticalPosition: 'top'
          }
        );
      }

      this.chargerConversations();
    });
  }


  private init() {
    this.wsService.connect();

    this.wsService.connected$.subscribe(c => {
      this.connected.set(c);
      if (c) this.abonnerMessages();
    });

    this.chargerConversations();
  }

  ngAfterViewChecked() {
    if (this.shouldScroll) {
      this.scrollBas();
      this.shouldScroll = false;
    }
  }

  ngOnDestroy() {
    this.wsService.disconnect();
  }

  chargerConversations() {
    if (!this.clientId) return;
    this.loading.set(true);

    this.chatService.conversationsClient(this.clientId)
      .subscribe({
        next: (convs) => {
          this.conversations.set(convs);
          this.loading.set(false);
          // Ouvrir automatiquement la première conversation
          if (convs.length > 0 && !this.convActive()) {
            this.ouvrirConversation(convs[0]);
          }
        },
        error: () => this.loading.set(false)
      });
  }



  private pollingInterval: any;


  private dernierMsgCount = 0;

  ouvrirConversation(conv: Conversation) {
    this.convActive.set(conv);
    this.loadingMessages.set(true);
    this.messages.set([]);

    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }

    this.chatService.getMessages(conv.id).subscribe({
      next: (msgs) => {
        this.messages.set(msgs);
        this.dernierMsgCount = msgs.length;
        this.loadingMessages.set(false);
        this.shouldScroll = true;

        this.pollingInterval = setInterval(() => {
          this.chatService.getMessages(conv.id)
            .subscribe(newMsgs => {
              if (newMsgs.length > this.dernierMsgCount) {
                const nouveaux = newMsgs.slice(this.dernierMsgCount);

                nouveaux.forEach(msg => {
                  if (msg.expediteurType === 'COMPTABLE') {
                    this.snackBar.open(
                      `Nouveau message de ${msg.expediteurNom} : ${msg.typeMessage === 'TEXTE' || !msg.typeMessage ? (msg.contenu.length > 45 ? msg.contenu.substring(0, 45) + '...' : msg.contenu) : 'Fichier joint'}`,
                      'OK',
                      {
                        duration: 4000,
                        panelClass: ['chat-notif'],
                        horizontalPosition: 'right',
                        verticalPosition: 'top'
                      }
                    );
                  }
                });

                this.messages.set(newMsgs);
                this.dernierMsgCount = newMsgs.length;
                this.shouldScroll = true;
              }
            });
        }, 2000);





      },
      error: () => this.loadingMessages.set(false)
    });
  }


  envoyer() {
    const conv = this.convActive();
    if (!conv || !this.texte.trim()) return;

    const contenu = this.texte.trim();
    this.texte = '';

    // ← Afficher IMMÉDIATEMENT côté client
    const msgLocal: Message = {
      id: crypto.randomUUID(),
      conversationId: conv.id,
      expediteurType: 'CLIENT',
      expediteurId: this.clientId,
      expediteurNom: conv.clientNom,
      contenu,
      lu: false,
      typeMessage: 'TEXTE',
      createdAt: new Date().toISOString()
    };
    this.messages.update(m => [...m, msgLocal]);
    this.shouldScroll = true;

    // Envoyer via WebSocket
    this.wsService.send('/app/chat.envoyer', {
      conversationId: conv.id,
      contenu,
      typeMessage: 'TEXTE'
    });
  }



  onEnter(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.envoyer();
    }
  }

  // Le client voit ses messages à droite
  estMoi(msg: Message): boolean {
    const result = msg.expediteurType === 'CLIENT';
    // console.log('estMoi:', msg.expediteurType, '->', result);
    return result;
  }

  private scrollBas() {
    try {
      this.messagesEnd?.nativeElement
        ?.scrollIntoView({ behavior: 'smooth' });
    } catch {}
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const now = new Date();
    const isToday =
      d.toDateString() === now.toDateString();

    if (isToday) {
      return d.toLocaleTimeString('fr-MA', {
        hour: '2-digit', minute: '2-digit'
      });
    }
    return d.toLocaleDateString('fr-MA', {
      day: 'numeric', month: 'short',
      hour: '2-digit', minute: '2-digit'
    });
  }

  getNonLus(conv: Conversation): number {
    return conv.nonLusClient;
  }

  getInitiales(nom: string): string {
    if (!nom) return '?';
    return nom.split(' ')
      .map(n => n[0])
      .join('')
      .substring(0, 2)
      .toUpperCase();
  }

  get nomComptable(): string {
    return this.convActive()?.comptableNom || 'Votre comptable';
  }
  ouvrirFichier(url: string) {
    window.open(url, '_blank');
  }

  onFichierSelectionne(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const conv = this.convActive();
    if (!conv) return;
    console.log('this.clientId:', this.clientId);
    console.log('conv.clientId:', conv.clientId);

    if (file.size > 10 * 1024 * 1024) {
      this.snackBar.open('❌ Fichier trop grand', '',
        { duration: 3000 });
      input.value = '';
      return;
    }

    this.chatService.uploadFichier(conv.id, file)
      .subscribe({
        next: (res) => {
          this.chatService.envoyerMessageRest(
            conv.id,
            res.nom,
            res.type as any,
            res.url,
            res.nom,
            this.clientId  // ← ajoute le clientId
          ).subscribe({
            next: (msg) => {
              this.messages.update(m => [...m, msg]);
              this.dernierMsgCount = this.messages().length;
              this.shouldScroll = true;
              this.snackBar.open('✅ Fichier envoyé', '',
                { duration: 2000 });
            }

          });

        },
        error: () => this.snackBar.open(
          '❌ Erreur upload', '', { duration: 3000 })
      });




    input.value = '';
  }

  estLienVisio(contenu: string): boolean {
    return contenu.includes('/visio/rejoindre');
  }

  extraireLienVisio(contenu: string): string {
    const match = contenu.match(/http:\/\/[^\s]+/);
    return match ? match[0] : '';
  }

  extraireTexteAvantLien(contenu: string): string {
    return contenu.split('http://')[0];
  }
}
