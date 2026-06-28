import { Injectable, signal, computed } from '@angular/core';
import { ClientResponse } from '../models/cabinet.models';

/**
 * Service global partagé entre tous les composants.
 * Stocke le client actuellement sélectionné par le comptable.
 * Toutes les pages filtrent leurs données sur ce clientId.
 */
@Injectable({ providedIn: 'root' })
export class ClientContextService {

  private _clientActif = signal<ClientResponse | null>(null);
  private _clients     = signal<ClientResponse[]>([]);

  // Lecture
  clientActif  = this._clientActif.asReadonly();
  clients      = this._clients.asReadonly();
  clientActifId = computed(() => this._clientActif()?.id ?? null);

  // Écriture
  setClients(clients: ClientResponse[]) {
    this._clients.set(clients);
    // Sélectionner le premier client par défaut seulement
    // si aucun client actif n'est déjà sélectionné
    if (!this._clientActif() && clients.length > 0)
      this._clientActif.set(clients[0]);
  }

  setClientActif(client: ClientResponse) {
    this._clientActif.set(client);
  }

  reset() {
    this._clientActif.set(null);
    this._clients.set([]);
  }
}
