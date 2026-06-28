import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const allowedRoles: string[] = route.data['roles'] || [];
  const userRole = authService.getRole();

  if (userRole && allowedRoles.includes(userRole)) {
    return true;
  }

  // Redirect to appropriate dashboard based on role
  switch (userRole) {
    case 'ADMIN': router.navigate(['/admin']); break;
    case 'DIRECTEUR': router.navigate(['/directeur']); break;
    case 'COMPTABLE': router.navigate(['/comptable']); break;
    default: router.navigate(['/auth/login']);
  }
  return false;
};
