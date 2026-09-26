# E-Commerce Shop — Spring Boot Microservices Platform

A Spring Boot 4 / Java 21 microservices e-commerce backend: service discovery (Eureka),
centralized config (Spring Cloud Config), an API gateway with JWT authentication,
Kafka-based order/payment choreography, Redis caching + token blocklisting, and
Postgres-per-service persistence.

| Service | Port (host) | Responsibility |
|---|---|---|
| config-service | 8888 | Spring Cloud Config server (native profile, serves `config/*.yml`) |
| discovery-service | 8761 | Eureka service registry |
| api-gateway | 9091 | Single entry point, JWT validation, routing, rate limiting |
| auth-service | 8080 | Registration/login/refresh/logout, issues JWTs |
| user-service | 8082 | User persistence, called internally by auth-service via Feign |
| product-service | 8081 | Product catalog + admin CRUD |
| order-service | 8083 (+5005 debug) | Order orchestration, Kafka producer/consumer |
| payment-service | — (internal only) | Payment processing, Kafka producer/consumer |
| notification-service | — (internal only) | Email notifications via Kafka, sent through MailHog |
| kafka-service | — | Message broker (Apache Kafka, KRaft mode) |
| redis | 6379 | Cache + JWT refresh-token blocklist |
| user/product/order/payment-postgres | 5432–5435 | One Postgres instance per service |
| mailhog | 8025 (UI), 1025 (SMTP) | Local mail sink for notification-service |

All client traffic goes through the **api-gateway on port 9091**. The individual
service ports are exposed for local debugging only; in a real deployment they
would not be published to the host.

---

## 1. Prerequisites

- JDK 21
- Maven 3.9+ (or use the bundled `./mvnw`)
- Docker Engine + Docker Compose v2 (`docker compose`, not the old `docker-compose`)

Postgres, Redis, Kafka and MailHog all run as containers — you do not need to
install them locally.

---

## 2. Setup

### 2.1 Clone and apply the fixes above
```bash
git clone <your-fork-url> e-commerce-shop
cd e-commerce-shop
# apply fixes 2.1 and 2.2
```

### 2.2 Create your `.env` file
Docker Compose reads `.env` from the repo root automatically. Create one:

```bash
cat > .env << 'EOF'
# Shared secrets — used by the gateway and every downstream service
SECURITY_INTERNAL_API_KEY=change-me-internal-key

# JWT (auth-service issues, api-gateway validates)
JWT_SECRET=replace-with-a-long-random-base64-secret
JWT_EXPIRATION=3600000
JWT_REFRESH_TOKEN=86400000

# Redis
REDIS_PASSWORD=change-me-redis-password

# Postgres — same password reused across the 4 per-service instances
DATASOURCE_PASSWORD=change-me-postgres-password
EOF
```

`JWT_SECRET` must be a real secret (it feeds an HMAC signer) — don't leave
the placeholder in anything beyond local dev. `JWT_EXPIRATION` /
`JWT_REFRESH_TOKEN` are in milliseconds (1 hour / 24 hours above).

### 3.3 Build the Maven reactor
The Dockerfiles copy a pre-built jar, so build all modules first, in
dependency order, from the repo root (this also builds the shared libraries —
`common-dto`, `common-exception`, `common-security`, `common-gateway-security`,
`feign-client-config`, `kafka-service` — that every service depends on):

```bash
./mvnw clean package -DskipTests
```

Drop `-DskipTests` if you want the full suite (JUnit 5 + Mockito + AssertJ +
Testcontainers) to run as part of the build — expect it to be noticeably
slower since Testcontainers spins up real Postgres/Kafka containers per
module.

### 2.4 Start the stack
```bash
docker compose up --build -d
```

Compose brings services up in dependency order via `depends_on` +
healthchecks: `config-service` → `discovery-service` → `api-gateway`, with
each business service waiting on its own Postgres and on Redis being healthy.

Check everything registered correctly:
```bash
# Eureka dashboard — should list api-gateway, auth-service, user-service,
# product-service, order-service, payment-service, notification-service
open http://localhost:8761

# MailHog UI — order-confirmation emails land here instead of a real inbox
open http://localhost:8025
```

### 2.5 Tear down
```bash
docker compose down          # keep volumes (Postgres data)
docker compose down -v       # also wipe Postgres/Redis volumes
```

---

## 4. Authentication model

All traffic goes through `api-gateway:9091`. The gateway:

