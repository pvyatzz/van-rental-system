import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    if (this.auth.isCustomer() && typeof google !== 'undefined') {
      google.accounts.id.disableAutoSelect(); // don't silently sign the same Google account back in
    }
    this.auth.logout();
    this.router.navigate(['/vehicles']);
  }
}
