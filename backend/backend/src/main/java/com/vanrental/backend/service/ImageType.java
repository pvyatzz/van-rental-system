package com.vanrental.backend.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Recognises an image by its first bytes ("magic numbers"), because the file name and the Content-Type the
 * browser sends can be anything. A .exe renamed to proof.jpg still starts with "MZ", not FF D8 FF.
 */
public enum ImageType {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public final String contentType;

    ImageType(String contentType) {
        this.contentType = contentType;
    }

    public static Optional<ImageType> detect(byte[] data) {
        if (startsWith(data, 0, JPEG_START)) {
            return Optional.of(JPEG);
        }
        if (startsWith(data, 0, PNG_START)) {
            return Optional.of(PNG);
        }
        // WebP: "RIFF" + 4 size bytes + "WEBP"
        if (startsWith(data, 0, "RIFF".getBytes()) && startsWith(data, 8, "WEBP".getBytes())) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
