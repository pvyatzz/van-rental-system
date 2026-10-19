import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { Vehicle, VehicleStatus } from '../../models/vehicle';
import { VehicleService } from '../../services/vehicle.service';
import { problemMessage } from '../../shared/problem';

@Component({
  selector: 'app-admin-vehicles',
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './admin-vehicles.component.html',
  styleUrl: './admin-vehicles.component.scss'
})
export class AdminVehiclesComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly vehicleService = inject(VehicleService);

  readonly statuses: { value: VehicleStatus; label: string }[] = [
    { value: 'AVAILABLE', label: 'Available' },
    { value: 'BOOKED', label: 'Booked' },
    { value: 'UNDER_MAINTENANCE', label: 'Under maintenance' }
  ];

  /** Photos already in frontend/public/images (copied from the van-img folder) */
  readonly knownPhotos = ['/images/l300-fb.png', '/images/h100.png'];

  readonly vehicles = signal<Vehicle[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /** Which van is being edited (null = none). Only one edit form is open at a time. */
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly savedId = signal<number | null>(null);

  readonly editForm = this.fb.nonNullable.group({
    model: ['', [Validators.required, Validators.maxLength(255)]],
    capacity: [1, [Validators.required, Validators.min(1)]],
    ratePerDay: [0, [Validators.required, Validators.min(0)]],
    status: ['AVAILABLE' as VehicleStatus, Validators.required],
    photoUrl: ['']
  });

  /** Live photo URL for the preview while typing */
  readonly photoPreview = toSignal(this.editForm.controls.photoUrl.valueChanges, { initialValue: '' });

  ngOnInit(): void {
    this.vehicleService.getAll().subscribe({
      next: vehicles => {
        this.vehicles.set(vehicles);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(problemMessage(err));
        this.loading.set(false);
      }
    });
  }

  startEdit(vehicle: Vehicle): void {
    this.editingId.set(vehicle.id);
    this.saveError.set(null);
    this.savedId.set(null);
    // reset() fills the form with this van's current values and clears touched/dirty flags
    this.editForm.reset({
      model: vehicle.model,
      capacity: vehicle.capacity,
      ratePerDay: vehicle.ratePerDay,
      status: vehicle.status,
      photoUrl: vehicle.photoUrl ?? ''
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  save(): void {
    const id = this.editingId();
    if (id === null || this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const value = this.editForm.getRawValue();
    this.saving.set(true);
    this.saveError.set(null);
    this.vehicleService.update(id, {
      model: value.model.trim(),
      capacity: value.capacity,
      ratePerDay: value.ratePerDay,
      status: value.status,
      photoUrl: value.photoUrl.trim() || null
    }).subscribe({
      next: updated => {
        this.vehicles.update(list => list.map(v => (v.id === updated.id ? updated : v)));
        this.saving.set(false);
        this.editingId.set(null);
        this.savedId.set(updated.id); // shows a "Saved" note on that card
      },
      error: err => {
        this.saving.set(false);
        this.saveError.set(problemMessage(err));
      }
    });
  }

  statusLabel(status: VehicleStatus): string {
    return this.statuses.find(s => s.value === status)?.label ?? status;
  }

  showError(name: string): boolean {
    const control = this.editForm.get(name);
    return !!control && control.invalid && control.touched;
  }
}
