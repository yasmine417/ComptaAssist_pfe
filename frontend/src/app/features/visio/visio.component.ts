import {
  Component, ElementRef, OnDestroy, OnInit,
  ViewChild, inject, signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import {
  Room, RoomEvent, RemoteParticipant,
  RemoteTrack, RemoteTrackPublication,
  createLocalVideoTrack
} from 'livekit-client';
import { VisioService } from '../../core/services/visio.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-visio',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './visio.component.html',
  styleUrl: './visio.component.scss'
})
export class VisioComponent implements OnInit, OnDestroy {

  @ViewChild('localVideo')
  localVideoRef!: ElementRef<HTMLVideoElement>;
  @ViewChild('remoteContainer')
  remoteContainerRef!: ElementRef<HTMLDivElement>;

  private visioService = inject(VisioService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);

  private room: Room | null = null;

  connecte = signal(false);
  connexionEnCours = signal(false);
  micActif = signal(true);
  cameraActive = signal(true);
  partageEcranActif = signal(false);
  participants = signal<string[]>([]);
  roomNameActuel = signal('');

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['room']) {
        this.roomNameActuel.set(params['room']);
      }
    });
  }

  async rejoindreReunion() {
    const roomName = this.roomNameActuel();
    if (!roomName) return;

    this.connexionEnCours.set(true);
    const user = this.authService.currentUser();
    const nomAffiche = user
      ? `${user.prenom} ${user.nom}`
      : 'Client';

    this.visioService.rejoindre(roomName, nomAffiche)
      .subscribe({
        next: async (creds) => {
          await this.connecterLiveKit(creds.url, creds.token);
          this.connexionEnCours.set(false);
        },
        error: (err) => {
          console.error('Erreur rejoindre visio:', err);
          this.connexionEnCours.set(false);
        }
      });
  }

  private async connecterLiveKit(url: string, token: string) {
    this.room = new Room();

    this.room.on(RoomEvent.TrackSubscribed, (
      track: RemoteTrack,
      publication: RemoteTrackPublication,
      participant: RemoteParticipant
    ) => {
      const element = track.attach();
      element.style.width = '320px';
      element.style.borderRadius = '12px';
      element.style.margin = '6px';

      if (this.remoteContainerRef?.nativeElement) {
        this.remoteContainerRef.nativeElement
          .appendChild(element);
      } else {
        // Le DOM n'est pas encore prêt, on retente
        setTimeout(() => {
          if (this.remoteContainerRef?.nativeElement) {
            this.remoteContainerRef.nativeElement
              .appendChild(element);
          }
        }, 100);
      }
    });

    this.room.on(RoomEvent.ParticipantConnected, (p) => {
      this.participants.update(list => [...list, p.identity]);
    });

    this.room.on(RoomEvent.ParticipantDisconnected, (p) => {
      this.participants.update(
        list => list.filter(id => id !== p.identity));
    });

    // ← Affiche la zone vidéo AVANT de connecter
    // pour que le DOM (#remoteContainer, #localVideo)
    // existe déjà quand les événements LiveKit arrivent
    this.connecte.set(true);

    // Laisse Angular rendre le DOM avant de continuer
    await new Promise(resolve => setTimeout(resolve, 0));

    await this.room.connect(url, token);

    // ── Essaie la caméra, sinon continue sans ──────────
    try {
      const videoTrack = await createLocalVideoTrack();
      await this.room.localParticipant.publishTrack(videoTrack);
      if (this.localVideoRef?.nativeElement) {
        videoTrack.attach(this.localVideoRef.nativeElement);
      }
      this.cameraActive.set(true);
    } catch (err) {
      console.warn('Caméra indisponible, audio seul:', err);
      this.cameraActive.set(false);
    }

    // ── Le micro, lui, devrait fonctionner indépendamment ──
    try {
      await this.room.localParticipant.setMicrophoneEnabled(true);
    } catch (err) {
      console.warn('Micro indisponible:', err);
      this.micActif.set(false);
    }
  }

  toggleMic() {
    if (!this.room) return;
    const enabled = !this.micActif();
    this.room.localParticipant.setMicrophoneEnabled(enabled);
    this.micActif.set(enabled);
  }

  toggleCamera() {
    if (!this.room) return;
    const enabled = !this.cameraActive();
    this.room.localParticipant.setCameraEnabled(enabled);
    this.cameraActive.set(enabled);
  }

  async toggleScreenShare() {
    if (!this.room) return;
    const enabled = !this.partageEcranActif();
    await this.room.localParticipant
      .setScreenShareEnabled(enabled);
    this.partageEcranActif.set(enabled);
  }

  quitterReunion() {
    this.room?.disconnect();
    this.room = null;
    this.connecte.set(false);
  }

  ngOnDestroy() {
    this.room?.disconnect();
  }
}
