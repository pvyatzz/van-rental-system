import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Booking } from '../../models/booking';
import { BookingService } from '../../services/booking.service';
import { problemMessage } from '../../shared/problem';

/** The signed-in customer's bookings (GET /api/bookings/mine) */
@Component({
  selector: 'app-my-bookings',
  imports: [RouterLink, DatePipe, CurrencyPipe],
  template: `
    <section class="page">
      <h1>My bookings</h1>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      } @else if (bookings() === null) {
        <p class="muted">Loading…</p>
      } @else if (bookings()!.length === 0) {
        <p class="muted">You have no bookings yet.</p>
        <a class="btn" routerLink="/vehicles">Browse vans</a>
      } @else {
        <ul>
          @for (b of bookings(); track b.id) {
            <li>
              <a [routerLink]="['/bookings', b.reference]">
                <span class="ref">{{ b.reference }}</span>
                <strong>{{ b.vehicleModel }}</strong>
                <span>{{ b.startDate | date: 'MMM d' }} – {{ b.endDate | date: 'MMM d, y' }}</span>
                <span>{{ b.totalPrice | currency: 'PHP' : 'symbol' : '1.0-0' }}</span>
                <span class="status" [attr.data-status]="b.status">{{ b.status }}</span>
              </a>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    .page { max-width: 720px; margin: 0 auto; padding: 24px 16px 48px; }
    h1 { margin: 0 0 16px; }
    ul { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
    a:not(.btn) { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 16px; background: #fff;
      border: 1px solid #e3e3e3; border-radius: 10px; padding: 14px 16px; color: inherit; text-decoration: none; }
    a:not(.btn):hover { border-color: #1f3b57; }
    .ref { font-family: ui-monospace, Consolas, monospace; color: #555; font-size: 0.85rem; }
    .status { margin-left: auto; padding: 2px 10px; border-radius: 999px; font-size: 0.78rem; font-weight: 600;
      background: #fdecd6; color: #8a4b00; }
    .status[data-status='CONFIRMED'] { background: #e3f4e6; color: #1e6b2e; }
    .status[data-status='COMPLETED'] { background: #e4ecf5; color: #1f3b57; }
    .status[data-status='CANCELLED'] { background: #f1f1f1; color: #777; }
    .muted { color: #777; }
    .error { color: #b3261e; }
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
