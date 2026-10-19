import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Booking } from '../../models/booking';
import { BookingService } from '../../services/booking.service';
import { IconComponent } from '../../shared/icon.component';
import { problemMessage } from '../../shared/problem';

/** The signed-in customer's bookings (GET /api/bookings/mine) */
@Component({
  selector: 'app-my-bookings',
  imports: [RouterLink, DatePipe, CurrencyPipe, IconComponent],
  template: `
    <section class="container page narrow">
      <div class="page-header head">
        <div>
          <span class="eyebrow">Your account</span>
          <h1>My bookings</h1>
          <p>Tap a booking to see its details and payment status.</p>
        </div>
        <a class="btn" routerLink="/book">New booking</a>
      </div>

      @if (error()) {
        <p class="alert">{{ error() }}</p>
      } @else if (bookings() === null) {
        @for (i of [1, 2]; track i) { <div class="skeleton row-skeleton"></div> }
      } @else if (bookings()!.length === 0) {
        <div class="card empty-state">
          <app-icon name="van" [size]="40" />
          <h2>No bookings yet</h2>
          <p>When you book a van it will show up here.</p>
          <a class="btn" routerLink="/vehicles">Browse vans</a>
        </div>
      } @else {
        <ul>
          @for (b of bookings(); track b.id) {
            <li>
              <a class="card booking" [routerLink]="['/bookings', b.reference]">
                <span class="icon"><app-icon name="van" [size]="22" /></span>
                <div class="main">
                  <strong>{{ b.vehicleModel }}</strong>
                  <span class="dates"><app-icon name="calendar" [size]="14" />
                    {{ b.startDate | date: 'MMM d' }} – {{ b.endDate | date: 'MMM d, y' }}</span>
                  <span class="ref">{{ b.reference }}</span>
                </div>
                <div class="side">
                  <span class="badge" [attr.data-status]="b.status">{{ b.status }}</span>
                  <strong>{{ b.totalPrice | currency: 'PHP' : 'symbol' : '1.0-0' }}</strong>
                </div>
              </a>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    .narrow { max-width: 820px; }
    .head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-end; gap: 16px; }
    ul { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 12px; }
    .booking { display: flex; align-items: center; gap: 16px; padding: 16px 18px; color: inherit;
      text-decoration: none; transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s; }
    .booking:hover { border-color: #bfd0ee; box-shadow: var(--shadow-md); transform: translateY(-1px); }
    .icon { display: inline-grid; place-items: center; width: 46px; height: 46px; border-radius: 12px;
      background: var(--c-primary-soft); color: var(--c-primary); flex-shrink: 0; }
    .main { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
    .dates { display: inline-flex; align-items: center; gap: 6px; color: var(--c-text-2); font-size: 0.9rem; }
    .ref { font-family: ui-monospace, Consolas, monospace; color: var(--c-text-3); font-size: 0.8rem; }
    .side { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
    .empty-state { display: flex; flex-direction: column; align-items: center; color: var(--c-text-3); }
    .empty-state h2 { color: var(--c-text); margin-top: 12px; }
    .row-skeleton { height: 84px; margin-bottom: 12px; }
    @media (max-width: 480px) { .icon { display: none; } }
  `
})
export class MyBookingsComponent implements OnInit {
  private readonly bookingService = inject(BookingService);

  readonly bookings = signal<Booking[] | null>(null);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.bookingService.mine().subscribe({
      next: bookings => this.bookings.set(bookings),
      error: err => this.error.set(problemMessage(err))
    });
  }
}
