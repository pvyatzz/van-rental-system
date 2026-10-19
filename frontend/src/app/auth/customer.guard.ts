import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/** Booking and "My bookings" need a Google sign-in. Not signed in → /login?returnUrl=... */
export const customerGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).hasRole('CUSTOMER')) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
