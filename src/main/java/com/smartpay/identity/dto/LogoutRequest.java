package com.smartpay.identity.dto;

/** Optional body: the client may send its refresh token so it can be revoked on logout. */
public class LogoutRequest {

    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
