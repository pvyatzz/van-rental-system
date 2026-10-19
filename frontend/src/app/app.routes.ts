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
  {
    // Admin area: a shell component with its own <router-outlet> for the child pages
    path: 'admin',
    loadComponent: () => import('./admin/admin.component').then(m => m.AdminComponent),
    children: [
      { path: '', redirectTo: 'bookings', pathMatch: 'full' },
      {
        path: 'bookings',
        loadComponent: () =>
          import('./admin/admin-bookings/admin-bookings.component').then(m => m.AdminBookingsComponent)
      },
      {
        path: 'vehicles',
        loadComponent: () =>
          import('./admin/admin-vehicles/admin-vehicles.component').then(m => m.AdminVehiclesComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'vehicles' }
];
