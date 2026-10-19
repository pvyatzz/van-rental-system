import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { catchError, debounceTime, map, of, startWith, switchMap, tap } from 'rxjs';

import { Booking, BookingStatus, PaymentStatus } from '../../models/booking';
import { Vehicle } from '../../models/vehicle';
import { BookingService } from '../../services/booking.service';
import { VehicleService } from '../../services/vehicle.service';
import { rentalDays } from '../../shared/dates';
import { googleMapsUrl } from '../../shared/maps';
import { problemMessage } from '../../shared/problem';

interface StatusAction {
  status: BookingStatus;
  label: string;
}

/** Mirrors BookingStatus.canChangeTo() on the backend: which buttons each status gets */
const ACTIONS: Record<BookingStatus, StatusAction[]> = {
  PENDING: [{ status: 'CONFIRMED', label: 'Confirm' }, { status: 'CANCELLED', label: 'Cancel' }],
  CONFIRMED: [{ status: 'COMPLETED', label: 'Mark completed' }, { status: 'CANCELLED', label: 'Cancel' }],
  CANCELLED: [],
  COMPLETED: []
};

@Component({
  selector: 'app-admin-bookings',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './admin-bookings.component.html',
  styleUrl: './admin-bookings.component.scss',
  // Escape closes the proof dialog, wherever the keyboard focus is
  host: { '(document:keydown.escape)': 'closeProof()' }
})
export class AdminBookingsComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly bookingService = inject(BookingService);
  private readonly vehicleService = inject(VehicleService);

  readonly statuses: BookingStatus[] = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED'];
  readonly actions = ACTIONS;
  readonly mapUrl = googleMapsUrl;

  readonly vehicles = signal<Vehicle[]>([]);
  readonly bookings = signal<Booking[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  /** id of the booking whose button was just clicked (disables its buttons while saving) */
  readonly busyId = signal<number | null>(null);

  readonly filters = this.fb.nonNullable.group({
    vehicleId: [0],
    status: ['' as BookingStatus | ''],
    from: [''],
    to: ['']
  });

  readonly pendingCount = computed(() => this.bookings().filter(b => b.status === 'PENDING').length);

  constructor() {
    // Reload the table whenever a filter changes
    this.filters.valueChanges.pipe(
      startWith(null),
      map(() => this.filters.getRawValue()),
      tap(() => {
        this.loading.set(true);
        this.error.set(null);
      }),
      debounceTime(250),
      switchMap(f =>
        this.bookingService.search({
          vehicleId: f.vehicleId || undefined, // 0 = "All vans" → don't send the filter
          status: f.status,
          from: f.from,
          to: f.to
        }).pipe(
          catchError(err => {
            this.error.set(problemMessage(err));
            return of([] as Booking[]);
          })
        )
      ),
      takeUntilDestroyed()
    ).subscribe(bookings => {
      this.bookings.set(bookings);
      this.loading.set(false);
    });
  }

  ngOnInit(): void {
    this.vehicleService.getAll().subscribe(vehicles => this.vehicles.set(vehicles));
  }

  clearFilters(): void {
    this.filters.reset(); // nonNullable → back to the initial values (0 and '')
  }

  changeStatus(booking: Booking, action: StatusAction): void {
    if (action.status === 'CANCELLED'
        && !confirm(`Cancel booking #${booking.id} for ${booking.customerName}? This can't be undone.`)) {
      return;
    }
    this.busyId.set(booking.id);
    this.actionError.set(null);
    this.bookingService.changeStatus(booking.id, action.status).subscribe({
      next: updated => {
        // Replace just that row; signals need a NEW array so Angular notices the change
        this.bookings.update(list => list.map(b => (b.id === updated.id ? updated : b)));
        this.busyId.set(null);
      },
      error: err => {
        this.actionError.set(`Booking #${booking.id}: ${problemMessage(err)}`);
        this.busyId.set(null);
      }
    });
  }

  days(booking: Booking): number {
    return rentalDays(booking.startDate, booking.endDate);
  }

  // ---- Down payment ----

  readonly paymentLabels: Record<PaymentStatus, string> = {
    SUBMITTED: 'to check',
    VERIFIED: 'verified',
    REJECTED: 'rejected'
  };

  /** The booking whose proof is open in the dialog */
  readonly proofFor = signal<Booking | null>(null);
  readonly proofUrl = signal<string | null>(null);
  readonly proofError = signal<string | null>(null);
  readonly reviewing = signal(false);

  /** Mirrors the backend rule: a booking with a down payment can only be confirmed once it's verified */
  needsPaymentCheck(b: Booking): boolean {
    return b.paymentStatus !== null && b.paymentStatus !== 'VERIFIED';
  }

  openProof(b: Booking): void {
    this.closeProof();
    this.proofFor.set(b);
    // <img src="/api/..."> can't send our login token, so download the image with HttpClient
    // (which goes through the auth interceptor) and show it from a local object URL
    this.bookingService.paymentProof(b.id).subscribe({
      next: blob => this.proofUrl.set(URL.createObjectURL(blob)),
      error: err => this.proofError.set(problemMessage(err))
    });
  }

  closeProof(): void {
    const url = this.proofUrl();
    if (url) {
      URL.revokeObjectURL(url); // free the image from memory
    }
    this.proofUrl.set(null);
    this.proofError.set(null);
    this.proofFor.set(null);
  }

  review(b: Booking, decision: PaymentStatus): void {
    if (decision === 'REJECTED'
        && !confirm(`Reject the down payment for ${b.reference}? The customer will be asked to upload a new proof.`)) {
      return;
    }
    this.reviewing.set(true);
    this.bookingService.reviewPayment(b.id, decision).subscribe({
      next: updated => {
        this.bookings.update(list => list.map(x => (x.id === updated.id ? updated : x)));
        this.proofFor.set(updated);
        this.reviewing.set(false);
      },
      error: err => {
        this.proofError.set(problemMessage(err));
        this.reviewing.set(false);
      }
    });
  }

  ngOnDestroy(): void {
    this.closeProof();
  }
}
