import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap, tap } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { Vehicle } from '../../models/vehicle';
import { BookingService } from '../../services/booking.service';
import { VehicleService } from '../../services/vehicle.service';
import { rentalDays, todayIso } from '../../shared/dates';
import { PickupMapComponent, PickupPoint } from '../../shared/pickup-map/pickup-map.component';
import { IconComponent } from '../../shared/icon.component';
import { problemMessage } from '../../shared/problem';
import { ProofUploadComponent } from '../../shared/proof-upload/proof-upload.component';

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
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, PickupMapComponent, ProofUploadComponent, IconComponent],
  templateUrl: './booking-form.component.html',
  styleUrl: './booking-form.component.scss'
})
export class BookingFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly vehicleService = inject(VehicleService);
  private readonly bookingService = inject(BookingService);
  /** The signed-in customer (name + email from Google), shown at the top of the form */
  protected readonly auth = inject(AuthService);

  /** Filled from the URL query string (?vehicleId=2) thanks to withComponentInputBinding() */
  readonly vehicleId = input<string>();

  readonly today = todayIso();
  readonly vehicles = signal<Vehicle[]>([]);
  readonly availability = signal<AvailabilityState>('idle');
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  /** ₱ amount from the server (GET /api/bookings/payment-info) */
  readonly downPayment = signal<number | null>(null);
  /** The payment screenshot. Files don't fit reactive forms well, so it's a plain signal. */
  readonly paymentProof = signal<File | null>(null);
  readonly proofMissing = signal(false);

  readonly form = this.fb.nonNullable.group({
    vehicleId: [0, [Validators.required, Validators.min(1)]],
    dates: this.fb.nonNullable.group(
      {
        startDate: ['', Validators.required],
        endDate: ['', Validators.required]
      },
      { validators: dateRangeValidator }
    ),
    // Name and email come from Google; we only need a number to call or text
    contactNumber: ['', [Validators.required, Validators.maxLength(20)]],
    purpose: ['', Validators.maxLength(255)],
    // Optional pickup location: typed address and/or a pin from the map
    pickup: this.fb.nonNullable.group({
      address: ['', Validators.maxLength(255)],
      latitude: [null as number | null],
      longitude: [null as number | null]
    })
  });

  /** The form's current value as a signal, so computed() values below update automatically */
  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() }
  );

  readonly selectedVehicle = computed(() =>
    this.vehicles().find(v => v.id === this.formValue().vehicleId) ?? null
  );

  /** The chosen dates, for the summary card */
  readonly formDates = computed(() => this.formValue().dates);

  readonly days = computed(() => {
    const { startDate, endDate } = this.formValue().dates;
    return startDate && endDate && endDate >= startDate ? rentalDays(startDate, endDate) : 0;
  });

  readonly estimatedTotal = computed(() => {
    const vehicle = this.selectedVehicle();
    return vehicle ? vehicle.ratePerDay * this.days() : 0;
  });

  /** What's left to pay at pickup after the down payment */
  readonly estimatedBalance = computed(() => Math.max(0, this.estimatedTotal() - (this.downPayment() ?? 0)));

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
    this.bookingService.paymentInfo().subscribe(info => this.downPayment.set(info.downPayment));

    const preselected = Number(this.vehicleId());
    if (preselected) {
      this.form.controls.vehicleId.setValue(preselected);
    }
  }

  submit(): void {
    const proof = this.paymentProof();
    this.proofMissing.set(!proof);
    if (this.form.invalid || this.availability() !== 'available' || !proof) {
      this.form.markAllAsTouched(); // reveal validation messages on untouched fields
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);

    const value = this.form.getRawValue();
    // One request: the backend links the booking to the signed-in Google account
    this.bookingService.create({
      vehicleId: value.vehicleId,
      contactNumber: value.contactNumber.trim(),
      startDate: value.dates.startDate,
      endDate: value.dates.endDate,
      purpose: value.purpose.trim() || null,
      pickupAddress: value.pickup.address.trim() || null,
      pickupLatitude: value.pickup.latitude,
      pickupLongitude: value.pickup.longitude
    }, proof).subscribe({
      next: booking => this.router.navigate(['/bookings', booking.reference]),
      error: err => {
        this.submitting.set(false);
        this.submitError.set(problemMessage(err));
        if (err instanceof HttpErrorResponse && err.status === 409) {
          this.availability.set('unavailable'); // someone else booked these dates first
        }
      }
    });
  }

  onProofChange(file: File | null): void {
    this.paymentProof.set(file);
    if (file) {
      this.proofMissing.set(false);
    }
  }

  /** Called by <app-pickup-map> when a pin is dropped (or removed → null) */
  onPickupPoint(point: PickupPoint | null): void {
    this.form.controls.pickup.patchValue({
      latitude: point?.latitude ?? null,
      longitude: point?.longitude ?? null
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
}
