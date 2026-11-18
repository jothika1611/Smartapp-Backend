package com.smartpay.fraud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.fraud.repository.FraudAlertRepository;
import com.smartpay.fraud.repository.FraudAssessmentRepository;
import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.RoleName;
import com.smartpay.identity.entity.User;
import com.smartpay.identity.entity.UserStatus;
import com.smartpay.identity.repository.RoleRepository;
import com.smartpay.identity.repository.UserRepository;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.repository.TransactionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FraudIntegrationTest {

    private static final String ADMIN_EMAIL = "fraud-admin@smartpay.local";
    private static final String ADMIN_PASSWORD = "AdminOnly@123";
    private static final String PASSWORD = "DemoOnly@123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("smartpay_fraud_test")
            .withUsername("smartpay_test")
            .withPassword("test_only_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.secret", () -> "fraud-integration-secret-at-least-32-chars");
        registry.add("app.rate-limit.auth-per-minute", () -> "2000");
        registry.add("app.rate-limit.per-minute", () -> "2000");
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
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private FraudAssessmentRepository assessmentRepository;
    @Autowired
    private FraudAlertRepository alertRepository;

    @BeforeEach
    void ensureAdminExists() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setFullName("Simulated Fraud Admin");
        admin.setPhone("9000000098");
        admin.setStatus(UserStatus.ACTIVE);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }

    private String uniqueEmail() {
        return "fraud-" + UUID.randomUUID() + "@smartpay.local";
    }

    private String register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", "Fraud Demo",
                                "phone", "9000000020"))))
                .andExpect(status().isCreated());
        return login(email, PASSWORD).path("accessToken").asText();
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

    private String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD).path("accessToken").asText();
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private void fund(String token, Long userId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/admin/demo-funding")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "fund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "amount", amount,
                                "description", "Fraud test funding"))))
                .andExpect(status().isCreated());
    }

    private JsonNode send(String token, String receiverEmail, String amount, String device,
                          String location) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverEmail", receiverEmail);
        body.put("amount", amount);
        body.put("currency", "INR");
        body.put("description", "Simulated fraud-rule test");
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

    private JsonNode assessment(String token, String reference, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/transactions/" + reference + "/assessment")
                        .header("Authorization", "Bearer " + token))
                .andExpect(expectedStatus == 200 ? status().isOk() : status().isForbidden())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private BigDecimal balance(String email) {
        return walletRepository.findByUserId(userId(email)).orElseThrow().getAvailableBalance();
    }

    @Test
    void highAmountWithNewDeviceAndLocationProducesHoldAndAdminAlert() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String token = register(senderEmail);
        register(receiverEmail);
        fund(adminToken(), userId(senderEmail), "100000.00");

        JsonNode result = send(token, receiverEmail, "60000.00", "brand-new-device", "brand-new-place");

        assertThat(result.path("status").asText()).isEqualTo("HOLD");
        assertThat(result.path("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(result.path("riskScore").asInt()).isEqualTo(50);
        assertThat(balance(senderEmail)).isEqualByComparingTo("100000.00");
        assertThat(balance(receiverEmail)).isEqualByComparingTo("0.00");

        String reference = result.path("reference").asText();
        Transaction stored = transactionRepository.findByReference(reference).orElseThrow();
        assertThat(assessmentRepository.findByTransactionId(stored.getId())).isPresent();
        assertThat(alertRepository.findByTransactionId(stored.getId())).hasSize(1);
        assertThat(alertRepository.findByTransactionId(stored.getId()).get(0).getStatus())
                .isEqualTo("OPEN");

        JsonNode details = assessment(token, reference, 200);
        assertThat(details.path("score").asInt()).isEqualTo(50);
        assertThat(details.path("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(details.path("deterministicExplanation").asText())
                .contains("Risk 50/100 (HIGH)")
                .contains("no fraud determination made");
        assertThat(details.path("aiExplanation").asText()).contains("Simulated AI wording");
        assertThat(details.path("aiSource").asText()).isEqualTo("MOCK");
        assertThat(details.path("hits").size()).isEqualTo(3);

        mockMvc.perform(post("/api/v1/payments/" + reference + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void newDeviceAndLocationProduceMediumPendingWithoutMovingMoney() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String outsiderEmail = uniqueEmail();
        String token = register(senderEmail);
        register(receiverEmail);
        String outsiderToken = register(outsiderEmail);
        fund(adminToken(), userId(senderEmail), "1000.00");

        JsonNode low = send(token, receiverEmail, "25.00", null, null);
        assertThat(low.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(balance(senderEmail)).isEqualByComparingTo("975.00");

        JsonNode medium = send(token, receiverEmail, "25.00", "device-two", "place-two");
        assertThat(medium.path("status").asText()).isEqualTo("PENDING");
        assertThat(medium.path("riskLevel").asText()).isEqualTo("MEDIUM");
        assertThat(medium.path("riskScore").asInt()).isEqualTo(25);
        assertThat(balance(senderEmail)).isEqualByComparingTo("975.00");

        String reference = medium.path("reference").asText();
        JsonNode details = assessment(token, reference, 200);
        assertThat(details.path("hits").size()).isEqualTo(2);
        assertThat(details.path("deterministicExplanation").asText()).contains("Risk 25/100 (MEDIUM)");

        assessment(outsiderToken, reference, 403);
        JsonNode adminView = assessment(adminToken(), reference, 200);
        assertThat(adminView.path("reference").asText()).isEqualTo(reference);

        mockMvc.perform(post("/api/v1/payments/" + reference + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void ordinaryTransferHasLowAssessmentWithNoHits() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String token = register(senderEmail);
        register(receiverEmail);
        fund(adminToken(), userId(senderEmail), "200.00");

        JsonNode result = send(token, receiverEmail, "10.00", null, null);

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("riskLevel").asText()).isEqualTo("LOW");
        assertThat(result.path("riskScore").asInt()).isZero();

        JsonNode details = assessment(token, result.path("reference").asText(), 200);
        assertThat(details.path("hits").size()).isZero();
        assertThat(details.path("deterministicExplanation").asText())
                .contains("No configured risk rule was triggered");
    }
}
