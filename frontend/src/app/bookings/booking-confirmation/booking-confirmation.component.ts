import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../auth/auth.service';
import { Booking } from '../../models/booking';
import { BookingService } from '../../services/booking.service';
import { rentalDays } from '../../shared/dates';
import { googleMapsUrl } from '../../shared/maps';
import { problemMessage } from '../../shared/problem';
import { ProofUploadComponent } from '../../shared/proof-upload/proof-upload.component';

@Component({
  selector: 'app-booking-confirmation',
  imports: [CurrencyPipe, DatePipe, RouterLink, ProofUploadComponent],
  templateUrl: './booking-confirmation.component.html',
  styleUrl: './booking-confirmation.component.scss'
})
export class BookingConfirmationComponent implements OnInit {
  private readonly bookingService = inject(BookingService);
  protected readonly auth = inject(AuthService);

  /** The :reference from the route /bookings/:reference (bound by withComponentInputBinding) */
  readonly reference = input.required<string>();

  readonly mapUrl = googleMapsUrl;
  readonly booking = signal<Booking | null>(null);
  readonly error = signal<string | null>(null);

  readonly newProof = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly reuploadError = signal<string | null>(null);

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

  paymentLabel(b: Booking): string {
    switch (b.paymentStatus) {
      case 'VERIFIED': return 'received ✓';
      case 'REJECTED': return 'not confirmed: please upload a new proof';
      case 'SUBMITTED': return 'proof received, being checked';
      default: return '';
    }
  }

  reupload(b: Booking): void {
    const file = this.newProof();
    if (!file) {
      return;
    }
    this.uploading.set(true);
    this.reuploadError.set(null);
    this.bookingService.reuploadProof(b.reference, file).subscribe({
      next: updated => {
        this.booking.set(updated);
        this.uploading.set(false);
        this.newProof.set(null);
      },
      error: err => {
        this.uploading.set(false);
        this.reuploadError.set(problemMessage(err));
      }
    });
  }
}
