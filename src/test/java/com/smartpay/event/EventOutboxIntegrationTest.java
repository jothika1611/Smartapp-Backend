package com.smartpay.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.smartpay.event.service.DeadLetterService;
import com.smartpay.event.service.EventDedupeService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves the outbox publishes, consumers dedupe, and poison events reach the DLQ. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EventOutboxIntegrationTest {

    private static final String ADMIN_EMAIL = "event-admin@smartpay.local";
    private static final String ADMIN_PASSWORD = "AdminOnly@123";
    private static final String PASSWORD = "DemoOnly@123";
    private static final List<String> TOPICS = List.of(
            "smartpay.tx.created", "smartpay.tx.status.changed", "smartpay.dlq");
    private static final AtomicBoolean TOPICS_READY = new AtomicBoolean(false);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("smartpay_event_test")
            .withUsername("smartpay_test")
            .withPassword("test_only_password");

    @Container
    static final GenericContainer<?> KAFKA = new GenericContainer<>("apache/kafka:3.9.0")
            .withExposedPorts(9092)
            .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1))
            .withEnv("KAFKA_NODE_ID", "1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://localhost:9093")
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:19092")
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT")
            .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(19092), ExposedPort.tcp(9092))));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
        registry.add("app.kafka.enabled", () -> "true");
        registry.add("app.outbox.poll-ms", () -> "200");
        registry.add("app.jwt.secret", () -> "event-outbox-secret-at-least-32-characters");
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
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EventDedupeService dedupeService;
    @Autowired
    private DeadLetterService deadLetterService;

    @BeforeEach
    void ensureAdminExists() throws Exception {
        ensureTopics();
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setFullName("Simulated Event Admin");
        admin.setPhone("9000000081");
        admin.setStatus(UserStatus.ACTIVE);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
    }

    private void ensureTopics() throws Exception {
        if (TOPICS_READY.get()) {
            return;
        }
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092",
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 20_000))) {
            Set<String> existing = admin.listTopics().names().get(30, TimeUnit.SECONDS);
            List<NewTopic> missing = TOPICS.stream()
                    .filter(topic -> !existing.contains(topic))
                    .map(topic -> new NewTopic(topic, 1, (short) 1))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            if (!existing.contains("__consumer_offsets")) {
                missing.add(new NewTopic("__consumer_offsets", 50, (short) 1));
            }
            if (!missing.isEmpty()) {
                try {
                    admin.createTopics(missing).all().get(30, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (!(exception.getCause() instanceof TopicExistsException)) {
                        throw exception;
                    }
                }
            }
            TOPICS_READY.set(true);
        }
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

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private void fund(String adminToken, String email) throws Exception {
        mockMvc.perform(post("/api/v1/admin/demo-funding")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "fund-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId(email),
                                "amount", "500.00",
                                "description", "Event test funding"))))
                .andExpect(status().isCreated());
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private void waitUntil(BooleanSupplierLike condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(200L);
        }
        throw new AssertionError("Timed out waiting for asynchronous event processing");
    }

    @FunctionalInterface
    interface BooleanSupplierLike {
        boolean get();
    }

    @Test
    void transactionEventsArePublishedAndDeduplicated() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String senderEmail = "event-sender-" + UUID.randomUUID() + "@smartpay.local";
        String receiverEmail = "event-receiver-" + UUID.randomUUID() + "@smartpay.local";
        String senderToken = registerNamedAndLogin(senderEmail);
        registerNamedAndLogin(receiverEmail);
        fund(adminToken, senderEmail);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverEmail", receiverEmail);
        body.put("amount", "25.00");
        body.put("currency", "INR");
        body.put("description", "Simulated outbox test");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + senderToken)
                        .header("Idempotency-Key", "transfer-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        waitUntil(() -> count("SELECT COUNT(*) FROM outbox_events WHERE status = 'PUBLISHED'") >= 1L);
        waitUntil(() -> count("SELECT COUNT(*) FROM notifications") >= 2L);

        String eventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM processed_events WHERE consumer_group = 'smartpay-notification' LIMIT 1",
                String.class);
        assertThat(eventId).isNotBlank();
        assertThat(dedupeService.markProcessed(eventId, "smartpay-notification")).isFalse();
        assertThat(dedupeService.markProcessed("manual-" + UUID.randomUUID(), "manual-test")).isTrue();
    }

    @Test
    void poisonEventReachesDeadLetterAuditTable() throws Exception {
        long before = count("SELECT COUNT(*) FROM audit_logs WHERE action = 'KAFKA_DLQ'");
        deadLetterService.publish("smartpay.tx.created", "not-json", "simulated poison event");

        waitUntil(() -> count("SELECT COUNT(*) FROM audit_logs WHERE action = 'KAFKA_DLQ'") > before);
    }

    private String registerNamedAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "fullName", "Named Event Demo",
                                "phone", "9000000083"))))
                .andExpect(status().isCreated());
        return login(email, PASSWORD);
    }
}
