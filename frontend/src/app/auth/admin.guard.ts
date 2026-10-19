import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Runs before the router opens /admin. Not logged in as admin → go to /login?returnUrl=/admin/...
 * This only hides pages; the real protection is the backend refusing requests without a valid token.
 */
export const adminGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).hasRole('ADMIN')) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
