package com.vanrental.backend.security;

/**
 * Who a Google sign-in belongs to.
 * subject = Google's permanent user id ("sub"). Emails can change, so we link customers by this id.
 */
public record GoogleIdentity(String subject, String email, String name) {
}
