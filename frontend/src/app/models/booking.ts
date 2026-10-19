// Mirrors the backend DTOs in com.vanrental.backend.dto
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

/** What we send: BookingRequest. Dates are 'YYYY-MM-DD' strings. */
export interface BookingRequest {
  vehicleId: number;
  customerId: number;
  startDate: string;
  endDate: string;
  purpose: string | null;
}

/** What we get back: BookingResponse */
export interface Booking {
  id: number;
  vehicleId: number;
  vehicleModel: string;
  customerId: number;
  customerName: string;
  customerContactNumber: string;
  startDate: string;
  endDate: string;
  purpose: string | null;
  status: BookingStatus;
  totalPrice: number;
  createdAt: string;
}

/** Optional search filters for GET /api/bookings */
export interface BookingFilters {
  vehicleId?: number;
  status?: BookingStatus | '';
  from?: string;
  to?: string;
}

export interface Availability {
  vehicleId: number;
  startDate: string;
  endDate: string;
  available: boolean;
}
