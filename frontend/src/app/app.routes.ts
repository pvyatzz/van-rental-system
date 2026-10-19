import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'vehicles', pathMatch: 'full' },
  {
    path: 'vehicles',
    loadComponent: () =>
      import('./vehicles/vehicle-list/vehicle-list.component').then(m => m.VehicleListComponent)
  },
  {
    // optional ?vehicleId=2 preselects a van
    path: 'book',
    loadComponent: () =>
      import('./bookings/booking-form/booking-form.component').then(m => m.BookingFormComponent)
  },
  {
    path: 'bookings/:id',
    loadComponent: () =>
      import('./bookings/booking-confirmation/booking-confirmation.component')
        .then(m => m.BookingConfirmationComponent)
  },
  { path: '**', redirectTo: 'vehicles' }
];
