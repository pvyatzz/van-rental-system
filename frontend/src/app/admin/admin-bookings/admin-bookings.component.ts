import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { catchError, debounceTime, map, of, startWith, switchMap, tap } from 'rxjs';

import { Booking, BookingStatus } from '../../models/booking';
import { Vehicle } from '../../models/vehicle';
import { BookingService } from '../../services/booking.service';
import { VehicleService } from '../../services/vehicle.service';
import { rentalDays } from '../../shared/dates';
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
  styleUrl: './admin-bookings.component.scss'
})
export class AdminBookingsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly bookingService = inject(BookingService);
  private readonly vehicleService = inject(VehicleService);

  readonly statuses: BookingStatus[] = ['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED'];
  readonly actions = ACTIONS;

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
}
