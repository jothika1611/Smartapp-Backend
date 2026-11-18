package com.smartpay.identity.config;

import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.RoleName;
import com.smartpay.identity.entity.User;
import com.smartpay.identity.entity.UserStatus;
import com.smartpay.identity.repository.RoleRepository;
import com.smartpay.identity.repository.UserRepository;
import com.smartpay.shared.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one obvious demo administrator when DEMO_ADMIN_ENABLED=true.
 * Disabled in tests, and never enabled against real customer data.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo-admin", name = "enabled", havingValue = "true")
public class DemoAdminInitializer implements ApplicationRunner {

    public static final String LOGIN = "admin";
    public static final String EMAIL = "admin@smartpay.local";
    public static final String PASSWORD = "admin123";

    private static final Logger log = LoggerFactory.getLogger(DemoAdminInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public DemoAdminInitializer(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder,
                                ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing; Flyway did not run"));

        User admin = userRepository.findByEmail(EMAIL).orElseGet(() -> createAdmin(adminRole));
        boolean changed = false;
        if (!admin.getRoles().contains(adminRole)) {
            admin.getRoles().add(adminRole);
            changed = true;
        }
        if (!passwordEncoder.matches(PASSWORD, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
            changed = true;
        }
        if (changed) {
            userRepository.save(admin);
        }

        log.warn("DEMO ADMIN READY: login '{}' (or '{}') with password '{}'. "
                + "This is a simulation account; disable DEMO_ADMIN_ENABLED outside demos.",
                LOGIN, EMAIL, PASSWORD);
    }

    private User createAdmin(Role adminRole) {
        User admin = new User();
        admin.setEmail(EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFullName("Demo Administrator");
        admin.setPhone(null);
        admin.setStatus(UserStatus.ACTIVE);
        admin.getRoles().add(adminRole);
        User saved = userRepository.save(admin);
        // Gives the demo admin a wallet too, through the same registration seam.
        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), saved.getEmail()));
        return saved;
    }
}
