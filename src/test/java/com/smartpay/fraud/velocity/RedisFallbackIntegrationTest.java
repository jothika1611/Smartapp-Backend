package com.smartpay.fraud.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.RoleName;
import com.smartpay.identity.entity.User;
import com.smartpay.identity.entity.UserStatus;
import com.smartpay.identity.repository.RoleRepository;
import com.smartpay.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With Redis enabled but unreachable, MySQL still serves velocity counts and payments succeed. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RedisFallbackIntegrationTest {

    private static final String ADMIN_EMAIL = "redis-admin@smartpay.local";
    private static final String PASSWORD = "DemoOnly@123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("smartpay_redis_fallback_test")
            .withUsername("smartpay_test")
            .withPassword("test_only_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.redis.enabled", () -> "true");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "6399");
        registry.add("spring.data.redis.timeout", () -> "300ms");
        registry.add("app.jwt.secret", () -> "redis-fallback-secret-at-least-32-characters");
        registry.add("app.rate-limit.auth-per-minute", () -> "5000");
        registry.add("app.rate-limit.per-minute", () -> "5000");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureAdminExists() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode("AdminOnly@123"));
        admin.setFullName("Simulated Redis Admin");
        admin.setPhone("9000000091");
        admin.setStatus(UserStatus.ACTIVE);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", PASSWORD,
                                "fullName", "Redis Demo", "phone", "9000000092"))))
                .andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    @Test
    void paymentSucceedsWhenRedisIsUnavailable() throws Exception {
        String adminToken = login(ADMIN_EMAIL, "AdminOnly@123");
        String senderEmail = "redis-sender-" + UUID.randomUUID() + "@smartpay.local";
        String receiverEmail = "redis-receiver-" + UUID.randomUUID() + "@smartpay.local";
        String senderToken = register(senderEmail);
        register(receiverEmail);

        mockMvc.perform(post("/api/v1/admin/demo-funding")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "fund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userRepository.findByEmail(senderEmail).orElseThrow().getId(),
                                "amount", "200.00",
                                "description", "Redis fallback funding"))))
                .andExpect(status().isCreated());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverEmail", receiverEmail);
        body.put("amount", "10.00");
        body.put("currency", "INR");
        body.put("description", "Simulated Redis fallback transfer");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + senderToken)
                        .header("Idempotency-Key", "transfer-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }
}
