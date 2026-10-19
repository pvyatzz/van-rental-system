import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/** Shell for the admin area: tab links + a nested <router-outlet> for the child pages */
@Component({
  selector: 'app-admin',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <section class="admin">
      <h1>Admin</h1>
      <nav class="tabs">
        <a routerLink="bookings" routerLinkActive="active">Bookings</a>
        <a routerLink="vehicles" routerLinkActive="active">Vans</a>
        <a routerLink="reports" routerLinkActive="active">Reports</a>
      </nav>
      <router-outlet />
    </section>
  `,
  styles: `
    .admin { max-width: 1200px; margin: 0 auto; padding: 24px 16px 48px; }
    h1 { margin: 0 0 12px; }
    .tabs { display: flex; gap: 4px; border-bottom: 1px solid #d9dce1; margin-bottom: 20px; }
    .tabs a { padding: 8px 16px; color: #555; text-decoration: none; border-bottom: 3px solid transparent; }
    .tabs a.active { color: #1f3b57; font-weight: 600; border-bottom-color: #1f3b57; }
  `
})
export class AdminComponent {}