1. Lets `/api/v1/auth/**` and `/actuator/health|info` through unauthenticated.
2. For everything else, requires `Authorization: Bearer <token>`, validates
   the JWT, and checks it isn't in the Redis logout blocklist.
3. On success, it **injects internal headers** downstream
   (`X-User-Id`, `X-User-Email`, `X-User-Role`, `X-Internal-Api-Key`) — each
   service's `InternalAuthenticationFilter` trusts these headers and rejects
   any request that doesn't carry a valid `X-Internal-Api-Key`, which means
   **you cannot call product-service/order-service/payment-service directly
   on their host ports and expect it to work** — always go through 9091.

Role is self-declared at registration (`RegisterRequest.role`) — there's no
admin-invite or promotion flow. Fine for a portfolio project; call this out
explicitly if anyone asks about it as a production design, because letting a
client pick its own privilege level at signup is not something you'd ship.

---

## 5. Usage examples

Base URL for everything below: `http://localhost:9091`.

### 5.1 Register a user
```bash
curl -X POST http://localhost:9091/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Ada",
    "lastName": "Lovelace",
    "email": "ada@example.com",
    "password": "secret1",
    "role": "USER"
  }'
```
Register a second account with `"role": "ADMIN"` — you'll need it for the
catalog-management calls in 5.4.

Response:
```json
{ "token": "<jwt>", "refreshToken": "<refresh-jwt>" }
```

### 5.2 Log in
```bash
curl -X POST http://localhost:9091/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "email": "ada@example.com", "password": "secret1" }'
```
Save the token:
```bash
TOKEN=$(curl -s -X POST http://localhost:9091/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "email": "ada@example.com", "password": "secret1" }' | jq -r .token)
```

### 5.3 Refresh / logout
```bash
curl -X POST http://localhost:9091/api/v1/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{ "refreshToken": "<refresh-jwt>" }'

curl -X POST http://localhost:9091/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{ "token": "<jwt>", "refreshToken": "<refresh-jwt>" }'
```
Logout adds the token to the Redis blocklist — the gateway will reject it on
the next request even though it hasn't expired yet.

### 5.4 Catalog — browse (any authenticated user)
```bash
curl "http://localhost:9091/api/v1/products?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:9091/api/v1/products/category?category=JEANS" \
  -H "Authorization: Bearer $TOKEN"
```

### 5.5 Catalog — admin CRUD (requires an ADMIN-role token)
```bash
curl -X POST http://localhost:9091/api/v1/admin/products/create \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Classic Denim Jacket",
    "price": 79.99,
    "quantity": 50,
    "category": "JACKETS",
    "imageUrl": "https://example.com/img/denim-jacket.jpg",
    "isAvailable": true
  }'
```
Valid `category` values are the `Category` enum constants, e.g.
`MENS_CLOTHING`, `T_SHIRTS`, `JEANS`, `SNEAKERS`, `HOODIES` — see
`product-service/.../model/Category.java` for the full list.

### 5.6 Place an order
```bash
curl -X POST http://localhost:9091/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "products": [
      { "productId": 1, "quantity": 2 },
      { "productId": 3, "quantity": 1 }
    ]
  }'
```
Do not pass `X-User-Id` yourself — the gateway sets it from the JWT after
authenticating you; the header `OrderController` reads is a gateway-injected
value, not client input.

Creating the order kicks off the Kafka choreography: order-service publishes
`order.created`, product-service reserves stock, payment-service charges and
publishes the result, order-service updates status, and notification-service
sends a confirmation email you can see in MailHog
(`http://localhost:8025`).

```bash
curl http://localhost:9091/api/v1/orders \
  -H "Authorization: Bearer $TOKEN"
```

### 5.7 Payments
```bash
curl -X POST http://localhost:9091/api/v1/payments/1/confirm \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "orderId": 1 }'

curl http://localhost:9091/api/v1/payments/payment/1 \
  -H "Authorization: Bearer $TOKEN"
```
Check `PaymentController`/its DTO for the exact confirm-request body if it
has evolved since this was written — verify against the current source
rather than trusting this README blindly, same as you should for anyone
else's docs.

---

## 6. Running tests

```bash
./mvnw test                        # whole reactor
./mvnw -pl user-service test        # single module
```
Modules that use Testcontainers (`@Testcontainers` + `@SpringBootTest`) need
a working Docker daemon on the machine running the tests, same as for
`docker compose` itself — CI runners need Docker-in-Docker or an equivalent.

