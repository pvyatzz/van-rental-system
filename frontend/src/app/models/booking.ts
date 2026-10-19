// Mirrors the backend DTOs in com.vanrental.backend.dto
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

/** The ₱500 down payment: proof uploaded (SUBMITTED), then checked by the admin */
export type PaymentStatus = 'SUBMITTED' | 'VERIFIED' | 'REJECTED';

/**
 * What a signed-in customer sends (CustomerBookingRequest). Dates are 'YYYY-MM-DD' strings.
 * No customer id or name: the backend takes those from the Google sign-in.
 */
export interface BookingRequest {
  vehicleId: number;
  contactNumber: string;
  startDate: string;
  endDate: string;
  purpose: string | null;
  pickupAddress: string | null;
  pickupLatitude: number | null;
  pickupLongitude: number | null;
}

/** What we get back: BookingResponse */
export interface Booking {
  id: number;
  /** Public code shown to the customer, e.g. "VR-7K3P9QXA" */
  reference: string;
  vehicleId: number;
  vehicleModel: string;
  customerId: number;
  customerName: string;
  customerContactNumber: string;
  customerEmail: string | null;
  startDate: string;
  endDate: string;
  purpose: string | null;
  pickupAddress: string | null;
  pickupLatitude: number | null;
  pickupLongitude: number | null;
  status: BookingStatus;
  totalPrice: number;
  /** null for bookings made before down payments existed */
  downPayment: number | null;
  /** totalPrice minus the down payment: what's left to pay at pickup */
  balanceDue: number;
  paymentStatus: PaymentStatus | null;
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
