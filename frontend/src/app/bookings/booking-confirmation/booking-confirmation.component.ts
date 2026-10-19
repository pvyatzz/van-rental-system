import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Booking } from '../../models/booking';
import { BookingService } from '../../services/booking.service';
import { rentalDays } from '../../shared/dates';
import { problemMessage } from '../../shared/problem';

@Component({
  selector: 'app-booking-confirmation',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './booking-confirmation.component.html',
  styleUrl: './booking-confirmation.component.scss'
})
export class BookingConfirmationComponent implements OnInit {
  private readonly bookingService = inject(BookingService);

  /** The :reference from the route /bookings/:reference (bound by withComponentInputBinding) */
  readonly reference = input.required<string>();

  readonly booking = signal<Booking | null>(null);
  readonly error = signal<string | null>(null);

  readonly days = computed(() => {
    const b = this.booking();
    return b ? rentalDays(b.startDate, b.endDate) : 0;
  });

  ngOnInit(): void {
    this.bookingService.getByReference(this.reference()).subscribe({
      next: booking => this.booking.set(booking),
      error: err => this.error.set(problemMessage(err))
    });
  }
}
