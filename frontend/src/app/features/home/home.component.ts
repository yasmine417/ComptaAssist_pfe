import {
  Component, OnInit, OnDestroy,
  HostListener, inject
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, CommonModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, OnDestroy {

  isScrolled  = false;
  menuOpen    = false;

  private observer!: IntersectionObserver;

  features = [
    {
      icon: 'receipt_long',
      title: 'OCR & Comptabilisation IA',
      desc: 'Uploadez vos factures PDF. L\'IA les lit, les comptabilise selon le PCG marocain et génère les écritures comptables en quelques secondes.',
      color: '#2563a8'
    },
    {
      icon: 'chat',
      title: 'Assistant Fiscal Intelligent',
      desc: 'Posez vos questions sur la TVA, IS, IR. L\'IA cite les articles exacts du Code Général des Impôts 2024.',
      color: '#7c3aed'
    },
    {
      icon: 'person_search',
      title: 'Analyse Client en Temps Réel',
      desc: 'Interrogez l\'IA sur n\'importe quel client : CA, TVA, factures impayées — avec les données réelles de votre base.',
      color: '#059669'
    },
    {
      icon: 'summarize',
      title: 'Rapports Mensuels Automatiques',
      desc: 'Génération automatique le 1er de chaque mois. Analyse IA, graphiques, envoi email — zéro intervention manuelle.',
      color: '#d97706'
    },
    {
      icon: 'notifications_active',
      title: 'Notifications & Alertes',
      desc: 'Les comptables reçoivent en temps réel les alertes sur les rapports générés, les échéances et les anomalies détectées.',
      color: '#dc2626'
    },
    {
      icon: 'history',
      title: 'Audit Log Complet',
      desc: 'Traçabilité totale de toutes les actions : créations, modifications, connexions — filtrables par rôle et par action.',
      color: '#0891b2'
    }
  ];

  stats = [
    { value: '500+', label: 'Cabinets utilisateurs' },
    { value: '10 000+', label: 'Dossiers gérés' },
    { value: '98%', label: 'Précision IA' },
    { value: '3h', label: 'Économisées / jour' }
  ];

  roles = [
    {
      icon: 'admin_panel_settings',
      title: 'Administrateur',
      gradient: 'linear-gradient(135deg, #1a3d6b, #2563a8)',
      desc: 'Pilotez la plateforme et les ressources IA.',
      items: [
        'Indexation des lois fiscales (CGI, CGNC)',
        'Gestion des utilisateurs et directeurs',
        'Audit log complet de toutes les actions'
      ]
    },
    {
      icon: 'business_center',
      title: 'Directeur de Cabinet',
      gradient: 'linear-gradient(135deg, #065f46, #059669)',
      desc: 'Gérez votre cabinet et votre équipe.',
      items: [
        'Création et gestion des clients',
        'Supervision de l\'équipe comptable',
        'Consultation des rapports générés'
      ]
    },
    {
      icon: 'account_balance',
      title: 'Expert Comptable',
      gradient: 'linear-gradient(135deg, #5b21b6, #7c3aed)',
      desc: 'Analysez, comptabilisez, conseillez.',
      items: [
        'OCR et comptabilisation automatique',
        'Assistant IA fiscal + analyse clients',
        'Trésorerie et journal comptable'
      ]
    }
  ];

  ngOnInit() {
    this.initScrollReveal();
  }

  ngOnDestroy() {
    this.observer?.disconnect();
  }

  @HostListener('window:scroll')
  onScroll() {
    this.isScrolled = window.scrollY > 60;
  }

  initScrollReveal() {
    const options = {
      threshold: 0.12,
      rootMargin: '0px 0px -40px 0px'
    };

    this.observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
        }
      });
    }, options);

    setTimeout(() => {
      document.querySelectorAll(
        '.reveal, .reveal-up, .reveal-right'
      ).forEach(el => this.observer.observe(el));
    }, 100);
  }

  scrollTo(event: Event, id: string) {
    event.preventDefault();
    document.getElementById(id)?.scrollIntoView({
      behavior: 'smooth', block: 'start'
    });
  }
}
