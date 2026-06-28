import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import(
      './features/home/home.component'
      ).then(m => m.HomeComponent)
  },

  // ← Route publique upload client
  {
    path: 'upload/:token',
    loadComponent: () => import(
      './pages/upload-client/upload-client.component'
      ).then(m => m.UploadClientComponent)
  },
  {
    path: 'visio/rejoindre',
    loadComponent: () => import('./features/visio/visio.component')
      .then(m => m.VisioComponent)
  },
  {
    path: 'chat/client',
    loadComponent: () => import(
      './features/chat-client/chat-client.component'
      ).then(m => m.ChatClientComponent)
  },
  {
    path: 'signer/:token',
    loadComponent: () => import(
      './features/public/signer-page/signer-page.component'
      ).then(m => m.SignerPageComponent)
  },
  {
    path: 'auth',
    children: [
      {
        path: 'login',
        loadComponent: () => import(
          './features/auth/login/login.component'
          ).then(m => m.LoginComponent)
      },
      {
        path: 'register',
        loadComponent: () => import(
          './features/auth/register/register.component'
          ).then(m => m.RegisterComponent)
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' }
    ]
  },

  {
    path: 'admin',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import(
      './features/admin/dashboard/admin-dashboard.component'
      ).then(m => m.AdminDashboardComponent)
  },
  {
    path: 'admin/utilisateurs',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import(
      './features/admin/utilisateurs/admin-utilisateurs.component'
      ).then(m => m.AdminUtilisateursComponent)
  },
  {
    path: 'admin/audit',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadComponent: () => import(
      './features/admin/audit/admin-audit.component'
      ).then(m => m.AdminAuditComponent)
  },

  {
    path: 'directeur',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['DIRECTEUR'] },
    children: [
      {
        path: '',
        loadComponent: () => import(
          './features/directeur/dashboard/directeur-dashboard.component'
          ).then(m => m.DirecteurDashboardComponent)
      },
      {
        path: 'membres',
        loadComponent: () => import(
          './features/directeur/membres/membres.component'
          ).then(m => m.MembresComponent)
      },
      {
        path: 'clients',
        loadComponent: () => import(
          './features/directeur/clients/clients.component'
          ).then(m => m.ClientsComponent)
      },
      {
        path: 'rapports',
        loadComponent: () => import(
          './features/directeur/rapports/rapports.component'
          ).then(m => m.RapportsComponent)
      }
    ]
  },

  {
    path: 'comptable',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COMPTABLE'] },
    children: [
      {
        path: '',
        loadComponent: () => import(
          './features/comptable/dashboard/comptable-dashboard.component'
          ).then(m => m.ComptableDashboardComponent)
      },
      {
        path: 'chat',
        loadComponent: () => import(
          './features/comptable/chat/chat.component'
          ).then(m => m.ChatComponent)
      },
      {
        path: 'bilan',
        loadComponent: () => import(
          './features/comptable/bilan/bilan.component'
          ).then(m => m.BilanComponent)
      },
      {
        path: 'factures',
        loadComponent: () => import(
          './features/comptable/factures/factures.component'
          ).then(m => m.FacturesComponent)
      },
      {
        path: 'alertes',
        loadComponent: () => import(
          './features/comptable/alertes/alertes.component'
          ).then(m => m.AlertesComponent)
      },
      {
        path: 'factures-cpc',
        loadComponent: () => import(
          './features/comptable/factures-cpc/factures-cpc.component'
          ).then(m => m.FacturesCpcComponent)
      },
      {
        path: 'tresorerie',
        loadComponent: () => import(
          './features/comptable/tresorerie/dashboard-tresorerie.component'
          ).then(m => m.DashboardTresorerieComponent)
      },
      {
        path: 'signatures',
        loadComponent: () =>
          import('./features/comptable/signatures/signatures.component')
            .then(m => m.SignaturesComponent)
      },
      {
        path: 'bilanC',
        loadComponent: () =>
          import('./features/comptable/bilanC/bilan.component')
          .then(m => m.BilanComponent),

      },
      {
        path: 'cpc',
        loadComponent: () => import(
          './features/comptable/cpc/cpc-viewer.component'
          ).then(m => m.CpcViewerComponent)
      },
      {
        path: 'rapports-mensuels',
        loadComponent: () => import('./features/comptable/rapports-mensuels/rapports-mensuels.component')
          .then(m => m.RapportsMensuelsComponent)
      },
      {
        path: 'tva',
        loadComponent: () => import(
          './features/comptable/tva-declaration/tva-declaration.component'
          ).then(m => m.TvaDeclarationComponent)
      },
      // Dans le children de 'comptable', ajoute :
      {
        path: 'chat1',
        loadComponent: () => import(
          './features/comptable/chat1/chat.component'
          ).then(m => m.ChatComponent)
      },


    ]
  },

  // Wildcard — toujours en dernier
  { path: '**', redirectTo: '' }
];
