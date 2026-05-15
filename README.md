# NIP Transfer Service

A production-grade Spring Boot microservice simulating **NIBSS NIP 2.0** interbank transfer infrastructure, built to demonstrate core banking API patterns relevant to Nigerian microfinance institutions.

> Built as a technical demonstration for Rex Microfinance Bank's Senior Java Engineer role.

---

## Overview

This service implements the key patterns required for NIP-compliant payment processing:

- **Idempotency** — duplicate transfer requests return the existing result without re-processing, preventing double-disbursement
- **Name Enquiry** — verifies beneficiary NUBAN account before funds are sent (CBN mandate)
- **Async processing** — HTTP response returns immediately with `PENDING`; gateway processing happens off-thread
- **Transaction lifecycle** — full `PENDING → PROCESSING → SUCCESSFUL/FAILED/REVERSED` state machine
- **NIBSS response code mapping** — maps `00`, `09`, `51`, `25`, `96` codes to internal status
- **Flyway migrations** — schema versioned and tracked, not auto-generated
- **API Key auth** — stateless security via `X-API-Key` header

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2, Spring Security |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| Migrations | Flyway |
| Build | Maven |
| Testing | JUnit 5, Mockito, MockMvc |
| CI/CD | GitHub Actions |
| Container | Docker (multi-stage build) |

---

## Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Maven 3.8+

### Run with Docker Compose

```bash
# Clone the repo
git clone https://github.com/MartinsO/nip-transfer-service
cd nip-transfer-service

# Start PostgreSQL + application
docker compose up -d

# Check health
curl http://localhost:8080/actuator/health
```

### Run locally

```bash
# Start PostgreSQL
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=nip_transfer_db \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15-alpine

# Run application
mvn spring-boot:run
```

---

## API Reference

All endpoints require `X-API-Key` header.

### Initiate Transfer

```http
POST /api/v1/transfers
Content-Type: application/json
X-API-Key: your-api-key
```

```json
{
  "idempotencyKey": "unique-client-ref-001",
  "senderAccountNumber": "0123456789",
  "senderBankCode": "090175",
  "senderName": "MARTINS JOJOLOLA",
  "beneficiaryAccountNumber": "9876543210",
  "beneficiaryBankCode": "058",
  "amount": 5000.00,
  "narration": "Loan repayment"
}
```

**Response: 202 Accepted**
```json
{
  "success": true,
  "message": "Transfer initiated. Poll status endpoint for updates.",
  "data": {
    "transactionId": "550e8400-e29b-41d4-a716-446655440000",
    "sessionId": "20240115143022090175000042",
    "status": "PENDING",
    "amount": 5000.00,
    "currency": "NGN"
  }
}
```

### Poll Transfer Status

```http
GET /api/v1/transfers/{transactionId}
X-API-Key: your-api-key
```

### Query by NIBSS Session ID

```http
GET /api/v1/transfers/session/{sessionId}
X-API-Key: your-api-key
```

### Account Transfer History

```http
GET /api/v1/transfers/account/{accountNumber}?page=0&size=20
X-API-Key: your-api-key
```

---

## Key Design Decisions

### Idempotency
Payment systems must handle network retries gracefully. Every transfer requires a client-supplied `idempotencyKey`. Before processing, the service checks for an existing record with that key:
- **PENDING/PROCESSING** → return current state
- **SUCCESSFUL/FAILED** → return `409 Conflict` with existing result
- **Not found** → process new transfer

This prevents double-disbursement even under poor network conditions.

### Async NIP Gateway Processing
The `POST /transfers` endpoint returns `202 Accepted` immediately with `PENDING` status. The actual NIBSS gateway call happens asynchronously via `@Async`. In production this would be a message queue (Kafka/RabbitMQ) for durability.

### NIBSS NIP 2.0 Compliance
Session ID format follows NIBSS specification: `YYYYMMDDHHMMSS + institutionCode(6) + sequence(6)`. Amount limits respect CBN single-transaction ceiling of ₦10,000,000.

---

## Running Tests

```bash
# All tests
mvn test

# With coverage report
mvn test jacoco:report
open target/site/jacoco/index.html
```

---

## Author

**Martins Jojolola** — Java/Spring Boot Backend Engineer, Lagos Nigeria  
[GitHub](https://github.com/MartinsO) | [LinkedIn](https://linkedin.com/in/DevMartinsO)
