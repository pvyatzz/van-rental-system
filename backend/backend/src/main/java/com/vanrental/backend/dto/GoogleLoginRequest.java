package com.vanrental.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** The ID token (JWT) the "Sign in with Google" button gives the browser */
public record GoogleLoginRequest(@NotBlank String credential) {
}
