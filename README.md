# OrderFlow application

Two small Java 21 services for a DevOps release lab. Each service has its own
Maven Wrapper so it can be built independently in CI and as a container.

| Service | Default port | Purpose |
| --- | ---: | --- |
| `apps/pricing-api` | 8081 | Versioned SKU price catalog |
| `apps/orders-api` | 8080 | Order API, PostgreSQL persistence, receipt delivery |

## Request path

```text
client -> orders-api -> pricing-api
                     -> PostgreSQL (order and receipt state)
receipt worker -> local directory or S3
```

The orders API requires an `Idempotency-Key` on create. A retry with the same
key and payload returns the existing order; the same key with a different payload
returns HTTP 409. The database unique constraint also resolves a concurrent
retry race. The receipt worker scans committed `PENDING` rows and writes to a
deterministic object key, then marks the row `STORED`. Failed writes remain
pending for a later retry. In this POC, multiple workers may write the same
receipt object, which is safe because the object key and content are stable.

## API contract

```http
GET /api/v1/prices/BOOK-001
GET /api/v1/version
POST /api/v1/orders
Idempotency-Key: checkout-123
Content-Type: application/json

{"sku":"BOOK-001","quantity":2}

GET /api/v1/orders/{id}
```

Prices are in USD. The catalog contains `BOOK-001` ($12.50), `MUG-001`
($8.75) and `BAG-001` ($24.00). The order response includes `receiptStatus`,
which changes from `PENDING` to `STORED` after delivery.

Actuator probe endpoints are `/actuator/health/liveness` and
`/actuator/health/readiness`. The orders service also exposes
`/actuator/prometheus` for a later metrics lab. Kubernetes readiness should
be wired to the readiness endpoint; liveness must not depend on pricing or S3.

## Build and test

From each service directory, run:

```sh
./mvnw test
./mvnw package
```

The orders tests use an isolated H2 database in PostgreSQL compatibility mode
to verify the migration, persistence, idempotency, HTTP endpoint and local
receipt worker. This does **not** replace the planned PostgreSQL integration
check in local Compose or RDS. The packaged service targets Java 21.

## Runtime configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/orderflow` | Orders JDBC URL |
| `DATABASE_USERNAME` | `orderflow` | Orders database user |
| `DATABASE_PASSWORD` | empty | Set via local environment or a secret in AWS |
| `PRICING_URL` | `http://localhost:8081` | Pricing service URL |
| `RECEIPT_STORE` | `local` | `local` or `s3` |
| `RECEIPT_DIR` | `./receipts` | Local receipt directory |
| `RECEIPT_BUCKET` | empty | Required in S3 mode |
| `RECEIPT_PREFIX` | `receipts/` | S3 key prefix |
| `APP_VERSION` | `0.1.0` | Visible release version |
| `SERVER_PORT` | 8080 for orders, 8081 for pricing | HTTP port |

In AWS, the S3 client uses the default AWS credential chain, which will use
EKS Pod Identity for the orders service account. Do not place AWS access keys
in configuration or Git. The Terraform/GitOps repositories will supply
different namespace-specific S3 prefixes and database configuration.

## Release contract

Each service has a separate image. A successful app PR goes to `main`, then
CI builds an immutable image digest. The same digest is promoted by reviewed
GitOps pull requests through `dev`, `stage`, and `prod-sim`. The release
tag records the app commit and image digest. A rollback restores a previous
digest without rebuilding it.

The schema migration is additive. Future schema changes must keep old and new
service versions working during blue/green promotion. A destructive column
change requires a later contract migration, after the old version is retired.
