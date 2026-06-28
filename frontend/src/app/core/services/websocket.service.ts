import { Injectable, inject, NgZone } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { BehaviorSubject, Subject } from 'rxjs';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class WebSocketService {

  private authService = inject(AuthService);
  private ngZone      = inject(NgZone);  // ← ajoute ça
  private client!: Client;
  private subscriptions: Map<string, StompSubscription> = new Map();

  connected$ = new BehaviorSubject<boolean>(false);
  message$   = new Subject<any>();

  connect() {
    const token = this.authService.getToken();
    if (!token) return;

    if (this.client?.active) {
      this.client.deactivate();
    }
    this.subscriptions.clear();

    this.client = new Client({
      brokerURL: 'ws://localhost:8088/ws-chat/websocket',
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      reconnectDelay: 3000,
      onConnect: () => {
        // ← Rentrer dans la zone Angular
        this.ngZone.run(() => {
          this.connected$.next(true);
          console.log('✅ WebSocket comptable connecté');
        });
      },
      onDisconnect: () => {
        this.ngZone.run(() => {
          this.connected$.next(false);
        });
      }
    });

    this.client.activate();
  }

  connectAsClient(clientId: string) {
    if (this.client?.active) {
      this.client.deactivate();
    }
    this.subscriptions.clear();

    this.client = new Client({
      brokerURL: 'ws://localhost:8088/ws-chat/websocket',
      connectHeaders: {
        clientId: clientId
      },
      reconnectDelay: 3000,
      onConnect: () => {
        this.ngZone.run(() => {  // ← NgZone ici aussi
          this.connected$.next(true);
          console.log('✅ WebSocket client connecté:', clientId);
        });
      },
      onDisconnect: () => {
        this.ngZone.run(() => {
          this.connected$.next(false);
        });
      }
    });

    this.client.activate();
  }

  disconnect() {
    this.subscriptions.forEach(s => s.unsubscribe());
    this.subscriptions.clear();
    this.client?.deactivate();
    this.ngZone.run(() => this.connected$.next(false));
  }

  subscribe(destination: string,
            callback: (msg: any) => void): void {
    if (!this.client?.connected) {
      const sub = this.connected$.subscribe(connected => {
        if (connected) {
          this.doSubscribe(destination, callback);
          sub.unsubscribe();
        }
      });
      return;
    }
    this.doSubscribe(destination, callback);
  }

  private doSubscribe(destination: string,
                      callback: (msg: any) => void) {
    // Supprimer l'ancien abonnement s'il existe
    if (this.subscriptions.has(destination)) {
      this.subscriptions.get(destination)?.unsubscribe();
      this.subscriptions.delete(destination);
    }

    console.log('✅ Abonnement créé:', destination);

    const sub = this.client.subscribe(
      destination,
      (msg: IMessage) => {
        this.ngZone.run(() => {
          console.log('📨 Message brut reçu:', msg.body);
          try {
            callback(JSON.parse(msg.body));
          } catch {
            callback(msg.body);
          }
        });
      }
    );
    this.subscriptions.set(destination, sub);
  }




  unsubscribe(destination: string) {
    this.subscriptions.get(destination)?.unsubscribe();
    this.subscriptions.delete(destination);
  }

  send(destination: string, body: any) {
    const token = this.authService.getToken();
    const headers: Record<string, string> = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;

    if (!this.client?.connected) {
      console.warn('WebSocket non connecté');
      return;
    }

    this.client.publish({
      destination,
      headers,
      body: JSON.stringify(body)
    });
  }
}
