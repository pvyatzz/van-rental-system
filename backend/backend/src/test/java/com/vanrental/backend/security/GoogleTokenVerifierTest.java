package com.vanrental.backend.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Google tokens can't be made in a test, so we play "Google" with our own RSA key pair:
 * tokens are signed with the private key and the verifier trusts only the matching public key.
 * Everything else (issuer, audience, expiry, email_verified checks) is the production code.
 */
class GoogleTokenVerifierTest {

    static final String CLIENT_ID = "my-app.apps.googleusercontent.com";

    NimbusJwtEncoder fakeGoogle;
    GoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        RSAKey rsa = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate()).keyID("test-key").build();
        fakeGoogle = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
        verifier = new GoogleTokenVerifier(CLIENT_ID,
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    }

    /** A token that is valid in every way; each test changes one thing */
    private String token(Consumer<JwtClaimsSet.Builder> change) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .subject("google-user-123")
                .audience(List.of(CLIENT_ID))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", "maria@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Maria Santos");
        change.accept(claims);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build();
        return fakeGoogle.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    @Test
    void validTokenGivesTheGoogleIdentity() {
        GoogleIdentity identity = verifier.verify(token(c -> { }));
        assertThat(identity).isEqualTo(new GoogleIdentity("google-user-123", "maria@gmail.com", "Maria Santos"));
    }

    @Test
    void tokenForAnotherAppIsRejected() {
        assertRejected(token(c -> c.audience(List.of("someone-elses-app.apps.googleusercontent.com"))));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        assertRejected(token(c -> c.issuer("https://evil.example")));
    }

    @Test
    void expiredTokenIsRejected() {
        assertRejected(token(c -> c.issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600))));
    }

    @Test
    void unverifiedEmailIsRejected() {
        assertRejected(token(c -> c.claim("email_verified", false)));
    }

    @Test
    void garbageIsRejected() {
        assertRejected("not-a-token");
    }

    @Test
    void withoutClientIdItSaysNotSetUp() {
        GoogleTokenVerifier notConfigured = new GoogleTokenVerifier("");
        assertThatThrownBy(() -> notConfigured.verify("anything"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private void assertRejected(String idToken) {
        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
