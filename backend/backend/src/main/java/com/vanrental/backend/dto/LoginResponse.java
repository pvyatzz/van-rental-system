package com.vanrental.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Returned after logging in (admin password login or customer Google sign-in).
 * roles is ["ADMIN"] or ["CUSTOMER"]; the frontend uses it to show the right menu.
 */
public record LoginResponse(String token, String displayName, String email, List<String> roles, Instant expiresAt) {
}
