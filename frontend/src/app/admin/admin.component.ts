import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { IconComponent } from '../shared/icon.component';

/** Shell for the admin area: tab links + a nested <router-outlet> for the child pages */
@Component({
  selector: 'app-admin',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    <section class="container page">
      <div class="page-header">
        <span class="eyebrow">Admin</span>
        <h1>Dashboard</h1>
        <p>Review bookings and payments, manage your vans and see how the business is doing.</p>
      </div>
      <nav class="tabs" aria-label="Admin sections">
        <a routerLink="bookings" routerLinkActive="active"><app-icon name="calendar" [size]="16" /> Bookings</a>
        <a routerLink="vehicles" routerLinkActive="active"><app-icon name="van" [size]="16" /> Vans</a>
        <a routerLink="reports" routerLinkActive="active"><app-icon name="wallet" [size]="16" /> Reports</a>
      </nav>
      <router-outlet />
    </section>
  `,
  styles: `
    .tabs { display: inline-flex; gap: 4px; padding: 4px; margin-bottom: 24px; border-radius: var(--r-md);
      background: var(--c-surface); border: 1px solid var(--c-border); box-shadow: var(--shadow-sm);
      max-width: 100%; overflow-x: auto; }
    .tabs a { display: inline-flex; align-items: center; gap: 8px; padding: 8px 16px; border-radius: var(--r-sm);
      color: var(--c-text-2); font-weight: 600; text-decoration: none; white-space: nowrap;
      transition: background 0.15s, color 0.15s; }
    .tabs a:hover { color: var(--c-text); background: var(--c-surface-2); }
    .tabs a.active { background: var(--c-primary); color: #fff; }
  `
})
export class AdminComponent {}
