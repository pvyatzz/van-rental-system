// Mirrors com.vanrental.backend.model.Customer
export interface Customer {
  id: number;
  fullName: string;
  contactNumber: string;
  email: string | null;
}

export type NewCustomer = Omit<Customer, 'id'>;
