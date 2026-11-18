package com.smartpay.identity.dto;

/** Tokens plus a safe user view. The refresh token is shown only at login/refresh time. */
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresInSeconds;
    private final UserResponse user;

    public AuthResponse(String accessToken, String refreshToken, String tokenType,
                        long expiresInSeconds, UserResponse user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresInSeconds() { return expiresInSeconds; }
    public UserResponse getUser() { return user; }
}
