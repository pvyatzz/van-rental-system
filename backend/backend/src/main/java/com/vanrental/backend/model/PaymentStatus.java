package com.vanrental.backend.model;

/** The ₱500 down payment: the customer uploaded proof (SUBMITTED), the admin checked it (VERIFIED or REJECTED). */
public enum PaymentStatus {
    SUBMITTED,
    VERIFIED,
    REJECTED
}
