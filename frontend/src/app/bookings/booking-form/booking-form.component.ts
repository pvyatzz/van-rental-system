import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap, tap } from 'rxjs';

import { Customer, NewCustomer } from '../../models/customer';
import { Vehicle } from '../../models/vehicle';
import { BookingService } from '../../services/booking.service';
import { CustomerService } from '../../services/customer.service';
import { VehicleService } from '../../services/vehicle.service';
import { rentalDays, todayIso } from '../../shared/dates';
import { problemMessage } from '../../shared/problem';

type AvailabilityState = 'idle' | 'checking' | 'available' | 'unavailable' | 'error';

/** Group validator: start must not be in the past, end must not be before start. */
function dateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const { startDate, endDate } = group.value as { startDate: string; endDate: string };
  if (!startDate || !endDate) {
    return null; // the 'required' validators on each field handle empty values
  }
  if (startDate < todayIso()) {
    return { startInPast: true }; // 'YYYY-MM-DD' strings compare correctly as text
  }
  if (endDate < startDate) {
    return { endBeforeStart: true };
  }
  return null;
}

@Component({
  selector: 'app-booking-form',
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './booking-form.component.html',
  styleUrl: './booking-form.component.scss'
})
export class BookingFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly vehicleService = inject(VehicleService);
  private readonly customerService = inject(CustomerService);
  private readonly bookingService = inject(BookingService);

  /** Filled from the URL query string (?vehicleId=2) thanks to withComponentInputBinding() */
  readonly vehicleId = input<string>();

  readonly today = todayIso();
  readonly vehicles = signal<Vehicle[]>([]);
  readonly availability = signal<AvailabilityState>('idle');
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  /** Remembered so a retry (e.g. after picking new dates) doesn't create the same customer twice */
  private savedCustomer: Customer | null = null;

  readonly form = this.fb.nonNullable.group({
    vehicleId: [0, [Validators.required, Validators.min(1)]],
    dates: this.fb.nonNullable.group(
      {
        startDate: ['', Validators.required],
        endDate: ['', Validators.required]
      },
      { validators: dateRangeValidator }
    ),
    customer: this.fb.nonNullable.group({
      fullName: ['', [Validators.required, Validators.maxLength(100)]],
      contactNumber: ['', [Validators.required, Validators.maxLength(20)]],
      email: ['', [Validators.email, Validators.maxLength(100)]]
    }),
    purpose: ['', Validators.maxLength(255)]
  });

  /** The form's current value as a signal, so computed() values below update automatically */
  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() }
  );

  readonly selectedVehicle = computed(() =>
    this.vehicles().find(v => v.id === this.formValue().vehicleId) ?? null
  );

  readonly days = computed(() => {
    const { startDate, endDate } = this.formValue().dates;
    return startDate && endDate && endDate >= startDate ? rentalDays(startDate, endDate) : 0;
  });

  readonly estimatedTotal = computed(() => {
    const vehicle = this.selectedVehicle();
    return vehicle ? vehicle.ratePerDay * this.days() : 0;
  });

  constructor() {
    // Real-time availability: every time the van or dates change, ask the backend.
    this.form.valueChanges.pipe(
      startWith(null),
      map(() => this.form.getRawValue()),
      map(v => ({ vehicleId: v.vehicleId, startDate: v.dates.startDate, endDate: v.dates.endDate })),
      // typing in the name field also fires valueChanges; ignore it unless van/dates changed
      distinctUntilChanged((a, b) =>
        a.vehicleId === b.vehicleId && a.startDate === b.startDate && a.endDate === b.endDate),
      tap(() => this.availability.set('idle')),
      debounceTime(300), // wait until the user stops changing things for 300 ms
      // switchMap cancels the previous request if a newer one starts → no out-of-order answers
      switchMap(q => this.checkAvailability(q.vehicleId, q.startDate, q.endDate)),
      takeUntilDestroyed() // unsubscribe automatically when the component is destroyed
    ).subscribe(state => this.availability.set(state));
  }

  ngOnInit(): void {
    this.vehicleService.getAll().subscribe({
      next: vehicles => this.vehicles.set(vehicles),
      error: err => this.submitError.set(problemMessage(err))
    });

    const preselected = Number(this.vehicleId());
    if (preselected) {
      this.form.controls.vehicleId.setValue(preselected);
    }
  }

  submit(): void {
    if (this.form.invalid || this.availability() !== 'available') {
      this.form.markAllAsTouched(); // reveal validation messages on untouched fields
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);

    const value = this.form.getRawValue();
    const customer: NewCustomer = {
      fullName: value.customer.fullName.trim(),
      contactNumber: value.customer.contactNumber.trim(),
      email: value.customer.email.trim() || null
    };

    // Two requests in sequence: create the customer, then the booking that references them
    this.getOrCreateCustomer(customer).pipe(
      switchMap(saved => this.bookingService.create({
        vehicleId: value.vehicleId,
        customerId: saved.id,
        startDate: value.dates.startDate,
        endDate: value.dates.endDate,
        purpose: value.purpose.trim() || null
      }))
    ).subscribe({
      next: booking => this.router.navigate(['/bookings', booking.id]),
      error: err => {
        this.submitting.set(false);
        this.submitError.set(problemMessage(err));
        if (err instanceof HttpErrorResponse && err.status === 409) {
          this.availability.set('unavailable'); // someone else booked these dates first
        }
      }
    });
  }

  /** Small helper for the template: show a field's error only after the user touched it */
  showError(path: string): boolean {
    const control = this.form.get(path);
    return !!control && control.invalid && control.touched;
  }

  vehicleLabel(vehicle: Vehicle): string {
    return `${vehicle.model} #${vehicle.id} (${vehicle.capacity} seats)`;
  }

  private checkAvailability(vehicleId: number, startDate: string, endDate: string): Observable<AvailabilityState> {
    if (!vehicleId || this.form.controls.dates.invalid) {
      return of('idle');
    }
    this.availability.set('checking');
    return this.bookingService.checkAvailability(vehicleId, startDate, endDate).pipe(
      map(result => (result.available ? 'available' : 'unavailable') as AvailabilityState),
      catchError(() => of('error' as AvailabilityState))
    );
  }

  private getOrCreateCustomer(customer: NewCustomer): Observable<Customer> {
    const saved = this.savedCustomer;
    if (saved && saved.fullName === customer.fullName
        && saved.contactNumber === customer.contactNumber && saved.email === customer.email) {
      return of(saved);
    }
    return this.customerService.create(customer).pipe(tap(created => (this.savedCustomer = created)));
  }
}
