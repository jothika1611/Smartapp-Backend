package com.smartpay.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.RoleName;
import com.smartpay.identity.entity.User;
import com.smartpay.identity.entity.UserStatus;
import com.smartpay.identity.repository.RoleRepository;
import com.smartpay.identity.repository.UserRepository;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.RiskLevel;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.entity.enums.TransactionType;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.wallet.repository.WalletLedgerEntryRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {

    private static final String ADMIN_EMAIL = "payment-admin@smartpay.local";
    private static final String ADMIN_PASSWORD = "AdminOnly@123";
    private static final String PASSWORD = "DemoOnly@123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("smartpay_payment_test")
            .withUsername("smartpay_test")
            .withPassword("test_only_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.secret", () -> "payment-integration-secret-at-least-32-chars");
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
    private WalletLedgerEntryRepository ledgerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void ensureAdminExists() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setFullName("Simulated Payment Admin");
        admin.setPhone("9000000099");
        admin.setStatus(UserStatus.ACTIVE);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }

    private String uniqueEmail() {
        return "payment-" + UUID.randomUUID() + "@smartpay.local";
    }

    private String register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Payment Demo","phone":"9000000010"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return login(email, PASSWORD).path("accessToken").asText();
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
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

    private String fund(String token, Long userId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/demo-funding")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "fund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"amount":%s,"description":"Payment test funding"}
                                """.formatted(userId, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("reference").asText();
    }

    private JsonNode send(String token, String path, String receiverEmail, String amount,
                          String key) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverEmail":"%s","amount":%s,"currency":"INR","description":"Simulated lunch split"}
                                """.formatted(receiverEmail, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private BigDecimal balance(Long userId) {
        return walletRepository.findByUserId(userId).orElseThrow().getAvailableBalance();
    }

    private BigDecimal money(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText());
    }

    @Test
    void transferSettlesAndWritesLedgerOnBothWallets() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String senderToken = register(senderEmail);
        String receiverToken = register(receiverEmail);
        fund(adminToken(), userId(senderEmail), "100.00");

        JsonNode result = send(senderToken, "/api/v1/transfers", receiverEmail, "30.00",
                "transfer-" + UUID.randomUUID());

        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("type").asText()).isEqualTo("TRANSFER");
        assertThat(money(result, "amount")).isEqualByComparingTo("30.00");
        String reference = result.path("reference").asText();
        assertThat(reference).startsWith("TXN-");
        assertThat(balance(userId(senderEmail))).isEqualByComparingTo("70.00");
        assertThat(balance(userId(receiverEmail))).isEqualByComparingTo("30.00");
        assertThat(ledgerRepository.countByWalletId(
                walletRepository.findByUserId(userId(senderEmail)).orElseThrow().getId())).isEqualTo(2);
        assertThat(ledgerRepository.countByWalletId(
                walletRepository.findByUserId(userId(receiverEmail)).orElseThrow().getId())).isEqualTo(1);

        Transaction stored = transactionRepository.findByReference(reference).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(stored.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(stored.getProcessedAt()).isNotNull();
    }

    @Test
    void paymentEndpointCreatesPaymentType() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String senderToken = register(senderEmail);
        register(receiverEmail);
        fund(adminToken(), userId(senderEmail), "50.00");

        JsonNode result = send(senderToken, "/api/v1/payments", receiverEmail, "12.50",
                "payment-" + UUID.randomUUID());

        assertThat(result.path("type").asText()).isEqualTo("PAYMENT");
        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(balance(userId(senderEmail))).isEqualByComparingTo("37.50");
    }

    @Test
    void sameIdempotencyKeyDoesNotMoveMoneyTwice() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String senderToken = register(senderEmail);
        register(receiverEmail);
        fund(adminToken(), userId(senderEmail), "100.00");
        String key = "idem-" + UUID.randomUUID();

        JsonNode first = send(senderToken, "/api/v1/transfers", receiverEmail, "25.00", key);
        JsonNode second = send(senderToken, "/api/v1/transfers", receiverEmail, "25.00", key);

        assertThat(second.path("reference").asText()).isEqualTo(first.path("reference").asText());
        assertThat(balance(userId(senderEmail))).isEqualByComparingTo("75.00");
        assertThat(ledgerRepository.countByWalletId(
                walletRepository.findByUserId(userId(receiverEmail)).orElseThrow().getId())).isEqualTo(1);
    }

    @Test
    void insufficientFundsIsRejectedWithoutMovingMoneyOrSavingTransaction() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String senderToken = register(senderEmail);
        register(receiverEmail);
        String key = "poor-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + senderToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverEmail":"%s","amount":"10.00"}
                                """.formatted(receiverEmail)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(balance(userId(senderEmail))).isEqualByComparingTo("0.00");
        assertThat(transactionRepository.findBySenderUserIdAndIdempotencyKey(
                userId(senderEmail), key)).isEmpty();
    }

    @Test
    void pendingTransactionCanBeCancelledOnlyOnce() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String senderToken = register(senderEmail);
        register(receiverEmail);

        Transaction pending = new Transaction();
        pending.setReference("TXN-PENDING-" + UUID.randomUUID().toString().substring(0, 8));
        pending.setSenderUserId(userId(senderEmail));
        pending.setReceiverUserId(userId(receiverEmail));
        pending.setType(TransactionType.PAYMENT);
        pending.setStatus(TransactionStatus.PENDING);
        pending.setAmount(new BigDecimal("10.00"));
        pending.setCurrency("INR");
        pending.setIdempotencyKey("pending-" + UUID.randomUUID());
        pending.setRiskScore(0);
        pending.setRiskLevel(RiskLevel.LOW);
        transactionRepository.save(pending);

        MvcResult cancelled = mockMvc.perform(post("/api/v1/payments/" + pending.getReference() + "/cancel")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(cancelled.getResponse().getContentAsString()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("FAILED");

        mockMvc.perform(post("/api/v1/payments/" + pending.getReference() + "/cancel")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void historyFiltersAndAccessRulesWork() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String outsiderEmail = uniqueEmail();
        String senderToken = register(senderEmail);
        String receiverToken = register(receiverEmail);
        String outsiderToken = register(outsiderEmail);
        fund(adminToken(), userId(senderEmail), "100.00");

        JsonNode transfer = send(senderToken, "/api/v1/transfers", receiverEmail, "15.00",
                "history-" + UUID.randomUUID());
        String reference = transfer.path("reference").asText();

        MvcResult senderHistory = mockMvc.perform(get("/api/v1/transactions")
                        .param("status", "SUCCESS").param("type", "TRANSFER")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode senderData = objectMapper.readTree(senderHistory.getResponse().getContentAsString()).path("data");
        assertThat(senderData.path("content").size()).isEqualTo(1);
        assertThat(senderData.path("content").get(0).path("reference").asText()).isEqualTo(reference);

        MvcResult receiverDetail = mockMvc.perform(get("/api/v1/transactions/" + reference)
                        .header("Authorization", "Bearer " + receiverToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(receiverDetail.getResponse().getContentAsString())
                .path("data").path("reference").asText()).isEqualTo(reference);

        mockMvc.perform(get("/api/v1/transactions/" + reference)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        MvcResult adminHistory = mockMvc.perform(get("/api/v1/transactions")
                        .param("type", "TRANSFER")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(adminHistory.getResponse().getContentAsString())
                .path("data").path("content").size()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/v1/transactions").param("status", "NOT_A_STATUS")
                        .header("Authorization", "Bearer " + senderToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fiveConcurrentTransfersDoNotLoseUpdates() throws Exception {
        String senderEmail = uniqueEmail();
        String receiverEmail = uniqueEmail();
        String senderToken = register(senderEmail);
        String receiverEmailForTransfer = receiverEmail;
        register(receiverEmail);
        fund(adminToken(), userId(senderEmail), "100.00");

        int count = 5;
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                String key = "concurrent-" + UUID.randomUUID();
                futures.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/v1/transfers")
                                    .header("Authorization", "Bearer " + senderToken)
                                    .header("Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"receiverEmail":"%s","amount":"10.00"}
                                            """.formatted(receiverEmailForTransfer)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(balance(userId(senderEmail))).isEqualByComparingTo("50.00");
        assertThat(balance(userId(receiverEmail))).isEqualByComparingTo("50.00");
    }

    @Test
    void oppositeDirectionConcurrentTransfersDoNotDeadlock() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        String tokenA = register(emailA);
        String tokenB = register(emailB);
        String admin = adminToken();
        fund(admin, userId(emailA), "50.00");
        fund(admin, userId(emailB), "50.00");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> aToB = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + tokenA)
                                .header("Idempotency-Key", "ab-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"receiverEmail":"%s","amount":"10.00"}
                                        """.formatted(emailB)))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> bToA = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + tokenB)
                                .header("Idempotency-Key", "ba-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"receiverEmail":"%s","amount":"10.00"}
                                        """.formatted(emailA)))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(aToB.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(bToA.get(30, TimeUnit.SECONDS)).isEqualTo(201);
        } finally {
            executor.shutdownNow();
        }

        assertThat(balance(userId(emailA))).isEqualByComparingTo("50.00");
        assertThat(balance(userId(emailB))).isEqualByComparingTo("50.00");
    }
}
