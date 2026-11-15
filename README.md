# Chubb APAC Claims Management Backend

A  easy-to-run **Java 17 + Spring Boot** REST API demonstrating how an insurance claim moves from submission through review to approval, settlement, or rejection. Uses **MySQL** for persistence and **Kafka** for lifecycle notifications.

**Reviewer quick start:** Docker is the only prerequisite. No local Java, Maven, MySQL, or Kafka installation is needed.

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

By running Above three commands you will be able to access the API at <http://localhost:8080/swagger-ui.html>. You can also view application logs with `docker compose logs -f app`.

The passwords in `.env.example` are **development examples only**. Replace them for anything beyond a local demo. The `.env` file is ignored by Git.

- Swagger (try the API): <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Application logs: `docker compose logs -f app` (Ctrl+C to exit)

If port 8080 is already being used on your machine, stop the other application or change `"8080:8080"` to `"8081:8080"` in `docker-compose.yml`, then use port 8081 for the URLs above.

## 1. What the project does

- Submit a **MOTOR** or **PROPERTY** claim with name, email, market, incident, and estimated amount.
- Track a claim using its claim number and claimant email.
- Search claims by market and/or status, with pagination and supported sort fields.
- Assign a claim to an officer, start review, request more information (status change only), approve, reject, or settle a claim.
- View an officer's outstanding workload and the *raw* sum of estimates for active claims.
- Persist changes in MySQL with Flyway-managed schema and JPA optimistic locking.
- Publish a Kafka lifecycle event when a claim is created or its status changes.
- Return **consistent, helpful JSON errors** for invalid input rather than exposing internal exceptions.

### Architecture (intentionally simple)

```text
             HTTP / Swagger
                   |
              ClaimController   <-- input validation, pagination checks
                   |
              ClaimService      <-- transactions and use-case orchestration
                 / | \
                /  |  \
        Claim aggregate  ClaimRepository  ClaimEventPublisher
        (state rules)       |                    |
                          MySQL                Kafka
                     (Flyway schema)       claims.lifecycle
```

The project is a **layered modular monolith**, not microservices. That keeps this assignment small, readable, and easier to review. Request/response DTOs are separate from persistence entities. State-transition rules live in the `Claim` aggregate, and errors are translated centrally in `ApiExceptionHandler`.

## 2. Requirements and running it

### Recommended: Docker Compose (complete stack)

Prerequisite: Docker Desktop, or a Docker-compatible engine with Compose.

```bash
# Run from the project root
cp .env.example .env
# Edit .env and choose passwords if desired
docker compose up --build -d

docker compose ps
docker compose logs --tail=100 app
curl http://localhost:8080/actuator/health
```

Compose starts:

| Container | Purpose | Connectivity |
|---|---|---|
| `app` | Spring Boot API | Mac/host port 8080 |
| `mysql` | MySQL 8.4 database `claimsdb` | Internal `mysql:3306` |
| `kafka` | Apache Kafka 4.0 broker | Internal `kafka:9092` |

MySQL and Kafka are **not exposed on your host**. This avoids collisions if you already have MySQL on port 3306 or Kafka on 9092 on your Mac. The app uses the Docker service names, **not `localhost`**, to reach them. The MySQL volume `mysql_data` retains database contents between restarts.

To inspect the database:

```bash
docker compose exec mysql mysql -u claims -p claimsdb
# Enter the MYSQL_PASSWORD value from your .env file, then:
# SHOW TABLES;
# SELECT claim_number, status, market FROM claims;
```

To inspect Kafka messages:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:9092 --list

docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic claims.lifecycle --from-beginning
```

The Spring Kafka configuration declares the `claims.lifecycle` topic for creation when the broker is available. Kafka data is not persisted in a named volume in this sample; it is a demo broker, not a durable messaging deployment.

Stop the stack without deleting MySQL data:

```bash
docker compose down
```

**Warning:** `docker compose down -v` also deletes the project's MySQL volume and all its saved claims. Do not use it unless you deliberately want a fresh database.

### Alternative: run Spring Boot locally

If you already have Java 17+, Maven 3.9+, MySQL 8.4, and Kafka installed/running on your machine:

```bash
export DB_URL='jdbc:mysql://localhost:3306/claimsdb'
export DB_USERNAME='root'
export DB_PASSWORD='your-local-mysql-password'
export KAFKA_BOOTSTRAP_SERVERS='localhost:9092'
mvn spring-boot:run
```

Create the `claimsdb` database yourself for this mode, and ensure MySQL credentials have schema/migration permissions. Flyway creates the table when the app starts. Local MySQL and Docker MySQL are **separate databases**. If using Docker Compose only, follow the recommended quick start instead.

## 3. API endpoints

Base URL: `http://localhost:8080/api/v1/claims`

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/` | Submit a new claim, returns **201** |
| `GET` | `/track?claimNumber=...&email=...` | Track a claim using its number and email |
| `GET` | `/?market=SG` | Search claims in one market |
| `GET` | `/?status=SUBMITTED&market=SG` | Filter by both status and market |
| `PUT` | `/{id}/assignment` | Assign a submitted claim to an officer |
| `POST` | `/{id}/review` | Start or resume review |
| `POST` | `/{id}/information-request` | Flag a claim as requiring information |
| `POST` | `/{id}/approve` | Approve with an amount |
| `POST` | `/{id}/reject` | Reject an under-review claim |
| `POST` | `/{id}/settle` | Settle an approved claim |
| `GET` | `/officer/{officerName}/workload` | Get active claims assigned to an officer |
| `GET` | `/management/outstanding-exposure` | Sum open claims' estimated amounts (see caveat below) |

**Pagination:** `?page=0&size=10&sort=createdAt,desc`. Size is capped at 100. Sort fields must be actual `Claim` properties (for example `createdAt`, `updatedAt`, `claimNumber`, `claimantName`, `status`, `market`, `assignedOfficer`, `estimatedAmount`); unsupported fields return 400 instead of a JPA error.

### Walkthrough (copy and paste)

**1. Submit a claim**

```bash
curl -i -X POST http://localhost:8080/api/v1/claims \
  -H 'Content-Type: application/json' \
  -d '{"claimantName":"Jane Doe","claimantEmail":"jane@example.com","type":"MOTOR","market":"SG","incidentDescription":"Vehicle collision","estimatedAmount":2500.00}'
```

Save the returned **`id`** and **`claimNumber`** (e.g. `CHB-A1B2C3D4`). The generated values will be different for every new claim.

**2. Track it**

```bash
curl 'http://localhost:8080/api/v1/claims/track?claimNumber=CHB-A1B2C3D4&email=jane@example.com'
```

Replace the example number with the actual response's `claimNumber`.

**3. Assign, review, approve and settle**

In the commands below, replace `<claim-id>` with the UUID returned by Step 1 (without the angle brackets).

```bash
curl -X PUT 'http://localhost:8080/api/v1/claims/<claim-id>/assignment' \
  -H 'Content-Type: application/json' -d '{"officerId":"officer-123"}'

curl -X POST 'http://localhost:8080/api/v1/claims/<claim-id>/review'

curl -X POST 'http://localhost:8080/api/v1/claims/<claim-id>/approve' \
  -H 'Content-Type: application/json' -d '{"approvedAmount":2000.00}'

curl -X POST 'http://localhost:8080/api/v1/claims/<claim-id>/settle'
```

Or use **Swagger UI** to avoid editing curl commands. To test rejection, create a *different* claim and reject it while it is `UNDER_REVIEW`. Settled/rejected claims cannot be reopened in this demo.

## 4. Lifecycle rules

```text
SUBMITTED --> ASSIGNED --> UNDER_REVIEW --> APPROVED --> SETTLED
                                |    ^          
                                v    |
                         INFORMATION_REQUIRED
                                |
                                +----+  (resume review)

