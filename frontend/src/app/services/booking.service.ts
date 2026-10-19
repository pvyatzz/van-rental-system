import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Availability, Booking, BookingFilters, BookingRequest, BookingStatus } from '../models/booking';

@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/bookings`;

  /** Lists bookings; empty filter values are left out of the query string */
  search(filters: BookingFilters = {}): Observable<Booking[]> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, value); // HttpParams is immutable: set() returns a new copy
      }
    }
    return this.http.get<Booking[]>(this.baseUrl, { params });
  }

  getById(id: number): Observable<Booking> {
    return this.http.get<Booking>(`${this.baseUrl}/${id}`);
  }

  /** Public lookup used by the customer's confirmation page */
  getByReference(reference: string): Observable<Booking> {
    return this.http.get<Booking>(`${this.baseUrl}/ref/${encodeURIComponent(reference)}`);
  }

  create(request: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>(this.baseUrl, request);
  }

  changeStatus(id: number, status: BookingStatus): Observable<Booking> {
    return this.http.patch<Booking>(`${this.baseUrl}/${id}/status`, { status });
  }

  checkAvailability(vehicleId: number, startDate: string, endDate: string): Observable<Availability> {
    // HttpClient builds the ?vehicleId=..&startDate=.. query string (and URL-encodes it)
    return this.http.get<Availability>(`${this.baseUrl}/availability`, {
      params: { vehicleId, startDate, endDate }
    });
  }
}
