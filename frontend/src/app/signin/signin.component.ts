import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';

import { GoogleSignInButtonComponent } from '../auth/google-sign-in-button.component';

/** Customer sign-in page. The customer guard sends people here before booking. */
@Component({
  selector: 'app-signin',
  imports: [GoogleSignInButtonComponent],
  template: `
    <section class="page">
      <div class="card">
        <h1>Sign in to book</h1>
        <p>We use your Google account so we can email you about your booking. We only see your name and email
          address.</p>
        <app-google-sign-in-button (signedIn)="continue()" />
      </div>
    </section>
  `,
  styles: `
    .page { max-width: 420px; margin: 0 auto; padding: 48px 16px; }
    .card { background: #fff; border: 1px solid #e3e3e3; border-radius: 10px; padding: 24px; }
    h1 { font-size: 1.4rem; margin: 0 0 8px; }
    p { color: #555; margin: 0 0 20px; }
  `
})
export class SigninComponent {
  private readonly router = inject(Router);

  /** Where to go after signing in, e.g. /book?vehicleId=2 */
  readonly returnUrl = input<string>();

  continue(): void {
    const url = this.returnUrl();
    // only paths inside this app (no "open redirect" to another site)
    this.router.navigateByUrl(url && url.startsWith('/') && !url.startsWith('//') ? url : '/my-bookings');
  }
}
