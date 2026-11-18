package com.smartpay.identity.config;

import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.RoleName;
import com.smartpay.identity.entity.User;
import com.smartpay.identity.entity.UserStatus;
import com.smartpay.identity.repository.RoleRepository;
import com.smartpay.identity.repository.UserRepository;
import com.smartpay.shared.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DemoAdminInitializer initializer;

    @Test
    void createsAdminWithAdminRoleAndPublishesWalletEvent() {
        Role adminRole = new Role(RoleName.ADMIN);
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail(DemoAdminInitializer.EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(DemoAdminInitializer.PASSWORD)).thenReturn("$2a$10$adminhash");
        when(passwordEncoder.matches(DemoAdminInitializer.PASSWORD, "$2a$10$adminhash")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        initializer.run(null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User admin = userCaptor.getValue();
        assertThat(admin.getEmail()).isEqualTo(DemoAdminInitializer.EMAIL);
        assertThat(admin.getPasswordHash()).isEqualTo("$2a$10$adminhash");
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getRoles()).extracting(Role::getName).containsExactly(RoleName.ADMIN);

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(99L);
    }

    @Test
    void upgradesExistingUserWithAdminRoleAndResetsDemoPassword() {
        Role adminRole = new Role(RoleName.ADMIN);
        User existing = new User();
        existing.setId(5L);
        existing.setEmail(DemoAdminInitializer.EMAIL);
        existing.setPasswordHash("$2a$10$oldhash");
        existing.setFullName("Existing user");
        existing.setStatus(UserStatus.ACTIVE);

        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail(DemoAdminInitializer.EMAIL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(DemoAdminInitializer.PASSWORD, "$2a$10$oldhash")).thenReturn(false);
        when(passwordEncoder.encode(DemoAdminInitializer.PASSWORD)).thenReturn("$2a$10$newhash");
        when(userRepository.save(existing)).thenReturn(existing);

        initializer.run(null);

        assertThat(existing.getRoles()).extracting(Role::getName).containsExactly(RoleName.ADMIN);
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$newhash");
        verify(userRepository).save(existing);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
