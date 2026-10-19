import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Vehicle, VehicleStatus } from '../../models/vehicle';
import { VehicleService } from '../../services/vehicle.service';

@Component({
  selector: 'app-vehicle-list',
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './vehicle-list.component.html',
  styleUrl: './vehicle-list.component.scss'
})
export class VehicleListComponent implements OnInit {
  private readonly vehicleService = inject(VehicleService);

  readonly vehicles = signal<Vehicle[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly statusLabels: Record<VehicleStatus, string> = {
    AVAILABLE: 'Available',
    BOOKED: 'Booked',
    UNDER_MAINTENANCE: 'Under maintenance'
  };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.vehicleService.getAll().subscribe({
      next: vehicles => {
        this.vehicles.set(vehicles);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load vehicles. Is the backend running on port 8080?');
        this.loading.set(false);
      }
    });
  }
}
