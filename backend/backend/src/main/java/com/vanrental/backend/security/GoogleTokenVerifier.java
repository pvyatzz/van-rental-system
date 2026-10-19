package com.vanrental.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Checks a Google ID token (the "credential" the Sign in with Google button gives the browser).
 * We never trust what the browser says about who the user is — we verify Google's signature ourselves:
 *  1. signature: made with one of Google's published keys (downloaded from the JWKS URL and cached)
 *  2. expiry: not expired
 *  3. issuer: really issued by accounts.google.com
 *  4. audience: issued for OUR app's Client ID (a token for another website must not work here)
 *  5. email_verified: Google has confirmed the email address
 */
@Component
public class GoogleTokenVerifier {

    private static final String GOOGLE_KEYS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS_URL).build());
    }

    /** For tests: same checks, but with a decoder that trusts a test key instead of Google's keys */
    GoogleTokenVerifier(String clientId, NimbusJwtDecoder nimbus) {
        this.clientId = clientId;
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), // expiry and "not before"
                check(jwt -> jwt.getIssuer() != null && GOOGLE_ISSUERS.contains(jwt.getIssuer().toString()), "wrong issuer"),
                check(jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId), "wrong audience")));
        this.decoder = nimbus;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    public GoogleIdentity verify(String idToken) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not set up yet (GOOGLE_CLIENT_ID is missing)");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified");
        }
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your Google email address is not verified");
        }
        String name = jwt.getClaimAsString("name");
        String email = jwt.getClaimAsString("email");
        return new GoogleIdentity(jwt.getSubject(), email, name != null ? name : email);
    }

    private static OAuth2TokenValidator<Jwt> check(java.util.function.Predicate<Jwt> rule, String problem) {
        return jwt -> rule.test(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", problem, null));
    }
}
