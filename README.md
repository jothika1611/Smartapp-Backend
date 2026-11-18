# SmartPay Backend

> Portfolio simulation only. This service does **not** connect to a bank, card network, payment
> gateway or real-money system. All balances and transactions are simulated. Never use it with real
> customer data.

The backend is a Spring Boot modular monolith that provides JWT identity, simulated wallets, an
immutable ledger, transfers/payments, deterministic fraud scoring, admin review, Kafka outbox events,
optional Redis velocity counters and AI wording. MySQL is the only source of financial truth.

For the full project picture, start with the [root README](../README.md).

---

## Quick facts

| Item | Value |
|---|---|
| Language / framework | Java 21, Spring Boot 3.5.16 |
| Build | Maven |
| Default port | `8081` |
| Base API path | `/api/v1` |
| Database | MySQL 8, Flyway migrations `V1`–`V8` |
| Money | `BigDecimal` in Java, `DECIMAL(19,2)` in MySQL, decimal strings in JSON |
| Roles | `CUSTOMER`, `ADMIN` — assigned by the server only |
| Kafka | Optional, notification-only, disabled by default |
| Redis | Optional performance aid, disabled by default |
| AI | Mock provider by default, wording only |
| Docs | Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs` |
| Health | `/actuator/health` |

---

## Source layout

```text
src/main/java/com/smartpay/
├── identity/       registration, login, JWT, users, roles
├── wallet/         wallet balances and immutable ledger
├── payment/        simulated transfers, payments, history, idempotency
├── fraud/          rules, scoring, alerts, admin investigation
├── admin/          admin review and rule management
├── event/          transactional outbox and Kafka publisher/consumer
├── notification/   notification records and async handling
├── ai/             AI wording boundary
├── analytics/      KPI queries
├── shared/         common config, errors, security and DTOs
└── SmartPayApplication.java

src/main/resources/
├── application.yml
└── db/migration/    V1–V8 Flyway SQL files
```

Each module keeps its controller, service, repository, entity and DTO code close together. The
modules run inside one application today; the architecture is documented so they can be split later
if needed.

---

## Prerequisites

- Java 21
- Maven 3.9+
- MySQL 8 (or Docker Desktop for a local container)
- Docker Desktop if you want to run the Testcontainers-backed test suite

Host builds use the D-drive Maven cache configured in `../.mvn/maven.config`.

---

## Environment variables

The most important variables are below. The root `.env.example` is the safe template.

| Variable | Default | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3308/smartpay?...` | Use port `3308` for the local demo container |
| `DB_USER` | `smartpay_app` | Use `root` for the local host-run example below |
| `DB_PASSWORD` | `CHANGE_ME_DB_PASSWORD` | Required |
| `JWT_SECRET` | placeholder | Must be at least 32 characters |
| `SERVER_PORT` | `8081` | Backend port |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3001,http://localhost:5173` | Frontend origins |
| `DEMO_ADMIN_ENABLED` | `false` | Set `true` only for local demos |
| `KAFKA_ENABLED` | `false` | Notification-only outbox publisher |
| `REDIS_ENABLED` | `false` | Velocity counters fall back to SQL |
| `AI_PROVIDER` | `mock` | Use `openai` only with a real key |
| `FRAUD_LOW_MAX` | `24` | Score at or below this is LOW |
| `FRAUD_MEDIUM_MAX` | `49` | Score above LOW but below HIGH is MEDIUM |
| `FRAUD_HIGH_MIN` | `50` | Score at or above this is HIGH |

Do not commit real secrets. The application refuses to start with the placeholder JWT secret.

---

## Run locally

### 1. Start MySQL

From the repository root:

```powershell
docker run -d --name smartpay-mysql `
  -e MYSQL_ROOT_PASSWORD=demo_root_password `
  -e MYSQL_DATABASE=smartpay `
  -e MYSQL_USER=smartpay_app `
  -e MYSQL_PASSWORD=demo_app_password `
  -p 3308:3306 mysql:8.0
```

If the container already exists:

```powershell
docker start smartpay-mysql
```

### 2. Set environment variables

In `smartpay-backend`:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3308/smartpay?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:DB_USER = 'root'
$env:DB_PASSWORD = 'demo_root_password'
$env:JWT_SECRET = 'local-only-secret-at-least-32-characters'
$env:KAFKA_ENABLED = 'false'
$env:REDIS_ENABLED = 'false'
$env:AI_PROVIDER = 'mock'
$env:DEMO_ADMIN_ENABLED = 'true'
```

Use `root` here because the `smartpay_app` grant is intended for the Compose network.

### 3. Start the backend

