package com.vanrental.backend.model;

import java.security.SecureRandom;

/**
 * Generates public booking references like "VR-7K3P9QXA".
 * Customers see this code instead of the database id, so nobody can guess other people's bookings
 * by trying /bookings/1, /bookings/2, ...
 */
public final class BookingReference {

    // No 0/O or 1/I/L, so it is easy to read out over the phone
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 8; // 31^8 ≈ 850 billion possibilities
    private static final SecureRandom RANDOM = new SecureRandom(); // unpredictable, unlike java.util.Random

    private BookingReference() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("VR-");
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
