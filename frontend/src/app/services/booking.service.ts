import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Availability, Booking, BookingRequest } from '../models/booking';

@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/bookings`;

  getById(id: number): Observable<Booking> {
    return this.http.get<Booking>(`${this.baseUrl}/${id}`);
  }

  create(request: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>(this.baseUrl, request);
  }

  checkAvailability(vehicleId: number, startDate: string, endDate: string): Observable<Availability> {
    // HttpClient builds the ?vehicleId=..&startDate=.. query string (and URL-encodes it)
    return this.http.get<Availability>(`${this.baseUrl}/availability`, {
      params: { vehicleId, startDate, endDate }
    });
  }
}
