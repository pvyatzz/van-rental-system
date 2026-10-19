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

/** Creates the signed token (JWT) the frontend sends with every admin request. */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration validity;

    public TokenService(JwtEncoder jwtEncoder, @Value("${app.jwt.validity-hours}") long validityHours) {
        this.jwtEncoder = jwtEncoder;
        this.validity = Duration.ofHours(validityHours);
    }

    public LoginResponse issue(Authentication authentication) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(validity);
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .toList();

        // The claims are readable by anyone (base64), so never put secrets in them.
        // The signature only guarantees nobody changed them.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("van-rental")
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, authentication.getName(), expiresAt);
    }
}
