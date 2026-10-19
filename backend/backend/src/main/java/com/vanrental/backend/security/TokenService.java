package com.vanrental.backend.security;

import com.vanrental.backend.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Creates the signed token (JWT) the frontend sends with every request that needs a login. */
@Service
public class TokenService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    private final JwtEncoder jwtEncoder;
    private final Duration validity;

    public TokenService(JwtEncoder jwtEncoder, @Value("${app.jwt.validity-hours}") long validityHours) {
        this.jwtEncoder = jwtEncoder;
        this.validity = Duration.ofHours(validityHours);
    }

    /** Admin: after a username + password login */
    public LoginResponse issue(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .toList();
        return encode(authentication.getName(), authentication.getName(), null, roles);
    }

    /** Customer: after Google sign-in. The token's subject is Google's permanent user id. */
    public LoginResponse issueCustomer(GoogleIdentity identity) {
        return encode(identity.subject(), identity.name(), identity.email(), List.of(ROLE_CUSTOMER));
    }

    private LoginResponse encode(String subject, String displayName, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(validity);
        // The claims are readable by anyone (base64), so never put secrets in them.
        // The signature only guarantees nobody changed them.
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("van-rental")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .claim("name", displayName);
        if (email != null) {
            claims.claim("email", email);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new LoginResponse(token, displayName, email, roles, expiresAt);
    }
}
