import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Runs for EVERY HttpClient request:
 * 1. Adds "Authorization: Bearer <token>" when logged in (only for our own API, never other sites).
 * 2. If the API answers 401 (token missing/expired), logs out and sends the user to the right sign-in page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isApi = req.url.startsWith(environment.apiUrl);
  const isSignIn = req.url.includes('/auth/');

  const token = isApi ? auth.token() : null;
  // Requests are immutable: clone() makes a copy with the extra header
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      // A 401 on a sign-in request just means "wrong password" — the sign-in page shows that itself
      if (isApi && !isSignIn && error instanceof HttpErrorResponse && error.status === 401) {
        const wasCustomer = auth.isCustomer();
        auth.logout();
        router.navigate([wasCustomer ? '/signin' : '/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => error);
    })
  );
};
