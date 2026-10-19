import { AfterViewInit, Component, ElementRef, NgZone, inject, output, signal, viewChild } from '@angular/core';

import { environment } from '../../environments/environment';
import { problemMessage } from '../shared/problem';
import { AuthService } from './auth.service';

/**
 * Google's own "Sign in with Google" button (drawn by Google's script, not by us).
 * When the user finishes signing in, Google gives us an ID token; we send it to our backend,
 * which verifies it and returns our own CUSTOMER session.
 */
@Component({
  selector: 'app-google-sign-in-button',
  template: `
    @if (!configured) {
      <p class="note">Google sign-in is not set up yet: add the Google Client ID to <code>environment.ts</code>.</p>
    } @else {
      <div #buttonHost class="button-host"></div>
      @if (busy()) { <p class="note">Signing you in…</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    }
  `,
  styles: `
    .button-host { min-height: 44px; display: flex; justify-content: center; }
    .note { color: #666; font-size: 0.9rem; text-align: center; }
    .error { background: #fde8e7; color: #b3261e; padding: 10px 12px; border-radius: 6px; }
  `
})
export class GoogleSignInButtonComponent implements AfterViewInit {
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);
  private readonly buttonHost = viewChild<ElementRef<HTMLDivElement>>('buttonHost');

  readonly signedIn = output<void>();

  readonly configured = !!environment.googleClientId;
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  ngAfterViewInit(): void {
    if (this.configured) {
      this.renderWhenScriptReady(0);
    }
  }

  /** Google's script loads asynchronously (index.html); wait for it, but not forever */
  private renderWhenScriptReady(attempt: number): void {
    const host = this.buttonHost()?.nativeElement;
    if (typeof google !== 'undefined' && google.accounts?.id && host) {
      google.accounts.id.initialize({
        client_id: environment.googleClientId,
        // Google calls this outside Angular's zone, so step back in to update the page
        callback: response => this.zone.run(() => this.onCredential(response.credential))
      });
      google.accounts.id.renderButton(host, { theme: 'outline', size: 'large', text: 'signin_with', shape: 'pill' });
      return;
    }
    if (attempt > 50) { // ~10 seconds
      this.error.set('Could not load Google sign-in. Check your internet connection or ad blocker, then reload.');
      return;
    }
    setTimeout(() => this.renderWhenScriptReady(attempt + 1), 200);
  }

  private onCredential(credential: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.loginWithGoogle(credential).subscribe({
      next: () => {
        this.busy.set(false);
        this.signedIn.emit();
      },
      error: err => {
        this.busy.set(false);
        this.error.set(problemMessage(err));
      }
    });
  }
}
