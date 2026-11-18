package com.smartpay.wallet.controller;

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
import com.smartpay.shared.exception.ApiException;
import com.smartpay.wallet.entity.Wallet;
import com.smartpay.wallet.repository.WalletLedgerEntryRepository;
import com.smartpay.wallet.repository.WalletRepository;
import com.smartpay.wallet.service.WalletService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalletIntegrationTest {

    private static final String ADMIN_EMAIL = "wallet-admin@smartpay.local";
    private static final String ADMIN_PASSWORD = "AdminOnly@123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("smartpay_wallet_test")
            .withUsername("smartpay_test")
            .withPassword("test_only_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.jwt.secret", () -> "wallet-integration-secret-at-least-32-characters");
        registry.add("app.rate-limit.auth-per-minute", () -> "1000");
        registry.add("app.rate-limit.per-minute", () -> "1000");
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

    @Autowired
    private WalletService walletService;

    @BeforeEach
    void ensureAdminExists() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setFullName("Simulated Wallet Admin");
        admin.setPhone("9000000098");
        admin.setStatus(UserStatus.ACTIVE);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }

    private String uniqueEmail() {
        return "wallet-" + UUID.randomUUID() + "@smartpay.local";
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "DemoOnly@123",
                                  "fullName": "Wallet Demo",
                                  "phone": "9000000003"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String customerToken(String email) throws Exception {
        register(email);
        return login(email, "DemoOnly@123").path("accessToken").asText();
    }

    private String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD).path("accessToken").asText();
    }

    private JsonNode fund(String token, String idempotencyKey, Long userId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/demo-funding")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "amount": %s,
                                  "description": "Portfolio demo top-up"
                                }
                                """.formatted(userId, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private BigDecimal money(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText());
    }

    @Test
    void registrationCreatesWalletWithZeroBalance() throws Exception {
        String email = uniqueEmail();
        String token = customerToken(email);

        MvcResult result = mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andReturn();

        JsonNode wallet = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(wallet.path("availableBalance").isTextual()).isTrue();
        assertThat(money(wallet, "availableBalance")).isEqualByComparingTo("0.00");
        assertThat(money(wallet, "ledgerBalance")).isEqualByComparingTo("0.00");
        assertThat(wallet.path("userId").asLong()).isEqualTo(userId(email));
    }

    @Test
    void customerCannotCallAdminFunding() throws Exception {
        String token = customerToken(uniqueEmail());

        mockMvc.perform(post("/api/v1/admin/demo-funding")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 1, "amount": 100.00}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminFundingCreditsWalletAndWritesImmutableLedger() throws Exception {
        String email = uniqueEmail();
        String customerToken = customerToken(email);
        Long customerId = userId(email);
        String adminToken = adminToken();

        JsonNode funding = fund(adminToken, "funding-" + UUID.randomUUID(), customerId, "250.75");
        assertThat(funding.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(funding.path("reference").asText()).startsWith("FND-");
        assertThat(funding.path("availableBalance").isTextual()).isTrue();
        assertThat(money(funding, "availableBalance")).isEqualByComparingTo("250.75");

        MvcResult walletResult = mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode wallet = objectMapper.readTree(walletResult.getResponse().getContentAsString()).path("data");
        assertThat(money(wallet, "availableBalance")).isEqualByComparingTo("250.75");
        assertThat(money(wallet, "ledgerBalance")).isEqualByComparingTo("250.75");

        MvcResult ledgerResult = mockMvc.perform(get("/api/v1/wallets/me/ledger")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ledger = objectMapper.readTree(ledgerResult.getResponse().getContentAsString());
        assertThat(ledger.path("data").path("content").size()).isEqualTo(1);
        JsonNode entry = ledger.path("data").path("content").get(0);
        assertThat(entry.path("entryType").asText()).isEqualTo("CREDIT");
        assertThat(money(entry, "amount")).isEqualByComparingTo("250.75");
        assertThat(money(entry, "balanceAfter")).isEqualByComparingTo("250.75");

        String reference = funding.path("reference").asText();
        Transaction stored = transactionRepository.findByReference(reference).orElseThrow();
        assertThat(stored.getType()).isEqualTo(TransactionType.FUNDING);
        assertThat(stored.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(stored.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(stored.getAmount()).isEqualByComparingTo("250.75");
    }

    @Test
    void repeatedIdempotencyKeyDoesNotDoubleCredit() throws Exception {
        String email = uniqueEmail();
        String customerToken = customerToken(email);
        Long customerId = userId(email);
        String adminToken = adminToken();
        String key = "wallet-idem-" + UUID.randomUUID();

        JsonNode first = fund(adminToken, key, customerId, "100.00");
        JsonNode second = fund(adminToken, key, customerId, "100.00");

        assertThat(second.path("reference").asText()).isEqualTo(first.path("reference").asText());
        assertThat(money(second, "availableBalance")).isEqualByComparingTo("100.00");

        MvcResult ledgerResult = mockMvc.perform(get("/api/v1/wallets/me/ledger")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ledger = objectMapper.readTree(ledgerResult.getResponse().getContentAsString());
        assertThat(ledger.path("data").path("content").size()).isEqualTo(1);
    }

    @Test
    void fundingUnknownUserReturns404() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/admin/demo-funding")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 999999999, "amount": 10.00}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Receiver user not found"));
    }

    @Test
    void debitBeyondBalanceIsRejectedAndLeavesWalletUntouched() throws Exception {
        String email = uniqueEmail();
        customerToken(email);
        Long customerId = userId(email);

        assertThatThrownBy(() -> walletService.debit(customerId, new BigDecimal("1.00"), null, "test"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient");

        Wallet wallet = walletRepository.findByUserId(customerId).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(ledgerRepository.countByWalletId(wallet.getId())).isZero();
    }

    @Test
    void concurrentCreditsDoNotLoseUpdates() throws Exception {
        String email = uniqueEmail();
        customerToken(email);
        Long customerId = userId(email);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    walletService.credit(customerId, new BigDecimal("10.00"), null, "Concurrent test credit");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Wallet wallet = walletRepository.findByUserId(customerId).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("50.00");
        assertThat(wallet.getLedgerBalance()).isEqualByComparingTo("50.00");
        assertThat(ledgerRepository.countByWalletId(wallet.getId())).isEqualTo(threads);
    }
}
