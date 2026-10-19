import { CurrencyPipe } from '@angular/common';
import { Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Vehicle, VehicleStatus } from '../../models/vehicle';
import { VehicleService } from '../../services/vehicle.service';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-vehicle-list',
  imports: [CurrencyPipe, RouterLink, IconComponent],
  templateUrl: './vehicle-list.component.html',
  styleUrl: './vehicle-list.component.scss'
})
export class VehicleListComponent implements OnInit {
  private readonly vehicleService = inject(VehicleService);
  private readonly fleet = viewChild.required<ElementRef<HTMLElement>>('fleet');

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

  /** "See our vans": a plain href="#fleet" would leave the Angular page, so scroll instead */
  scrollToFleet(): void {
    this.fleet().nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
