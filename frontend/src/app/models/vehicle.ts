// Mirrors com.vanrental.backend.model.Vehicle / VehicleStatus
export type VehicleStatus = 'AVAILABLE' | 'BOOKED' | 'UNDER_MAINTENANCE';

export interface Vehicle {
  id: number;
  model: string;
  capacity: number;
  ratePerDay: number;
  status: VehicleStatus;
  photoUrl: string | null;
}
