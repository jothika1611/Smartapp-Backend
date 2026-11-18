package com.smartpay.identity.repository;

import com.smartpay.identity.entity.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {

    void deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
