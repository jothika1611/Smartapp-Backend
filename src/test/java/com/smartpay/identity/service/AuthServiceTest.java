package com.smartpay.identity.service;

import com.smartpay.identity.config.DemoAdminInitializer;
import com.smartpay.identity.dto.AuthResponse;
import com.smartpay.identity.dto.LoginRequest;
import com.smartpay.identity.dto.LogoutRequest;
import com.smartpay.identity.dto.RefreshRequest;
import com.smartpay.identity.dto.RegisterRequest;
import com.smartpay.identity.dto.UserResponse;
import com.smartpay.identity.entity.RefreshToken;
import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.RoleName;
import com.smartpay.identity.entity.User;
import com.smartpay.identity.entity.UserStatus;
import com.smartpay.identity.repository.RefreshTokenRepository;
import com.smartpay.identity.repository.RoleRepository;
import com.smartpay.identity.repository.UserRepository;
import com.smartpay.identity.security.CustomUserPrincipal;
import com.smartpay.shared.config.JwtProperties;
import com.smartpay.shared.event.UserRegisteredEvent;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest request(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("DemoOnly@123");
        request.setFullName("Demo Customer");
        request.setPhone("9000000000");
        return request;
    }

    private LoginRequest loginRequest(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("DemoOnly@123");
        return request;
    }

    private User activeUser(String email) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$storedhash");
        user.setFullName("Demo Customer");
        user.setPhone("9000000000");
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(new Role(RoleName.CUSTOMER));
        return user;
    }

    private void stubTokenIssuing(User user) {
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq(user.getEmail()), eq(List.of("CUSTOMER"))))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRawRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtTokenProvider.hashRefreshToken("raw-refresh-token")).thenReturn("refresh-hash");
        when(jwtProperties.getAccessTokenMinutes()).thenReturn(15L);
        when(jwtProperties.getRefreshTokenDays()).thenReturn(7L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void register_hashesPasswordAndAssignsOnlyCustomerRole() {
        when(userRepository.existsByEmail("demo.person@smartpay.local")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER))
                .thenReturn(Optional.of(new Role(RoleName.CUSTOMER)));
        when(passwordEncoder.encode("DemoOnly@123")).thenReturn("$2a$10$hashedvalue");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        UserResponse response = authService.register(request("demo.person@smartpay.local"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User persisted = captor.getValue();

        assertThat(persisted.getPasswordHash()).isEqualTo("$2a$10$hashedvalue");
        assertThat(persisted.getRoles()).extracting(role -> role.getName())
                .containsExactly(RoleName.CUSTOMER);
        assertThat(response.getEmail()).isEqualTo("demo.person@smartpay.local");
        assertThat(response.getRoles()).containsExactly("CUSTOMER");
        assertThat(response.toString()).doesNotContain("hashedvalue");

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().email()).isEqualTo("demo.person@smartpay.local");
    }

    @Test
    void register_normalisesEmailToLowerCase() {
        when(userRepository.existsByEmail("mixed.case@smartpay.local")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER))
                .thenReturn(Optional.of(new Role(RoleName.CUSTOMER)));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request("Mixed.Case@SmartPay.Local"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("mixed.case@smartpay.local");
    }

    @Test
    void register_rejectsDuplicateEmailBeforeHashing() {
        when(userRepository.existsByEmail("duplicate@smartpay.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request("duplicate@smartpay.local")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already registered");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_failsClearlyWhenCustomerRoleIsMissing() {
        when(userRepository.existsByEmail("norole@smartpay.local")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request("norole@smartpay.local")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("role");
    }

    @Test
    void login_issuesAccessAndRefreshTokensForAuthenticatedPrincipal() {
        User user = activeUser("login@smartpay.local");
        CustomUserPrincipal principal = new CustomUserPrincipal(1L, user.getEmail(), user.getPasswordHash(), true,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        stubTokenIssuing(user);

        AuthResponse response = authService.login(loginRequest("Login@SmartPay.Local"));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(900L);
        assertThat(response.getUser().getRoles()).containsExactly("CUSTOMER");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_mapsAdminAliasToDemoAdminEmail() {
        User admin = activeUser(DemoAdminInitializer.EMAIL);
        admin.getRoles().clear();
        admin.getRoles().add(new Role(RoleName.ADMIN));
        CustomUserPrincipal principal = new CustomUserPrincipal(1L, admin.getEmail(), admin.getPasswordHash(), true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(authenticationManager.authenticate(any())).thenAnswer(invocation -> {
            Authentication auth = invocation.getArgument(0);
            assertThat(auth.getName()).isEqualTo(DemoAdminInitializer.EMAIL);
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        });
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq(DemoAdminInitializer.EMAIL), eq(List.of("ADMIN"))))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRawRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtTokenProvider.hashRefreshToken("raw-refresh-token")).thenReturn("refresh-hash");
        when(jwtProperties.getAccessTokenMinutes()).thenReturn(15L);
        when(jwtProperties.getRefreshTokenDays()).thenReturn(7L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        AuthResponse response = authService.login(loginRequest(" admin "));

        assertThat(response.getUser().getRoles()).containsExactly("ADMIN");
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void login_usesOneGenericMessageForBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest("wrong@smartpay.local")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid email or password");

        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void refresh_rotatesAndRevokesTheOldRefreshToken() {
        User user = activeUser("refresh@smartpay.local");
        RefreshToken stored = new RefreshToken();
        stored.setUserId(1L);
        stored.setTokenHash("refresh-hash");
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("raw-refresh-token");

        when(jwtTokenProvider.hashRefreshToken("raw-refresh-token")).thenReturn("refresh-hash");
        when(refreshTokenRepository.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));
        stubTokenIssuing(user);

        AuthResponse response = authService.refresh(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void refresh_rejectsExpiredOrRevokedToken() {
        RefreshToken stored = new RefreshToken();
        stored.setUserId(1L);
        stored.setTokenHash("refresh-hash");
        stored.setExpiresAt(LocalDateTime.now().minusDays(1));

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("raw-refresh-token");

        when(jwtTokenProvider.hashRefreshToken("raw-refresh-token")).thenReturn("refresh-hash");
        when(refreshTokenRepository.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired or been revoked");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void logout_revokesAccessTokenJtiAndRefreshToken() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("raw-refresh-token");

        authService.logout(1L, "jti-123", null, request);

        verify(tokenRevocationService).revokeAccessToken(eq("jti-123"), eq(1L), any());
        verify(tokenRevocationService).revokeRefreshToken("raw-refresh-token");
    }
}
