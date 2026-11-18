package com.smartpay.identity.service;

import com.smartpay.identity.dto.AuthResponse;
import com.smartpay.identity.dto.LoginRequest;
import com.smartpay.identity.dto.LogoutRequest;
import com.smartpay.identity.dto.RefreshRequest;
import com.smartpay.identity.config.DemoAdminInitializer;
import com.smartpay.identity.dto.RegisterRequest;
import com.smartpay.identity.dto.UserResponse;
import com.smartpay.identity.entity.RefreshToken;
import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.RoleName;
import com.smartpay.identity.entity.User;
import com.smartpay.identity.entity.UserStatus;
import com.smartpay.identity.mapper.UserMapper;
import com.smartpay.identity.repository.RefreshTokenRepository;
import com.smartpay.identity.repository.RoleRepository;
import com.smartpay.identity.repository.UserRepository;
import com.smartpay.identity.security.CustomUserPrincipal;
import com.smartpay.shared.config.JwtProperties;
import com.smartpay.shared.event.UserRegisteredEvent;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.shared.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final TokenRevocationService tokenRevocationService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       JwtProperties jwtProperties,
                       TokenRevocationService tokenRevocationService,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.tokenRevocationService = tokenRevocationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Registers a customer. The role is hard-coded to CUSTOMER here: no client input can change it.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new ApiException(ErrorCode.ROLE_NOT_FOUND));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setPhone(request.getPhone());
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(customerRole);

        User saved = userRepository.save(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), saved.getEmail()));
        return UserMapper.toResponse(saved);
    }

    /** Verifies credentials via Spring Security; never trusts a client-supplied user id or role. */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeLogin(request.getEmail());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
            CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
            return issueTokens(principal);
        } catch (AuthenticationException ex) {
            // Deliberately identical for unknown email, wrong password and suspended account.
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
    }

    /** Rotates a refresh token: the old one is revoked and a new pair is issued. */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = jwtTokenProvider.hashRefreshToken(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));

        if (!stored.isActive()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Refresh token has expired or been revoked");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        stored.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
        refreshTokenRepository.save(stored);

        return issueTokens(toPrincipal(user));
    }

    /** Revokes the current access token id and, when supplied, the matching refresh token. */
    @Transactional
    public void logout(Long userId, String jti, String rawAccessToken, LogoutRequest request) {
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getAccessTokenMinutes() * 60);
        if (rawAccessToken != null && !rawAccessToken.isBlank()) {
            try {
                Claims claims = jwtTokenProvider.parseAccessToken(rawAccessToken);
                if (claims.getExpiration() != null) {
                    expiresAt = claims.getExpiration().toInstant();
                }
            } catch (RuntimeException ignored) {
                // Keep the conservative fallback expiry; logout must still revoke what we know.
            }
        }

        tokenRevocationService.revokeAccessToken(jti, userId, expiresAt);
        if (request != null) {
            tokenRevocationService.revokeRefreshToken(request.getRefreshToken());
        }
    }

    /** Allows the short demo login "admin" while keeping email login for everyone else. */
    private String normalizeLogin(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return DemoAdminInitializer.LOGIN.equals(value) ? DemoAdminInitializer.EMAIL : value;
    }

    private AuthResponse issueTokens(CustomUserPrincipal principal) {
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .toList();

        String accessToken = jwtTokenProvider.generateAccessToken(
                principal.getId(), principal.getEmail(), roles);
        String rawRefreshToken = jwtTokenProvider.generateRawRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(principal.getId());
        refreshToken.setTokenHash(jwtTokenProvider.hashRefreshToken(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(jwtProperties.getRefreshTokenDays()));
        refreshTokenRepository.save(refreshToken);

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        UserResponse userResponse = UserMapper.toResponse(user);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer",
                jwtProperties.getAccessTokenMinutes() * 60, userResponse);
    }

    private CustomUserPrincipal toPrincipal(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Account is suspended");
        }
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .toList();
        return new CustomUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), true, authorities);
    }
}
