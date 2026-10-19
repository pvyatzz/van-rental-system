import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from './auth/auth.service';
import { IconComponent } from './shared/icon.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Phone menu open/closed */
  readonly menuOpen = signal(false);

  /** First letter of the signed-in name, shown in the round avatar */
  readonly initial = computed(() => (this.auth.displayName() ?? '?').trim().charAt(0).toUpperCase());

  logout(): void {
    if (this.auth.isCustomer() && typeof google !== 'undefined') {
      google.accounts.id.disableAutoSelect(); // don't silently sign the same Google account back in
    }
    this.auth.logout();
    this.menuOpen.set(false);
    this.router.navigate(['/vehicles']);
  }
}
