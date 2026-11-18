package com.smartpay.identity.service;

import com.smartpay.identity.repository.RefreshTokenRepository;
import com.smartpay.identity.repository.RevokedAccessTokenRepository;
import com.smartpay.identity.entity.RevokedAccessToken;
import com.smartpay.shared.security.JwtTokenProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Records revoked access-token ids and refresh-token hashes until they expire. */
@Service
public class TokenRevocationService {

    public static final String JTI_ATTRIBUTE = "smartpay.accessTokenJti";
    public static final String ACCESS_TOKEN_ATTRIBUTE = "smartpay.accessToken";

    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public TokenRevocationService(RevokedAccessTokenRepository revokedAccessTokenRepository,
                                  RefreshTokenRepository refreshTokenRepository,
                                  JwtTokenProvider jwtTokenProvider) {
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public boolean isRevoked(String jti) {
        return jti != null && revokedAccessTokenRepository.existsById(jti);
    }

    @Transactional
    public void revokeAccessToken(String jti, Long userId, Instant expiresAt) {
        if (jti == null || revokedAccessTokenRepository.existsById(jti)) {
            return;
        }
        revokedAccessTokenRepository.save(new RevokedAccessToken(
                jti,
                userId,
                LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
                LocalDateTime.now(ZoneOffset.UTC)
        ));
    }

    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtTokenProvider.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
                    refreshTokenRepository.save(token);
                });
    }

    @Scheduled(fixedDelayString = "${app.security.revocation-cleanup-ms:3600000}",
            initialDelayString = "${app.security.revocation-cleanup-initial-delay-ms:60000}")
    @Transactional
    public void deleteExpiredRecords() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        revokedAccessTokenRepository.deleteAllByExpiresAtBefore(now);
        refreshTokenRepository.deleteAllByExpiresAtBefore(now);
    }
}
