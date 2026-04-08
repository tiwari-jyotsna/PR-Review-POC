# Loyalty Wallet System

A production-grade, ledger-based loyalty points engine built with Spring Boot 3.2 and Java 17.
All state is held in-memory using thread-safe, append-only ledgers — no database required.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Reference](#api-reference)
  - [Earn Points](#1-earn-points)
  - [Redeem Points](#2-redeem-points)
  - [Get Balance](#3-get-wallet-balance)
  - [Transaction History](#4-transaction-history)
- [Swagger / OpenAPI](#swagger--openapi)
- [Request Correlation & Logging](#request-correlation--logging)
- [Error Handling](#error-handling)
- [Thread-Safety Model](#thread-safety-model)
- [Sample cURL Flows](#sample-curl-flows)

---

## Architecture Overview

```
HTTP Request
     │
     ▼
┌─────────────────────┐
│   WalletController  │  ← Input validation (@Valid), requestId resolution
│   /api/wallet/**    │    Entry/exit logging per endpoint
└────────┬────────────┘
         │ constructor injection
         ▼
┌─────────────────────┐
│   WalletService     │  ← Business logging (earn/redeem actions)
│   (interface)       │    Orchestrates store calls, maps to DTOs
│   WalletServiceImpl │
└────────┬────────────┘
         │ constructor injection
         ▼
┌─────────────────────────────────────────────────────┐
│              InMemoryWalletStore                    │
│                                                     │
│  ConcurrentHashMap<userId, List<Transaction>>       │  ← Append-only ledger
│  ConcurrentHashMap<userId, Object> userLocks        │  ← Per-user monitors
│                                                     │
│  earn()   → balance-read + append (synchronized)   │
│  redeem() → balance-read + check + append (atomic) │
│  getStats()  → full ledger scan (snapshot)         │
└─────────────────────────────────────────────────────┘
```

**Ledger principle:** Balance is never stored directly. It is always derived by summing all
`EARN` transactions and subtracting all `REDEEM` transactions. This makes the audit trail
the single source of truth.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.2.4 |
| Web | Spring MVC (embedded Tomcat) |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| Boilerplate | Lombok (`@Getter`, `@Builder`, `@Slf4j`, `@Data`) |
| API Docs | springdoc-openapi 2.3.0 (OpenAPI 3.1 / Swagger UI) |
| Logging | SLF4J + Logback (Spring Boot default) |
| Build | Apache Maven 3.x |

---

## Project Structure

```
lms-poc/
├── pom.xml
└── src/main/
    ├── resources/
    │   └── application.yml
    └── java/com/loyalty/wallet/
        ├── LoyaltyWalletApplication.java
        ├── config/
        │   └── SwaggerConfig.java
        ├── controller/
        │   └── WalletController.java
        ├── dto/
        │   ├── EarnPointsRequest.java
        │   ├── RedeemPointsRequest.java
        │   ├── TransactionResponse.java
        │   ├── WalletResponse.java
        │   └── ErrorResponse.java
        ├── exception/
        │   ├── UserNotFoundException.java
        │   ├── InsufficientPointsException.java
        │   └── GlobalExceptionHandler.java
        ├── model/
        │   ├── Transaction.java
        │   └── TransactionType.java
        ├── service/
        │   ├── WalletService.java
        │   └── impl/WalletServiceImpl.java
        └── store/
            └── InMemoryWalletStore.java
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Build

```bash
mvn clean package -DskipTests
```

### Run

```bash
# Via Maven plugin
mvn spring-boot:run

# Via JAR
java -jar target/wallet-1.0.0.jar
```

The server starts on **http://localhost:8080**.

---

## Configuration

All settings live in [src/main/resources/application.yml](src/main/resources/application.yml).

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP listen port |
| `server.servlet.context-path` | `/` | Application context root |
| `logging.level.com.loyalty.wallet` | `DEBUG` | Package-level log verbosity |
| `logging.file.name` | `logs/loyalty-wallet.log` | Rolling log file path |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI mount point |
| `springdoc.api-docs.path` | `/v3/api-docs` | Raw OpenAPI JSON |

To override any property at runtime:

```bash
java -jar target/wallet-1.0.0.jar --server.port=9090
```

---

## API Reference

**Base URL:** `http://localhost:8080`

All request and response bodies are `application/json`.
Pass `X-Request-ID` header on every request to correlate calls through logs (auto-generated if omitted).

---

### 1. Earn Points

Credits loyalty points to a user's wallet. Creates the wallet automatically on first use.

```
POST /api/wallet/earn
```

**Request Headers**

| Header | Required | Description |
|---|---|---|
| `Content-Type` | Yes | `application/json` |
| `X-Request-ID` | No | Client-supplied correlation ID |

**Request Body**

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `userId` | string | Yes | max 64 chars | Wallet owner identifier |
| `points` | long | Yes | ≥ 1 | Points to credit |
| `description` | string | No | max 255 chars | Human-readable reason |

```json
{
  "userId": "user-001",
  "points": 500,
  "description": "Welcome bonus"
}
```

**Response — 201 Created**

```json
{
  "transactionId": "txn-a1b2c3d4e5f6",
  "userId": "user-001",
  "type": "EARN",
  "points": 500,
  "balanceAfter": 500,
  "description": "Welcome bonus",
  "timestamp": "2024-04-07T10:15:30.123Z"
}
```

**Error Responses**

| Status | Error Code | Trigger |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing/invalid fields |

---

### 2. Redeem Points

Debits loyalty points from a user's wallet.

```
POST /api/wallet/redeem
```

**Request Headers**

| Header | Required | Description |
|---|---|---|
| `Content-Type` | Yes | `application/json` |
| `X-Request-ID` | No | Client-supplied correlation ID |

**Request Body**

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `userId` | string | Yes | max 64 chars | Wallet owner identifier |
| `points` | long | Yes | ≥ 1 | Points to debit |
| `description` | string | No | max 255 chars | Human-readable reason |

```json
{
  "userId": "user-001",
  "points": 200,
  "description": "Discount on Order #ORD-20240401"
}
```

**Response — 200 OK**

```json
{
  "transactionId": "txn-b7c8d9e0f1a2",
  "userId": "user-001",
  "type": "REDEEM",
  "points": 200,
  "balanceAfter": 300,
  "description": "Discount on Order #ORD-20240401",
  "timestamp": "2024-04-07T10:22:45.456Z"
}
```

**Error Responses**

| Status | Error Code | Trigger |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing/invalid fields |
| 404 | `USER_NOT_FOUND` | No wallet exists for this userId |
| 422 | `INSUFFICIENT_POINTS` | Current balance < requested points |

---

### 3. Get Wallet Balance

Returns the current balance and lifetime statistics derived from the ledger.

```
GET /api/wallet/{userId}
```

**Path Parameters**

| Parameter | Description |
|---|---|
| `userId` | Wallet owner identifier |

**Response — 200 OK**

```json
{
  "userId": "user-001",
  "balance": 300,
  "totalEarned": 500,
  "totalRedeemed": 200,
  "transactionCount": 2,
  "lastUpdated": "2024-04-07T10:22:45.456Z"
}
```

**Error Responses**

| Status | Error Code | Trigger |
|---|---|---|
| 404 | `USER_NOT_FOUND` | No wallet exists for this userId |

---

### 4. Transaction History

Returns all ledger entries for a wallet in chronological order (oldest first).

```
GET /api/wallet/{userId}/transactions
```

**Path Parameters**

| Parameter | Description |
|---|---|
| `userId` | Wallet owner identifier |

**Response — 200 OK**

```json
[
  {
    "transactionId": "txn-a1b2c3d4e5f6",
    "userId": "user-001",
    "type": "EARN",
    "points": 500,
    "balanceAfter": 500,
    "description": "Welcome bonus",
    "timestamp": "2024-04-07T10:15:30.123Z"
  },
  {
    "transactionId": "txn-b7c8d9e0f1a2",
    "userId": "user-001",
    "type": "REDEEM",
    "points": 200,
    "balanceAfter": 300,
    "description": "Discount on Order #ORD-20240401",
    "timestamp": "2024-04-07T10:22:45.456Z"
  }
]
```

**Error Responses**

| Status | Error Code | Trigger |
|---|---|---|
| 404 | `USER_NOT_FOUND` | No wallet exists for this userId |

---

### Standard Error Envelope

All errors return the same structure:

```json
{
  "status": 422,
  "error": "INSUFFICIENT_POINTS",
  "message": "Insufficient points for userId=user-001: balance=100, requested=500",
  "path": "/api/wallet/redeem",
  "timestamp": "2024-04-07T10:30:00.000Z",
  "requestId": "req-abc12345",
  "fieldErrors": []
}
```

For validation errors (`400`), `fieldErrors` is populated:

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/wallet/earn",
  "timestamp": "2024-04-07T10:30:00.000Z",
  "requestId": "req-abc12345",
  "fieldErrors": [
    { "field": "points", "message": "points must be at least 1" },
    { "field": "userId", "message": "userId must not be blank" }
  ]
}
```

---

## Swagger / OpenAPI

| Resource | URL |
|---|---|
| **Swagger UI** (interactive) | http://localhost:8080/swagger-ui.html |
| **OpenAPI JSON** (machine-readable) | http://localhost:8080/v3/api-docs |
| **OpenAPI YAML** | http://localhost:8080/v3/api-docs.yaml |

The Swagger UI provides a fully interactive sandbox — all four endpoints can be called directly
from the browser with no additional tooling. Use the **Try it out** button on any operation.

![Swagger UI shows: Loyalty Wallet tag with 4 operations — POST /earn, POST /redeem, GET /{userId}, GET /{userId}/transactions]

---

## Request Correlation & Logging

### Sending a correlation ID

Pass `X-Request-ID` on any outbound call:

```bash
curl -H "X-Request-ID: my-trace-id-001" http://localhost:8080/api/wallet/user-001
```

If the header is absent, the controller auto-generates a short UUID (e.g. `req-a1b2c3d4`).

### Log format

```
2024-04-07 10:15:30.123 [http-nio-8080-exec-1] INFO  [req-a1b2c3d4] c.l.w.controller.WalletController - --> POST /api/wallet/earn: userId=user-001, points=500
2024-04-07 10:15:30.125 [http-nio-8080-exec-1] INFO  [req-a1b2c3d4] c.l.w.service.impl.WalletServiceImpl - Processing EARN: userId=user-001, points=500
2024-04-07 10:15:30.126 [http-nio-8080-exec-1] INFO  [req-a1b2c3d4] c.l.w.store.InMemoryWalletStore - EARN recorded: userId=user-001, points=500, balanceAfter=500
2024-04-07 10:15:30.127 [http-nio-8080-exec-1] INFO  [req-a1b2c3d4] c.l.w.service.impl.WalletServiceImpl - EARN complete: txnId=txn-a1b2c3d4e5f6, userId=user-001, points=500, balanceAfter=500
2024-04-07 10:15:30.128 [http-nio-8080-exec-1] INFO  [req-a1b2c3d4] c.l.w.controller.WalletController - <-- POST /api/wallet/earn: txnId=txn-a1b2c3d4e5f6, status=201
```

Logs are written to **console** and **`logs/loyalty-wallet.log`** simultaneously.

---

## Error Handling

| Exception | HTTP Status | Error Code |
|---|---|---|
| `MethodArgumentNotValidException` | 400 Bad Request | `VALIDATION_ERROR` |
| `UserNotFoundException` | 404 Not Found | `USER_NOT_FOUND` |
| `InsufficientPointsException` | 422 Unprocessable Entity | `INSUFFICIENT_POINTS` |
| Any other `Exception` | 500 Internal Server Error | `INTERNAL_ERROR` |

All cases are handled centrally in `GlobalExceptionHandler` (`@RestControllerAdvice`).
Validation failures and domain exceptions are logged at `WARN`; unexpected errors at `ERROR`.

---

## Thread-Safety Model

The store uses **two `ConcurrentHashMap` instances**:

```
ledger    : ConcurrentHashMap<userId, List<Transaction>>   ← the data
userLocks : ConcurrentHashMap<userId, Object>              ← per-user monitors
```

For every mutating operation (`earn` / `redeem`):

1. A monitor object is obtained for the target `userId` via `computeIfAbsent` (atomic).
2. All subsequent work — balance derivation, constraint checking, and list append — happens
   inside a `synchronized (lock)` block on that monitor.

This means:
- **Concurrent requests for different users** proceed in parallel with zero contention.
- **Concurrent requests for the same user** are serialized, preventing lost-update races
  (e.g. two simultaneous redeems on a low balance cannot both succeed).
- No global lock exists anywhere in the codebase.

---

## Sample cURL Flows

### Happy path — earn then redeem

```bash
# 1. Earn 500 points
curl -s -X POST http://localhost:8080/api/wallet/earn \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: demo-001" \
  -d '{"userId":"user-001","points":500,"description":"Welcome bonus"}' | jq .

# 2. Earn another 250 points
curl -s -X POST http://localhost:8080/api/wallet/earn \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: demo-002" \
  -d '{"userId":"user-001","points":250,"description":"Referral reward"}' | jq .

# 3. Check balance (should be 750)
curl -s http://localhost:8080/api/wallet/user-001 | jq .

# 4. Redeem 200 points
curl -s -X POST http://localhost:8080/api/wallet/redeem \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: demo-003" \
  -d '{"userId":"user-001","points":200,"description":"Order discount"}' | jq .

# 5. View full transaction history
curl -s http://localhost:8080/api/wallet/user-001/transactions | jq .
```

### Error paths

```bash
# Redeem from non-existent wallet → 404
curl -s -X POST http://localhost:8080/api/wallet/redeem \
  -H "Content-Type: application/json" \
  -d '{"userId":"ghost-user","points":100}' | jq .

# Redeem more than balance → 422
curl -s -X POST http://localhost:8080/api/wallet/redeem \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-001","points":99999}' | jq .

# Missing required field → 400
curl -s -X POST http://localhost:8080/api/wallet/earn \
  -H "Content-Type: application/json" \
  -d '{"points":-5}' | jq .
```

---

## Data Lifecycle Notice

All data lives **in-memory only**. Restarting the application clears all wallets and
transaction history. This is intentional for the POC phase — no persistence layer is wired.
