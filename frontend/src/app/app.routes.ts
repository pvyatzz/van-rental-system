import { Routes } from '@angular/router';

import { adminGuard } from './auth/admin.guard';
import { customerGuard } from './auth/customer.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'vehicles', pathMatch: 'full' },
  {
    path: 'vehicles',
    loadComponent: () =>
      import('./vehicles/vehicle-list/vehicle-list.component').then(m => m.VehicleListComponent)
  },
  {
    // optional ?vehicleId=2 preselects a van. Booking needs a Google sign-in (customerGuard → /login)
    path: 'book',
    canActivate: [customerGuard],
    loadComponent: () =>
      import('./bookings/booking-form/booking-form.component').then(m => m.BookingFormComponent)
  },
  {
    path: 'my-bookings',
    canActivate: [customerGuard],
    loadComponent: () =>
      import('./bookings/my-bookings/my-bookings.component').then(m => m.MyBookingsComponent)
  },
  {
    // old customer sign-in address; customers and admin now share /login (query params are kept)
    path: 'signin',
    redirectTo: 'login'
  },
  {
    // the public reference code (VR-XXXXXXXX), not the database id
    path: 'bookings/:reference',
    loadComponent: () =>
      import('./bookings/booking-confirmation/booking-confirmation.component')
        .then(m => m.BookingConfirmationComponent)
  },
  {
    path: 'login',
    loadComponent: () => import('./login/login.component').then(m => m.LoginComponent)
  },
  {
    // Admin area: a shell component with its own <router-outlet> for the child pages.
    // canActivate runs adminGuard first: not logged in → /login
    path: 'admin',
    canActivate: [adminGuard],
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
      },
      {
        path: 'reports',
        loadComponent: () =>
          import('./admin/admin-reports/admin-reports.component').then(m => m.AdminReportsComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'vehicles' }
];
