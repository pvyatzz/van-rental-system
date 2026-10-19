import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // withComponentInputBinding: route params (:id) and query params (?vehicleId=) become component inputs
    provideRouter(routes, withComponentInputBinding()),
    // every HttpClient request passes through authInterceptor (adds the login token)
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
