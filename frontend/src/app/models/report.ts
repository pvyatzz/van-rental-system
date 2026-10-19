// Mirrors com.vanrental.backend.dto.ReportResponse
export interface MonthStats {
  month: number; // 1 = January
  bookings: number;
  revenue: number;
  pendingValue: number;
}

export interface VehicleStats {
  vehicleId: number;
  model: string;
  bookings: number;
  rentalDays: number;
  revenue: number;
}

export interface YearlyReport {
  year: number;
  totals: {
    bookings: number;
    cancelled: number;
    revenue: number;
    pendingValue: number;
    mostBookedVehicle: string | null;
  };
  months: MonthStats[];
  vehicles: VehicleStats[];
}
