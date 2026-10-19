import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Availability, Booking, BookingFilters, BookingRequest, BookingStatus, PaymentStatus } from '../models/booking';

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

  /**
   * Signed-in customer: the booking + the payment screenshot in ONE multipart/form-data request.
   * The JSON goes in as a Blob with type application/json so Spring reads it as the "booking" part.
   */
  create(request: BookingRequest, paymentProof: File): Observable<Booking> {
    const form = new FormData();
    form.append('booking', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    form.append('paymentProof', paymentProof);
    // Don't set Content-Type yourself: the browser adds it with the multipart "boundary"
    return this.http.post<Booking>(this.baseUrl, form);
  }

  /** How much the down payment is (from the server, so both always agree) */
  paymentInfo(): Observable<{ downPayment: number }> {
    return this.http.get<{ downPayment: number }>(`${this.baseUrl}/payment-info`);
  }

  /** Customer: upload a new proof after a rejection */
  reuploadProof(reference: string, paymentProof: File): Observable<Booking> {
    const form = new FormData();
    form.append('paymentProof', paymentProof);
    return this.http.post<Booking>(`${this.baseUrl}/ref/${encodeURIComponent(reference)}/payment-proof`, form);
  }

  /** Admin: the proof image as a Blob (an <img src> can't send our login token, so we fetch it) */
  paymentProof(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/payment-proof`, { responseType: 'blob' });
  }

  /** Admin: VERIFIED or REJECTED */
  reviewPayment(id: number, status: PaymentStatus): Observable<Booking> {
    return this.http.patch<Booking>(`${this.baseUrl}/${id}/payment`, { status });
  }

  /** The signed-in customer's own bookings */
  mine(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${this.baseUrl}/mine`);
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
