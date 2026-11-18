package com.smartpay.identity.controller;

import com.smartpay.identity.dto.AuthResponse;
import com.smartpay.identity.dto.LoginRequest;
import com.smartpay.identity.dto.LogoutRequest;
import com.smartpay.identity.dto.RefreshRequest;
import com.smartpay.identity.dto.RegisterRequest;
import com.smartpay.identity.dto.UserResponse;
import com.smartpay.identity.security.CustomUserPrincipal;
import com.smartpay.identity.service.AuthService;
import com.smartpay.identity.service.TokenRevocationService;
import com.smartpay.identity.service.UserService;
import com.smartpay.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Simulated authentication endpoints")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @Operation(summary = "Register a CUSTOMER account with an automatically simulated profile")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("Simulated account registered. No real money or credentials involved.",
                authService.register(request));
    }

    @Operation(summary = "Log in and receive a JWT access token plus a rotating refresh token")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Simulated login successful.", authService.login(request));
    }

    @Operation(summary = "Exchange a refresh token for a new access/refresh token pair")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok("Tokens rotated.", authService.refresh(request));
    }

    @Operation(summary = "Revoke the current access token and optional refresh token")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestAttribute(name = TokenRevocationService.JTI_ATTRIBUTE, required = false) String jti,
            @RequestAttribute(name = TokenRevocationService.ACCESS_TOKEN_ATTRIBUTE, required = false) String accessToken,
            @RequestBody(required = false) LogoutRequest request) {
        authService.logout(principal.getId(), jti, accessToken, request);
        return ApiResponse.ok("Simulated session revoked.", null);
    }

    @Operation(summary = "Return the authenticated user's own profile")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(userService.getCurrentUser(principal.getId()));
    }
}
