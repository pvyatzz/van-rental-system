package com.vanrental.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yearly summary for the admin Reports tab.
 * - revenue      = total of CONFIRMED + COMPLETED bookings (money you can count on)
 * - pendingValue = total of PENDING bookings (possible extra income, not confirmed yet)
 * - bookings     = number of bookings that are not cancelled
 * A booking counts in the month (and year) it starts.
 */
public record ReportResponse(
        int year,
        Totals totals,
        List<MonthStats> months,
        List<VehicleStats> vehicles
) {
    public record Totals(long bookings, long cancelled, BigDecimal revenue, BigDecimal pendingValue,
                         String mostBookedVehicle) {
    }

    /** month = 1 (January) … 12 (December) */
    public record MonthStats(int month, long bookings, BigDecimal revenue, BigDecimal pendingValue) {
    }

    public record VehicleStats(Long vehicleId, String model, long bookings, long rentalDays, BigDecimal revenue) {
    }
}
