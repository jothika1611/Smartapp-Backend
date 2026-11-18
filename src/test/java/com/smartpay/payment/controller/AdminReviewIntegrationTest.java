package com.smartpay.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.RoleName;
import com.smartpay.identity.entity.User;
import com.smartpay.identity.entity.UserStatus;
import com.smartpay.identity.repository.RoleRepository;
import com.smartpay.identity.repository.UserRepository;
import com.smartpay.wallet.repository.WalletRepository;
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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminReviewIntegrationTest {

    private static final String ADMIN_EMAIL = "review-admin@smartpay.local";
    private static final String ADMIN_PASSWORD = "AdminOnly@123";
    private static final String PASSWORD = "DemoOnly@123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("smartpay_admin_review_test")
            .withUsername("smartpay_test")
            .withPassword("test_only_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.secret", () -> "admin-review-secret-at-least-32-characters");
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
    @Autowired
    private WalletRepository walletRepository;

    @BeforeEach
    void ensureAdminExists() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setFullName("Simulated Review Admin");
        admin.setPhone("9000000071");
        admin.setStatus(UserStatus.ACTIVE);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }

    private String uniqueEmail() {
        return "review-" + UUID.randomUUID() + "@smartpay.local";
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", "Review Demo",
                                "phone", "9000000072"))))
                .andExpect(status().isCreated());
        return login(email, PASSWORD).path("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD).path("accessToken").asText();
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private void fund(String token, String userEmail, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/admin/demo-funding")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "fund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId(userEmail),
                                "amount", amount,
                                "description", "Admin review test funding"))))
                .andExpect(status().isCreated());
    }

    private JsonNode send(String token, String receiverEmail, String amount, String device,
                          String location) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverEmail", receiverEmail);
        body.put("amount", amount);
        body.put("currency", "INR");
        body.put("description", "Simulated admin review test");
        if (device != null) {
            body.put("deviceFingerprint", device);
        }
        if (location != null) {
            body.put("locationHint", location);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "transfer-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private BigDecimal balance(String email) {
        return walletRepository.findByUserId(userId(email)).orElseThrow().getAvailableBalance();
    }

    private JsonNode getData(String token, String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode postData(String token, String url) throws Exception {
        MvcResult result = mockMvc.perform(post(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    void adminCanInvestigateAndApprovePendingTransfer() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String token = register(senderEmail);
        register(receiverEmail);
        String admin = adminToken();
        fund(admin, senderEmail, "1000.00");

        JsonNode pending = send(token, receiverEmail, "25.00", "pending-device", "pending-place");
        String reference = pending.path("reference").asText();
        assertThat(pending.path("status").asText()).isEqualTo("PENDING");
        assertThat(balance(senderEmail)).isEqualByComparingTo("1000.00");

        JsonNode search = getData(admin, "/api/v1/admin/transactions?status=PENDING&size=50");
        assertThat(search.path("content").toString()).contains(reference);

        JsonNode investigation = getData(admin,
                "/api/v1/admin/transactions/" + reference + "/investigation");
        assertThat(investigation.path("transaction").path("reference").asText()).isEqualTo(reference);
        assertThat(investigation.path("assessment").path("score").asInt()).isEqualTo(25);
        assertThat(investigation.path("assessment").path("hits").size()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/admin/transactions/" + reference + "/investigation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        JsonNode approved = postData(admin,
                "/api/v1/admin/transactions/" + reference + "/approve");
        assertThat(approved.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(approved.path("processedAt").isNull()).isFalse();
        assertThat(balance(senderEmail)).isEqualByComparingTo("975.00");

        mockMvc.perform(post("/api/v1/admin/transactions/" + reference + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminCanAssignResolveAlertAndRejectHoldWithoutMovingMoney() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String token = register(senderEmail);
        register(receiverEmail);
        String admin = adminToken();
        fund(admin, senderEmail, "200000.00");

        JsonNode hold = send(token, receiverEmail, "60000.00", "hold-device", "hold-place");
        String reference = hold.path("reference").asText();
        assertThat(hold.path("status").asText()).isEqualTo("HOLD");
        assertThat(balance(senderEmail)).isEqualByComparingTo("200000.00");

        JsonNode alerts = getData(admin, "/api/v1/admin/fraud/alerts?status=OPEN&size=50");
        long alertId = 0L;
        for (JsonNode alert : alerts.path("content")) {
            if (reference.equals(alert.path("transactionReference").asText())) {
                alertId = alert.path("id").asLong();
                break;
            }
        }
        assertThat(alertId).isPositive();

        JsonNode detail = getData(admin, "/api/v1/admin/fraud/alerts/" + alertId);
        assertThat(detail.path("senderEmail").asText()).isEqualTo(senderEmail);
        assertThat(detail.path("receiverEmail").asText()).isEqualTo(receiverEmail);
        assertThat(detail.path("senderName").asText()).isNotBlank();
        assertThat(detail.path("receiverName").asText()).isNotBlank();
        assertThat(detail.path("assessment").path("score").asInt()).isEqualTo(50);
        assertThat(detail.path("assessment").path("hits").size()).isEqualTo(3);

        JsonNode assigned = postData(admin, "/api/v1/admin/fraud/alerts/" + alertId + "/assign");
        assertThat(assigned.path("status").asText()).isEqualTo("INVESTIGATING");

        MvcResult resolvedResult = mockMvc.perform(post(
                        "/api/v1/admin/fraud/alerts/" + alertId + "/resolve")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "resolutionNotes", "Reviewed simulated evidence"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resolved = objectMapper.readTree(resolvedResult.getResponse().getContentAsString())
                .path("data");
        assertThat(resolved.path("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.path("resolutionNotes").asText())
                .isEqualTo("Reviewed simulated evidence");

        JsonNode rejected = postData(admin,
                "/api/v1/admin/transactions/" + reference + "/reject");
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(balance(senderEmail)).isEqualByComparingTo("200000.00");

        mockMvc.perform(get("/api/v1/admin/fraud/rules").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentApproveMovesMoneyOnlyOnce() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String token = register(senderEmail);
        register(receiverEmail);
        String admin = adminToken();
        fund(admin, senderEmail, "200000.00");

        JsonNode hold = send(token, receiverEmail, "60000.00", "race-device", "race-place");
        String reference = hold.path("reference").asText();
        assertThat(hold.path("status").asText()).isEqualTo("HOLD");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> approveWhenReleased(admin, reference, start));
            Future<Integer> second = executor.submit(() -> approveWhenReleased(admin, reference, start));
            start.countDown();
            int[] codes = {first.get(), second.get()};
            assertThat(codes).containsExactlyInAnyOrder(200, 422);
        } finally {
            executor.shutdownNow();
        }

        assertThat(balance(senderEmail)).isEqualByComparingTo("140000.00");
    }

    private int approveWhenReleased(String token, String reference, CountDownLatch start) {
        try {
            start.await();
            return mockMvc.perform(post("/api/v1/admin/transactions/" + reference + "/approve")
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void adminCanCreateUpdateAndDisableRule() throws Exception {
        String admin = adminToken();
        JsonNode initial = getData(admin, "/api/v1/admin/fraud/rules?size=100");
        assertThat(initial.path("totalElements").asInt()).isGreaterThanOrEqualTo(6);

        String code = "ADMIN_TEST_" + UUID.randomUUID().toString().substring(0, 6);
        String body = objectMapper.writeValueAsString(Map.of(
                "code", code,
                "name", "Admin test rule",
                "description", "Created by integration test",
                "category", "TEST",
                "maxPoints", 5,
                "thresholdValue", new BigDecimal("2.00"),
                "timeWindowMinutes", 5,
                "enabled", true));
        MvcResult createdResult = mockMvc.perform(post("/api/v1/admin/fraud/rules")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString())
                .path("data");
        long ruleId = created.path("id").asLong();
        assertThat(created.path("code").asText()).isEqualToIgnoringCase(code);

        MvcResult updatedResult = mockMvc.perform(put("/api/v1/admin/fraud/rules/" + ruleId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Renamed by admin",
                                "maxPoints", 9,
                                "enabled", true))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode updated = objectMapper.readTree(updatedResult.getResponse().getContentAsString())
                .path("data");
        assertThat(updated.path("name").asText()).isEqualTo("Renamed by admin");
        assertThat(updated.path("maxPoints").asInt()).isEqualTo(9);
        assertThat(updated.path("thresholdValue").isNull()).isTrue();

        mockMvc.perform(delete("/api/v1/admin/fraud/rules/" + ruleId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        JsonNode after = getData(admin, "/api/v1/admin/fraud/rules?size=100");
        boolean stillEnabled = false;
        for (JsonNode rule : after.path("content")) {
            if (rule.path("id").asLong() == ruleId) {
                stillEnabled = rule.path("enabled").asBoolean();
            }
        }
        assertThat(stillEnabled).isFalse();

        mockMvc.perform(post("/api/v1/admin/fraud/rules")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }
}
