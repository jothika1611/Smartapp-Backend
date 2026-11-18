package com.smartpay.shared.security;

import com.smartpay.shared.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setIssuer("smartpay-test");
        properties.setAccessTokenMinutes(15);
        properties.setRefreshTokenDays(7);
        return properties;
    }

    @Test
    void accessTokenCarriesUserIdEmailRolesAndJti() {
        JwtTokenProvider provider = new JwtTokenProvider(
                properties("test-only-secret-that-is-at-least-32-chars"));

        String token = provider.generateAccessToken(42L, "demo@smartpay.local", List.of("CUSTOMER", "ADMIN"));
        Claims claims = provider.parseAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("demo@smartpay.local");
        assertThat(claims.get("roles", List.class)).containsExactly("CUSTOMER", "ADMIN");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuer()).isEqualTo("smartpay-test");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void refreshTokensAreOpaqueAndStoredOnlyAsDeterministicHashes() {
        JwtTokenProvider provider = new JwtTokenProvider(
                properties("test-only-secret-that-is-at-least-32-chars"));

        String first = provider.generateRawRefreshToken();
        String second = provider.generateRawRefreshToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(provider.hashRefreshToken(first)).hasSize(64).isEqualTo(provider.hashRefreshToken(first));
        assertThat(provider.hashRefreshToken(first)).isNotEqualTo(first);
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        JwtTokenProvider issuer = new JwtTokenProvider(
                properties("first-test-only-secret-at-least-32-chars"));
        JwtTokenProvider verifier = new JwtTokenProvider(
                properties("second-test-only-secret-at-least-32-chars"));

        String token = issuer.generateAccessToken(1L, "demo@smartpay.local", List.of("CUSTOMER"));

        assertThatThrownBy(() -> verifier.parseAccessToken(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        JwtProperties properties = properties("test-only-secret-that-is-at-least-32-chars");
        properties.setAccessTokenMinutes(0);
        JwtTokenProvider provider = new JwtTokenProvider(properties);

        String token = provider.generateAccessToken(1L, "demo@smartpay.local", List.of("CUSTOMER"));
        Thread.sleep(1100);

        assertThatThrownBy(() -> provider.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shortSecretFailsFastAtStartup() {
        assertThatThrownBy(() -> new JwtTokenProvider(properties("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }
}
