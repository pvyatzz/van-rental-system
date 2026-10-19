import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'vehicles', pathMatch: 'full' },
  {
    path: 'vehicles',
    loadComponent: () =>
      import('./vehicles/vehicle-list/vehicle-list.component').then(m => m.VehicleListComponent)
  }
];
