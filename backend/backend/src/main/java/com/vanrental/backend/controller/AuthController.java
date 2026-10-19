package com.vanrental.backend.controller;

import com.vanrental.backend.dto.GoogleLoginRequest;
import com.vanrental.backend.dto.LoginRequest;
import com.vanrental.backend.dto.LoginResponse;
import com.vanrental.backend.security.GoogleTokenVerifier;
import com.vanrental.backend.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService,
                          GoogleTokenVerifier googleTokenVerifier) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    /** Admin login. Wrong username/password throws BadCredentialsException → 401 (GlobalExceptionHandler) */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return tokenService.issue(authentication);
    }

    /** Customer sign-in: exchanges a verified Google ID token for our own CUSTOMER token */
    @PostMapping("/google")
    public LoginResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return tokenService.issueCustomer(googleTokenVerifier.verify(request.credential()));
    }
}
