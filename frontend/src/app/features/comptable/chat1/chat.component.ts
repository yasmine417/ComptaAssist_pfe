import {
  Component, OnInit, OnDestroy, inject,
  signal, ViewChild, ElementRef, AfterViewChecked
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import {ComptableSidebarComponent} from '../../../shared/components/comptable-sidebar/comptable-sidebar.component';
import {ChatService, Conversation, Message} from '../../../core/services/chat.service';
import {WebSocketService} from '../../../core/services/websocket.service';
import {AuthService} from '../../../core/services/auth.service';
import {ClientContextService} from '../../../core/services/client-context.service';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {VisioService} from '../../../core/services/visio.service';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatIconModule, MatTooltipModule,MatSnackBarModule,
    ComptableSidebarComponent
  ],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss'
})
export class ChatComponent
  implements OnInit, OnDestroy, AfterViewChecked {

  @ViewChild('messagesEnd')
  messagesEnd!: ElementRef;

  private chatService  = inject(ChatService);
  private wsService    = inject(WebSocketService);
  private authService  = inject(AuthService);
  private clientCtx    = inject(ClientContextService);
  private snackBar = inject(MatSnackBar);

  currentUser       = this.authService.currentUser;
  conversations     = signal<Conversation[]>([]);
  messages          = signal<Message[]>([]);
  convActive        = signal<Conversation | null>(null);
  loading           = signal(false);
  loadingMessages   = signal(false);
  texte             = '';
  connected         = signal(false);
  private shouldScroll = false;

  private pollingInterval: any;

  ngOnInit() {
    this.wsService.connect();
    this.chargerConversations();

    this.wsService.connected$.subscribe(connected => {
      this.connected.set(connected);
      if (connected) this.abonnerMessages();
    });
  }


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
        this.chatService.marquerLus(conv.id).subscribe();

        this.pollingInterval = setInterval(() => {
          this.chatService.getMessages(conv.id)
            .subscribe(newMsgs => {
              if (newMsgs.length > this.dernierMsgCount) {
                const nouveaux = newMsgs.slice(this.dernierMsgCount);

                nouveaux.forEach(msg => {
                  if (msg.expediteurType === 'CLIENT') {
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

                // Remplacer les messages locaux par ceux du serveur
                // (pour avoir les vrais IDs et URLs)
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


  ngOnDestroy() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }
    this.wsService.disconnect();
  }

  private abonnerMessages() {
    const user = this.currentUser();
    if (!user?.id) return;

    const dest = `/user/${user.id}/queue/messages`;
    this.wsService.unsubscribe(dest);

    this.wsService.subscribe(dest, (msg: Message) => {
      const conv = this.convActive();

      if (conv && msg.conversationId === conv.id
        && msg.expediteurType === 'CLIENT') {
        this.messages.update(m => [...m, msg]);
        this.shouldScroll = true;
      } else if (msg.expediteurType === 'CLIENT') {
        // ← Notification si la conversation n'est pas active
        this.snackBar.open(
          `Nouveau message de ${msg.expediteurNom} : ${msg.contenu.length > 45 ? msg.contenu.substring(0, 45) + '...' : msg.contenu}`,
          'Voir',
          {
            duration: 4000,
            panelClass: ['chat-notif'],
            horizontalPosition: 'right',
            verticalPosition: 'top'
          }
        ).onAction().subscribe(() => {
          // Ouvrir la conversation au clic
          const convCible = this.conversations()
            .find(c => c.id === msg.conversationId);
          if (convCible) this.ouvrirConversation(convCible);
        });
      }

      this.chargerConversations();
    });
  }



  ngAfterViewChecked() {
    if (this.shouldScroll) {
      this.scrollBas();
      this.shouldScroll = false;
    }
  }



  chargerConversations() {
    this.loading.set(true);
    this.chatService.mesConversations().subscribe({
      next: (convs) => {
        this.conversations.set(convs);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }



  copierLienClient(conv: Conversation) {
    const lien = `http://localhost:4200/chat/client?clientId=${conv.clientId}`;
    navigator.clipboard.writeText(lien);
    // ou afficher le lien
    alert(`Lien client : ${lien}`);
  }



  // Ouvrir/créer conversation avec le client actif
  ouvrirAvecClientActif() {
    const client = this.clientCtx.clientActif();
    const user   = this.currentUser();
    if (!client || !user) return;

    // Construire le nom depuis les champs disponibles
    const prenom = (user as any).prenom || '';
    const nom    = (user as any).nom    || '';
    const comptableNom = `${prenom} ${nom}`.trim()
      || user.email
      || 'Comptable';

    console.log('comptableNom envoyé:', comptableNom);

    this.chatService.ouvrirConversation(
      client.id,
      client.nomEntreprise,
      client.email || '',
      user.cabinetId || '',
      comptableNom
    ).subscribe({
      next: (conv) => {
        const exists = this.conversations()
          .find(c => c.id === conv.id);
        if (!exists) {
          this.conversations.update(c => [conv, ...c]);
        }
        this.ouvrirConversation(conv);
      }
    });
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

    if (file.size > 10 * 1024 * 1024) {
      this.snackBar.open(
        '❌ Fichier trop grand (max 10MB)', '',
        { duration: 3000 });
      input.value = '';
      return;
    }

    this.snackBar.open('📤 Envoi en cours...', '', {
      duration: 2000 });

    this.chatService.uploadFichier(conv.id, file)
      .subscribe({
        next: (res) => {
          this.chatService.envoyerMessageRest(
            conv.id,
            res.nom,
            res.type as any,
            res.url,
            res.nom
          ).subscribe({
            next: (msg) => {
              this.messages.update(m => [...m, msg]);
              this.dernierMsgCount = this.messages().length;
              this.shouldScroll = true;
              this.snackBar.open('✅ Fichier envoyé', '',
                { duration: 2000 });
              // ← SUPPRIME le wsService.send ici
            },
            error: () => this.snackBar.open(
              '❌ Erreur envoi', '', { duration: 3000 })
          });
        },
        error: () => {
          this.snackBar.open(
            '❌ Erreur upload', '', { duration: 3000 });
        }
      });



    input.value = '';
  }







  envoyer() {
    const conv = this.convActive();
    if (!conv || !this.texte.trim()) return;

    const user = this.currentUser();
    const contenu = this.texte.trim();
    this.texte = '';

    // Afficher immédiatement sans attendre le serveur
    const msgLocal: Message = {
      id: crypto.randomUUID(),
      conversationId: conv.id,
      expediteurType: 'COMPTABLE',
      expediteurId: user?.id || '',
      expediteurNom: `${user?.prenom || ''} ${user?.nom || ''}`.trim(),
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

  estMoi(msg: Message): boolean {
    return msg.expediteurType === 'COMPTABLE';
  }

  private scrollBas() {
    try {
      this.messagesEnd?.nativeElement
        ?.scrollIntoView({ behavior: 'smooth' });
    } catch {}
  }

  formatDate(dateStr: string): string {
    const d = new Date(dateStr);
    const now = new Date();
    const isToday = d.toDateString() === now.toDateString();

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
    return conv.nonLusComptable;
  }

  getInitiales(nom: string): string {
    return nom.split(' ')
      .map(n => n[0])
      .join('')
      .substring(0, 2)
      .toUpperCase();
  }


  private visioService = inject(VisioService);
  demarrantVisio = signal(false);

  demarrerVisio() {
    const conv = this.convActive();
    const user = this.currentUser();
    if (!conv || !user) return;

    this.demarrantVisio.set(true);
    const nomComptable = `${user.prenom || ''} ${user.nom || ''}`.trim();

    this.visioService.demarrerAvecClient(conv.id, nomComptable)
      .subscribe({
        next: (creds) => {
          this.demarrantVisio.set(false);
          // Ouvre la visio dans un nouvel onglet pour le comptable aussi
          window.open(
            `/visio/rejoindre?room=${creds.roomName}`,
            '_blank'
          );
        },
        error: () => this.demarrantVisio.set(false)
      });
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