UNDER_REVIEW --> REJECTED
```

- A claim may be assigned only once, from `SUBMITTED`.
- Review starts from `ASSIGNED`; it may resume after `INFORMATION_REQUIRED`.
- Requesting information or approving/rejecting requires `UNDER_REVIEW`.
- Settlement requires `APPROVED`.
- An approved amount must be greater than zero and must not exceed the estimate.
- Market is normalized to uppercase; blank/invalid inputs are rejected.

**Scope note:** `information-request` currently changes the claim's status. It does **not** store the question, an answer, attachments, or authorization decisions. A complete claimant/staff information exchange is future work.

## 5. Error handling: what the API returns

Invalid **client input** produces a meaningful **4xx** response. Genuine **server failures** still correctly produce HTTP **500**, but with a safe message that hides exception internals.

Example (invalid email on claim creation):

```json
{
  "timestamp": "2026-11-15T12:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "claimantEmail: must be a well-formed email address",
  "path": "/api/v1/claims"
}
```

The timestamp and exact validation wording may differ.

| HTTP | Error code | Examples |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Blank name, invalid email or market, negative amount, invalid request DTO |
| 400 | `INVALID_JSON` | Malformed JSON, missing JSON body, unknown enum/type |
| 400 | `INVALID_PARAMETER` | Invalid UUID or unknown claim status in a query |
| 400 | `MISSING_PARAMETER` | Required `email` or `claimNumber` is missing |
| 400 | `INVALID_REQUEST` | Blank tracking parameters, unsupported sort field, status without market |
| 404 | `CLAIM_NOT_FOUND` | Unknown claim UUID or claim number/email combination |
| 404 | `NOT_FOUND` | Unknown API path |
| 405 | `METHOD_NOT_ALLOWED` | HTTP method not supported by the endpoint |
| 409 | `INVALID_CLAIM_STATE` | Trying to settle a claim before approval |
| 409 | `CONCURRENT_UPDATE` | A claim was modified concurrently (optimistic lock) |
| 409 | `DATA_CONFLICT` | Database uniqueness/constraint conflict |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Sending plain text instead of JSON |
| 500 | `INTERNAL_ERROR` | Unexpected server error, safely masked and logged |

Validation uses **Jakarta Bean Validation**, domain rules are enforced in `Claim`, and `ApiExceptionHandler` maps exceptions to public HTTP responses. Clients should rely on `status` and `code`, not the exact English error text.

## 6. Persistence and Kafka

**MySQL/Flyway:** `src/main/resources/db/migration/V1__create_claims.sql` creates the `claims` table. Fields include UUID, claim number, claimant details, type, market, description, estimated/approved amounts, status, assigned officer, timestamps, and JPA `@Version`. Indexed columns support market/status filtering and staff workloads. Hibernate uses `ddl-auto: validate` to check the schema rather than modifying it silently.

**Kafka:** `claims.lifecycle` publishes JSON events keyed by claim UUID. Example event fields: `claimId`, `claimNumber`, `eventType`, `status`, `market`, `occurredAt`.

**Reliability trade-off:** The assignment publishes to Kafka directly from the service. Sending an event and committing a DB transaction are not atomic, and Kafka sends are asynchronous. A production design should use a **transactional outbox**, retries, idempotent consumers, and monitoring. This was intentionally not added to keep the demo focused.

**Financial reporting caveat:** The exposure endpoint sums active claims' estimates across all markets as a basic demo. **Do not interpret it as a currency-converted APAC total**. Production reporting must group by currency/market and apply an explicit conversion policy.

## 7. Tests and code quality

From a machine with Java 17+, Maven, and optionally Docker:

```bash
mvn clean test
mvn verify
```

- **Domain tests:** valid lifecycle, invalid transitions, assignment and amount rules, information-request/resume.
- **Service tests:** create and normalize input, claimant tracking, assignment protection, filters, exposure query.
- **MVC tests:** successful create, malformed JSON, enum errors, invalid input/UUID/status, missing parameters, sorting, not-found, invalid state, unsupported HTTP method/content type, safe 500 fallback.
- **Repository integration tests:** MySQL Testcontainers for tracking, aggregation, and filtering. These tests require Docker and are configured to **skip without Docker**.
- **JaCoCo:** Open `target/site/jacoco/index.html` after `mvn verify` for a coverage report.

The Dockerfile builds with tests skipped so reviewers can start the demo quickly; **run the test commands separately** to verify behavior. No coverage threshold is claimed.

## 8. Design trade-offs and next steps

**Implemented:** layered REST/DTO/service/entity/repository design, clean state transitions, validation and safe API errors, MySQL schema migrations, pagination, integration with Kafka, OpenAPI docs, domain/service/MVC/repository tests, Docker Compose.

**Not implemented / intentionally deferred:**

1. **Authentication and authorization** for claimants, claims officers, and managers. Current staff endpoints are unauthenticated. **Never expose this demo publicly** or load real customer data into it.
2. Actual information-request/question/answer/attachment persistence and claimant ownership checks.
3. Immutable status-history and assessment/audit records.
4. Transactional outbox and reliable Kafka delivery guarantees.
5. Currency-aware exposure, market-specific regulations, and financial audit rules.
6. Production observability, request correlation IDs, rate limiting, and security hardening.

These are architectural improvements for a production system, **not hidden features** of this assessment demo. The goal here is to show readable engineering choices, realistic validations, and an end-to-end running backend without unnecessary infrastructure complexity.
