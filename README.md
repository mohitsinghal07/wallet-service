# Wallet Service

A deliberately small wallet API focused on money invariants under concurrency.

## Architecture

The service has two runtime components in local development:

```text
Docker Compose
├── app  -> Spring Boot application (this repository's Docker image)
└── db   -> PostgreSQL 17 Docker image
```

On Render, the same application Docker image runs as a Web Service, while PostgreSQL is provided by Render as a managed database. PostgreSQL is intentionally kept outside the application image because the database needs its own persistence and lifecycle.

## Run locally

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`.

## API

Create/get wallet (caller is the bearer token value):

```bash
curl -X POST http://localhost:8080/wallets \
  -H 'Authorization: Bearer user-a'
```

Read wallet:

```bash
curl http://localhost:8080/wallets/WALLET_UUID \
  -H 'Authorization: Bearer user-a'
```

Deposit (fund a wallet — the only operation that increases the total money supply; transfers only move money between wallets). Idempotent: replaying the same `idempotency_key` credits once, and a reused key with a different body returns `409`:

```bash
curl -X POST http://localhost:8080/wallets/WALLET_UUID/deposit \
  -H 'Content-Type: application/json' \
  -d '{"amount_paise":100000,"idempotency_key":"seed-1"}'
```

Transfer:

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer user-a' \
  -d '{"from":"WALLET_UUID","to":"OTHER_UUID","amount_paise":500,"idempotency_key":"payment-123"}'
```

Read transfer:

```bash
curl http://localhost:8080/transfers/TRANSFER_UUID \
  -H 'Authorization: Bearer user-a'
```

## Correctness mechanism

Each transfer executes in one PostgreSQL transaction. The first operation attempts to insert the idempotency key into `transfers`, where the key is protected by a unique database constraint. Concurrent retries for the same key therefore collapse to one transfer row. A different request body using an existing key returns `409`.

For a new transfer, both wallet rows are locked with `SELECT ... FOR UPDATE` in deterministic UUID order. The debit uses a conditional update with `balance_paise >= amount`. The credit and transfer completion happen in the same transaction. If anything fails, the transaction rolls back.

Deterministic lock ordering prevents the classic A->B vs B->A deadlock where each transaction locks one wallet and waits for the other.

Serializable isolation was intentionally not used: row-level locking is narrower and easier to reason about for this two-wallet write set while retaining PostgreSQL's default Read Committed isolation.

A ledger/event-sourcing design was rejected because it adds projection and reconciliation complexity that is unnecessary for this exercise. Application mutexes/distributed locks were rejected because the database state should remain authoritative. Serializable isolation was rejected because it is heavier and can introduce serialization retries for otherwise independent work.

## Consistency vs availability

This is a consistency-first money workload. A transfer is accepted only when PostgreSQL can atomically commit the debit, credit, and idempotency record. During a database outage the service fails closed rather than inventing a locally accepted monetary state. The deliberate trade-off is lower availability during a database failure in exchange for preserving the money invariants.

## Application configuration

`src/main/resources/application.yml` contains application-level configuration and safe local defaults. It also configures Actuator exposure and Prometheus histogram generation.

Environment variables override these defaults in different environments:

- Docker Compose supplies the database container's host/credentials and the app connection URL.
- Render supplies the managed PostgreSQL connection details as environment variables.

This is intentional: `application.yml` describes how the Spring application behaves, while `docker-compose.yml` and `render.yaml` describe the infrastructure and environment in which it runs. They are not three competing copies of one configuration file.

## Database migrations

Flyway runs SQL migrations from `src/main/resources/db/migration`. The first migration is `V1__init.sql`. Flyway records which migrations have already run, so the schema can be created and upgraded repeatably during application startup.

## Observability

### Structured logs

Logs are JSON and include a request-scoped `correlation_id` stored in SLF4J MDC. Transfer domain events include creation, debit, credit, insufficient-funds decline, and idempotent replay.

### Metrics stack

Spring Boot Actuator exposes management endpoints. Micrometer collects application metrics, and the Prometheus registry converts those metrics to Prometheus exposition format.

