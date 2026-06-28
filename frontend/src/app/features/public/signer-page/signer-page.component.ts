import { Component, OnInit, ViewChild, ElementRef, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';

@Component({
  selector: 'app-signer-page',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatProgressBarModule],
  templateUrl: './signer-page.component.html',
  styleUrl: './signer-page.component.scss'
})
export class SignerPageComponent implements OnInit {

  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  private ctx!: CanvasRenderingContext2D;
  private isDrawing = false;
  private token = '';
  private sanitizer = inject(DomSanitizer);

  BASE = 'http://localhost:8082/api/public/signature';

  etat       = signal<'chargement' | 'attente' | 'signe' | 'expire' | 'erreur'>('chargement');
  clientNom  = signal('');
  urlPdfSafe = signal<SafeResourceUrl | null>(null);
  envoi      = signal(false);
  hasDessin  = signal(false);
  message    = signal('');

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient
  ) {}

  ngOnInit() {
    this.token = this.route.snapshot.paramMap.get('token') || '';
    this.http.get<any>(`${this.BASE}/${this.token}`).subscribe({
      next: data => {
        if (data.statut === 'SIGNE') {
          this.clientNom.set(data.clientNom);
          this.etat.set('signe');
        } else if (data.statut === 'EN_ATTENTE_CLIENT') {
          this.clientNom.set(data.clientNom);
          // Sanitizer l'URL pour l'iframe
          this.urlPdfSafe.set(
            this.sanitizer.bypassSecurityTrustResourceUrl(data.urlPdf)
          );
          this.etat.set('attente');
          setTimeout(() => this.initCanvas(), 200);
        } else {
          this.etat.set('expire');
        }
      },
      error: () => this.etat.set('erreur')
    });
  }

  initCanvas() {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;
    this.ctx = canvas.getContext('2d')!;
    this.ctx.strokeStyle = '#1e293b';
    this.ctx.lineWidth   = 2.5;
    this.ctx.lineCap     = 'round';
    this.ctx.lineJoin    = 'round';

    canvas.addEventListener('mousedown',  e => this.startDraw(e));
    canvas.addEventListener('mousemove',  e => this.draw(e));
    canvas.addEventListener('mouseup',    () => this.stopDraw());
    canvas.addEventListener('mouseleave', () => this.stopDraw());
    canvas.addEventListener('touchstart', e => this.startDrawTouch(e), { passive: false });
    canvas.addEventListener('touchmove',  e => this.drawTouch(e),      { passive: false });
    canvas.addEventListener('touchend',   () => this.stopDraw());
  }

  startDraw(e: MouseEvent) {
    this.isDrawing = true;
    const r = this.canvasRef.nativeElement.getBoundingClientRect();
    this.ctx.beginPath();
    this.ctx.moveTo(e.clientX - r.left, e.clientY - r.top);
  }

  draw(e: MouseEvent) {
    if (!this.isDrawing) return;
    const r = this.canvasRef.nativeElement.getBoundingClientRect();
    this.ctx.lineTo(e.clientX - r.left, e.clientY - r.top);
    this.ctx.stroke();
    this.hasDessin.set(true);
  }

  startDrawTouch(e: TouchEvent) {
    e.preventDefault();
    this.isDrawing = true;
    const r = this.canvasRef.nativeElement.getBoundingClientRect();
    const t = e.touches[0];
    this.ctx.beginPath();
    this.ctx.moveTo(t.clientX - r.left, t.clientY - r.top);
  }

  drawTouch(e: TouchEvent) {
    e.preventDefault();
    if (!this.isDrawing) return;
    const r = this.canvasRef.nativeElement.getBoundingClientRect();
    const t = e.touches[0];
    this.ctx.lineTo(t.clientX - r.left, t.clientY - r.top);
    this.ctx.stroke();
    this.hasDessin.set(true);
  }

  stopDraw() { this.isDrawing = false; }

  effacer() {
    const canvas = this.canvasRef.nativeElement;
    this.ctx.clearRect(0, 0, canvas.width, canvas.height);
    this.hasDessin.set(false);
  }

  confirmerSignature() {
    if (!this.hasDessin()) return;
    const signature = this.canvasRef.nativeElement.toDataURL('image/png');
    this.envoi.set(true);
    this.http.post<any>(`${this.BASE}/${this.token}/signer`, { signature }).subscribe({
      next: res => {
        this.message.set(res.message || 'Document signé avec succès !');
        this.etat.set('signe');
        this.envoi.set(false);
      },
      error: err => {
        this.envoi.set(false);
        alert(err.error?.erreur || 'Erreur lors de la signature');
      }
    });
  }
}