```powershell
mvn spring-boot:run
```

Or build and run the jar:

```powershell
mvn clean package
java -jar target\smartpay-backend-0.12.0-PHASE12.jar
```

Open:

```text
Swagger UI: http://localhost:8081/swagger-ui.html
Health:     http://localhost:8081/actuator/health
OpenAPI:    http://localhost:8081/v3/api-docs
```

### Full stack instead

From the repository root, the recommended path is:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
```

See the [root README](../README.md#quick-start--full-stack-recommended) for the Compose details.

---

## Demo accounts

Registration is public for `CUSTOMER` only. There is no public ADMIN creation endpoint.

When `DEMO_ADMIN_ENABLED=true`, startup seeds:

| Login value | Password | Stored email | Role |
|---|---|---|---|
| `admin` or `admin@smartpay.local` | `admin123` | `admin@smartpay.local` | `ADMIN` |

Keep `DEMO_ADMIN_ENABLED=false` outside local demos.

---

## Implemented API groups

All paths below are under `/api/v1`.

| Group | Example endpoints | Purpose |
|---|---|---|
| Identity | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me` | Register, login, rotate and revoke tokens |
| Profile | `GET /users/me`, `PUT /users/me`, `POST /users/me/change-password` | Customer/admin self-service |
| Wallet | `GET /wallets/me`, `GET /wallets/me/ledger`, `POST /wallets/me/demo-topup` | Simulated balances and ledger |
| Payments | `POST /payments`, `POST /transfers`, `GET /payments/{reference}/status`, `POST /payments/{reference}/cancel` | Simulated money movement |
| History | `GET /transactions`, `GET /transactions/{reference}`, `GET /transactions/{reference}/assessment` | Filtered history and fraud explanation |
| Admin review | `GET /admin/transactions`, `GET /admin/transactions/{ref}/investigation`, `POST /admin/transactions/{ref}/approve`, `POST /admin/transactions/{ref}/reject` | Release or reject held money |
| Fraud alerts | `GET /admin/fraud/alerts`, `GET /admin/fraud/alerts/{id}`, assign and resolve endpoints | Alert queue with sender/receiver display fields |
| Fraud rules | `GET|POST /admin/fraud/rules`, `PUT|DELETE /admin/fraud/rules/{id}` | View and manage rules; delete means soft disable |
| Health/docs | `/actuator/health`, `/swagger-ui.html`, `/v3/api-docs` | Operations and API docs |

Money-changing POSTs require an `Idempotency-Key` header. See
[`docs/API_LIST.md`](../docs/API_LIST.md) for exact request and response shapes and for endpoints
that are planned but not implemented.

---

## Important rules

- Every money response is labelled `"simulated": true`.
- Amounts are decimal strings such as `"1500.00"`; never use floating point for money.
- No client-supplied role or user ID is trusted for authorization.
- `LOW` settles, `MEDIUM` becomes `PENDING`, `HIGH` becomes `HOLD` and raises an admin alert.
- Only an ADMIN can release held money.
- The deterministic rule engine decides the score and level first. AI only rewrites wording.
- Kafka events are asynchronous notifications. No consumer changes money.
- Redis is optional and only speeds up velocity counting. MySQL remains authoritative.

---

## Tests

```powershell
mvn test
```

The suite covers identity, wallet, ledger, idempotency, concurrency, fraud scoring, alerts, admin
review, outbox, Kafka/Redis boundaries and AI wording boundaries. Some tests use Testcontainers,
so Docker must be running. The full Docker-backed run passed 110 tests in this workspace.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| App refuses to start because of JWT secret | Set `JWT_SECRET` to a random string of at least 32 characters |
| `Access denied for user 'smartpay_app'` | Use `DB_USER=root` with `DB_PASSWORD=demo_root_password` for the local host-run MySQL container |
| Flyway validation error | Do not edit applied migration files; add a new `V9__...sql` instead |
| Tests fail to start database | Start Docker Desktop; Testcontainers needs it |
| Kafka/Redis connection warnings | Expected when `KAFKA_ENABLED=false` / `REDIS_ENABLED=false` |
| Frontend sees CORS errors | Add the frontend URL to `APP_CORS_ALLOWED_ORIGINS` |

---

## More documentation

- [Architecture](../docs/ARCHITECTURE.md)
- [ER diagram](../docs/ER_DIAGRAM.md)
- [Fraud scoring](../docs/FRAUD_SCORING.md)
- [API list](../docs/API_LIST.md)
- [Phase plan](../docs/PHASE_PLAN.md)
- [Toolchain](../docs/TOOLCHAIN.md)