The scrape endpoint is:

```text
GET /actuator/prometheus
```

Example:

```bash
curl http://localhost:8080/actuator/prometheus
```

HTTP request instrumentation is provided automatically by Spring Boot/Micrometer. The application enables a Prometheus histogram for `http.server.requests`, which gives Prometheus bucket data that can be used to calculate request latency percentiles such as p99.

Domain counters exposed by the application are:

```text
wallet_transfers_created_total
wallet_transfers_declined_insufficient_funds_total
wallet_transfers_idempotent_replays_total
```

### Prometheus queries

Assume Prometheus is scraping `/actuator/prometheus` into a Prometheus server.

#### 1. Request rate

```promql
sum(rate(http_server_requests_seconds_count[1m]))
```

What it means:

- `http_server_requests_seconds_count` is the cumulative number of observed HTTP requests.
- `rate(...[1m])` converts that counter into requests/second over the last minute.
- `sum(...)` combines all request paths/methods into one application-wide rate.

If the result is `8`, the service is handling roughly 8 HTTP requests per second over the recent one-minute window.

#### 2. p99 HTTP latency

```promql
histogram_quantile(
  0.99,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
)
```

Why the query is longer:

- `http_server_requests_seconds_bucket` contains cumulative histogram buckets such as `<= 0.1s`, `<= 0.25s`, `<= 0.5s`, etc.
- `rate(...[5m])` turns the bucket counters into recent per-second rates.
- `sum(...) by (le)` combines series while keeping the bucket boundary (`le`).
- `histogram_quantile(0.99, ...)` estimates the 99th percentile from those buckets.

If the result is `0.240`, the p99 latency is approximately 240 ms.

#### 3. 5xx error rate as a percentage

```promql
100 *
(
  sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count[5m]))
)
```

The numerator counts server-error responses (`500`-`599`). The denominator counts all HTTP requests. Multiplying by 100 makes the result directly readable as a percentage.

For example, `2.5` means roughly 2.5% of HTTP requests returned a 5xx response during the five-minute window.

#### 4. Transfer creation rate

```promql
rate(wallet_transfers_created_total[5m])
```

This shows the rate at which new transfer records are being created. It should increase once per accepted transfer, not once per HTTP retry of the same idempotency key.

#### 5. Insufficient-funds decline rate

```promql
rate(wallet_transfers_declined_insufficient_funds_total[5m])
```

This shows the rate of transfers declined because the source wallet did not have enough money.

#### 6. Idempotent replay rate

```promql
rate(wallet_transfers_idempotent_replays_total[5m])
```

This shows the rate of duplicate requests that reused an already-known idempotency key.

### Useful evaluation check

After running a same-key retry storm, these two metrics should tell a useful story:

```text
wallet_transfers_created_total       -> increases by 1
wallet_transfers_idempotent_replays_total -> increases for the retries
```

That is a much better signal than simply observing HTTP 200 responses because it demonstrates the domain behavior being exercised.

## Burst test

Requires bash, curl, jq:

```bash
BASE_URL=http://localhost:8080 N=25 K=50 ./scripts/burst.sh
```

The script exercises concurrent get-or-create, same-key retry storms, and simultaneous transfers in both directions while checking that the total of the two wallet balances is unchanged.


## Deployment

The included `render.yaml` provisions the Web Service plus managed PostgreSQL. The Web Service builds from the repository's Dockerfile; PostgreSQL is a separate managed resource.

## Project structure

```text
src/
├── main/
│   ├── java/com/wallet/
│   │   ├── api/       # HTTP controllers, filters, request/response models
│   │   ├── domain/    # Wallet/Transfer domain objects
│   │   ├── repo/      # PostgreSQL access
│   │   └── service/   # Money and wallet business logic
│   └── resources/
│       ├── application.yml       # Spring application configuration
│       ├── logback-spring.xml     # JSON logging configuration
│       └── db/migration/          # Flyway SQL migrations
└── test/
    └── java/com/wallet/           # tests
```
