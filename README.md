# Production-Ready Payment Gateway with Asynchronous Processing & Webhooks

[![Architecture](https://img.shields.io/badge/Architecture-Event--Driven-blue.svg)](#architecture)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](#license)
[![Author](https://img.shields.io/badge/Author-KALARI%20SRISUCHA-purple.svg)](https://github.com/sucha6174)

A robust, enterprise-grade payment gateway backend and checkout solution engineered with asynchronous job queues, event-driven webhooks, HMAC-SHA256 signature verification, idempotent payment execution, automated retry scheduling with exponential backoff, and an embeddable client SDK.

---

## Author & Repository Details

- **Developer**: KALARI SRISUCHA
- **GitHub Username**: [sucha6174](https://github.com/sucha6174)
- **Repository**: [https://github.com/sucha6174/payment-gateway1](https://github.com/sucha6174/payment-gateway1)

---

## 🎥 5-Minute Pitch Video

[Watch the 5-Minute Project Demo](VIDEO_LINK_TO_BE_ADDED)

---

## System Architecture

The payment gateway decouples synchronous client requests from heavy transaction processing and external webhooks using a Redis-backed queue system and autonomous worker services.

```
                  +----------------------------------------------+
                  |           Merchant Web Application           |
                  +----------------------------------------------+
                         |                              ^
          1. Create Order|                8. Webhook    | (HMAC-SHA256 Verified)
          & Client Token |                   Dispatch   |
                         v                              |
            +-------------------------+                 |
            |   Gateway API (8000)    |                 |
            |   (Spring Boot Core)    |                 |
            +-------------------------+                 |
              |            |            |               |
     Check    |       Save |    Enqueue |               |
  Idempotency |    Pending |        Job |               |
              v            v            v               |
        +----------+ +-----------+ +---------------+    |
        | Postgres | | Postgres  | | Redis Queues  |    |
        |  Cache   | | Payments  | |  (Bull/Redis) |    |
        +----------+ +-----------+ +---------------+    |
                                        |               |
                                Dequeue |               |
                                        v               |
                           +------------------------+   |
                           | Gateway Worker Service |---+
                           |  - Payment Worker      |
                           |  - Webhook Worker      |
                           |  - Refund Worker       |
                           |  - Retry Scheduler     |
                           +------------------------+
```

### Core Architecture Components

1. **API Service (`gateway_api`)**: Stateless HTTP REST API handling authentication, idempotency verification, payment initiation, capture, refund balance validations, and webhook metadata.
2. **Worker Service (`gateway_worker`)**: Dedicated multi-threaded background processor executing transactions, status transitions, signature generation, HTTP dispatches, and scheduled retries.
3. **Message Broker (`redis_gateway`)**: High-throughput in-memory Redis message broker managing `queue:payments`, `queue:webhooks`, and `queue:refunds`.
4. **Relational Database (`postgres_gateway`)**: PostgreSQL store with strict foreign keys, transactional integrity, and optimized composite query indexes.
5. **Merchant Dashboard (`gateway_dashboard`)**: Webhook configuration console, secret regenerator, delivery log viewer, and manual retry triggers.
6. **Checkout & SDK Service (`gateway_checkout`)**: Embeddable JavaScript SDK (`checkout.js`) and cross-origin checkout modal communicating via `postMessage`.

---

## Key Features & Production Patterns

### 1. Asynchronous Payment Processing & Deterministic Test Mode
- Payments are created in a `pending` state immediately and enqueued onto Redis.
- Background worker simulates acquiring bank network latency and resolves final state (`success` or `failed`).
- **Deterministic Test Mode**: Supports automated testing environments via environment variables:
  - `TEST_MODE=true`: Enables deterministic processing.
  - `TEST_PROCESSING_DELAY=1000`: Explicit processing delay in milliseconds (default: `1000ms`).
  - `TEST_PAYMENT_SUCCESS=true`: Forces payment outcome (`true` for success, `false` for failure).

### 2. Event-Driven Webhooks with HMAC-SHA256 Signatures
- Dispatches webhook events for `payment.created`, `payment.pending`, `payment.success`, `payment.failed`, `refund.created`, and `refund.processed`.
- Webhook payloads are signed using **HMAC-SHA256** with the merchant's private `webhook_secret`.
- Signatures are transmitted in the `X-Webhook-Signature` header using the exact raw JSON request body bytes.

### 3. Resilient Exponential Backoff Retry System
Failed webhook deliveries undergo up to **5 attempts** with persistent scheduling recorded in the `webhook_logs` table via `next_retry_at`:
- **Production Schedule**:
  - Attempt 1: Immediate (0s delay)
  - Attempt 2: After 1 minute (60s)
  - Attempt 3: After 5 minutes (300s)
  - Attempt 4: After 30 minutes (1800s)
  - Attempt 5: After 2 hours (7200s)
- **Test Mode Schedule (`WEBHOOK_RETRY_INTERVALS_TEST=true`)**:
  - Attempt 1: 0 seconds (immediate)
  - Attempt 2: 5 seconds
  - Attempt 3: 10 seconds
  - Attempt 4: 15 seconds
  - Attempt 5: 20 seconds
- Webhooks failing after 5 attempts are marked as permanently `failed`. Merchants can trigger a manual retry from the dashboard, which resets attempts to 0 and re-enqueues delivery.

### 4. Idempotency Key Handling
- Supports the `Idempotency-Key` HTTP header on payment creation requests.
- Scoped to `(merchant_id, idempotency_key)`.
- Replaying the identical key within the **24-hour expiration window** returns the exact cached HTTP response without re-executing business logic or charging twice.

### 5. Asynchronous Full & Partial Refunds
- Refunds require payments to be in `success` status.
- Validates cumulative refund amount: $\text{Requested Amount} \le \text{Payment Amount} - \sum(\text{Processed} + \text{Pending Refunds})$.
- Excess refund requests are rejected with `400 BAD_REQUEST_ERROR`.
- Background worker processes refunds asynchronously and emits `refund.processed`.

### 6. Embeddable JavaScript SDK
- Universal JavaScript widget bundled to a single `checkout.js` file.
- Provides `open()` and `close()` methods.
- Supports `key`, `orderId`, `onSuccess`, `onFailure`, and `onClose` callbacks.
- Communicates securely across iframe boundaries via `window.postMessage`.

---

## API Specification

### Authentication
All merchant API endpoints require credentials passed in HTTP headers:
- `X-Api-Key`: Merchant public API Key (`key_test_abc123`)
- `X-Api-Secret`: Merchant private API Secret (`secret_test_xyz789`)

---

### 1. Create Order
`POST /api/v1/orders`

```bash
curl -X POST http://localhost:8000/api/v1/orders \
  -H "X-Api-Key: key_test_abc123" \
  -H "X-Api-Secret: secret_test_xyz789" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50000,
    "currency": "INR",
    "receipt": "rcpt_1001"
  }'
```

**Response (201 Created):**
```json
{
  "id": "order_aB1c2D3e4F5g6H7i",
  "merchant_id": "a0000000-0000-0000-0000-000000000001",
  "amount": 50000,
  "currency": "INR",
  "receipt": "rcpt_1001",
  "status": "created",
  "created_at": "2026-09-03T06:00:00Z"
}
```

---

### 2. Create Payment (Asynchronous & Idempotent)
`POST /api/v1/payments`

```bash
curl -X POST http://localhost:8000/api/v1/payments \
  -H "X-Api-Key: key_test_abc123" \
  -H "X-Api-Secret: secret_test_xyz789" \
  -H "Idempotency-Key: req_unique_id_99" \
  -H "Content-Type: application/json" \
  -d '{
    "order_id": "order_aB1c2D3e4F5g6H7i",
    "method": "upi",
    "vpa": "customer@upi"
  }'
```

**Response (201 Created):**
```json
{
  "id": "pay_J7k8L9m0N1o2P3q4",
  "order_id": "order_aB1c2D3e4F5g6H7i",
  "amount": 50000,
  "currency": "INR",
  "method": "upi",
  "vpa": "customer@upi",
  "status": "pending",
  "captured": false,
  "created_at": "2026-09-03T06:01:00Z"
}
```

---

### 3. Capture Payment
`POST /api/v1/payments/{payment_id}/capture`

```bash
curl -X POST http://localhost:8000/api/v1/payments/pay_J7k8L9m0N1o2P3q4/capture \
  -H "X-Api-Key: key_test_abc123" \
  -H "X-Api-Secret: secret_test_xyz789" \
  -H "Content-Type: application/json" \
  -d '{ "amount": 50000 }'
```

**Response (200 OK):**
```json
{
  "id": "pay_J7k8L9m0N1o2P3q4",
  "order_id": "order_aB1c2D3e4F5g6H7i",
  "amount": 50000,
  "currency": "INR",
  "method": "upi",
  "status": "success",
  "captured": true,
  "created_at": "2026-09-03T06:01:00Z",
  "updated_at": "2026-09-03T06:01:02Z"
}
```

---

### 4. Create Refund
`POST /api/v1/payments/{payment_id}/refunds`

```bash
curl -X POST http://localhost:8000/api/v1/payments/pay_J7k8L9m0N1o2P3q4/refunds \
  -H "X-Api-Key: key_test_abc123" \
  -H "X-Api-Secret: secret_test_xyz789" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 25000,
    "reason": "Customer requested partial refund"
  }'
```

**Response (201 Created):**
```json
{
  "id": "rfnd_R1s2T3u4V5w6X7y8",
  "payment_id": "pay_J7k8L9m0N1o2P3q4",
  "amount": 25000,
  "reason": "Customer requested partial refund",
  "status": "pending",
  "created_at": "2026-09-03T06:02:00Z"
}
```

---

### 5. Get Refund
`GET /api/v1/refunds/{refund_id}`

```bash
curl -X GET http://localhost:8000/api/v1/refunds/rfnd_R1s2T3u4V5w6X7y8 \
  -H "X-Api-Key: key_test_abc123" \
  -H "X-Api-Secret: secret_test_xyz789"
```

---

### 6. List Webhook Logs
`GET /api/v1/webhooks?limit=10&offset=0`

```bash
curl -X GET "http://localhost:8000/api/v1/webhooks?limit=10&offset=0" \
  -H "X-Api-Key: key_test_abc123" \
  -H "X-Api-Secret: secret_test_xyz789"
```

---

### 7. Retry Webhook Delivery
`POST /api/v1/webhooks/{webhook_id}/retry`

```bash
curl -X POST http://localhost:8000/api/v1/webhooks/550e8400-e29b-41d4-a716-446655440000/retry \
  -H "X-Api-Key: key_test_abc123" \
  -H "X-Api-Secret: secret_test_xyz789"
```

---

### 8. Job Queue Status (Test & Evaluation Monitoring)
`GET /api/v1/test/jobs/status` (Unauthenticated)

```bash
curl -X GET http://localhost:8000/api/v1/test/jobs/status
```

**Response (200 OK):**
```json
{
  "pending": 0,
  "processing": 0,
  "completed": 12,
  "failed": 0,
  "worker_status": "running"
}
```

---

## Webhook Verification Guide

Merchants should verify the `X-Webhook-Signature` header on every incoming POST webhook using HMAC-SHA256:

### Node.js Verification Example
```javascript
const crypto = require('crypto');

function verifyWebhook(rawPayloadString, signatureHeader, secret) {
  const expectedSignature = crypto
    .createHmac('sha256', secret)
    .update(rawPayloadString)
    .digest('hex');

  return expectedSignature.toLowerCase() === signatureHeader.toLowerCase();
}
```

### Python Verification Example
```python
import hmac
import hashlib

def verify_webhook(raw_payload_bytes, signature_header, secret):
    expected_sig = hmac.new(
        secret.encode('utf-8'),
        raw_payload_bytes,
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected_sig.lower(), signature_header.lower())
```

---

## Embeddable JavaScript SDK Integration

Include the bundled script in your HTML page:

```html
<script src="http://localhost:3001/checkout.js"></script>

<button id="pay-button">Pay Now</button>

<script>
document.getElementById('pay-button').addEventListener('click', function() {
  const checkout = new PaymentGateway({
    key: 'key_test_abc123',
    orderId: 'order_aB1c2D3e4F5g6H7i',
    onSuccess: function(response) {
      console.log('Payment successful! ID:', response.paymentId);
    },
    onFailure: function(error) {
      console.error('Payment failed:', error);
    },
    onClose: function() {
      console.log('Payment modal was closed by the user or programmatically.');
    }
  });

  // Open checkout modal
  checkout.open();

  // Programmatically close modal whenever needed:
  // checkout.close();
});
</script>
```

### SDK Constructor Options & Methods

| Parameter | Type | Description |
|---|---|---|
| `key` | `string` | **Required**. Merchant API Key (`key_test_...`) |
| `orderId` | `string` | **Required**. Order ID created via backend |
| `onSuccess` | `function` | Callback invoked upon successful transaction |
| `onFailure` | `function` | Callback invoked upon transaction failure |
| `onClose` | `function` | Callback invoked when modal is dismissed |
| `open()` | `method` | Renders and presents modal overlay and checkout iframe |
| `close()` | `method` | Dismisses modal, tears down listeners, and calls `onClose` |

---

## Service Port Map

| Service | Container Name | Port | Description |
|---|---|---|---|
| API Service | `gateway_api` | `8000` | REST API endpoints |
| Dashboard | `gateway_dashboard` | `3000` | Webhook management & docs |
| Checkout / SDK | `gateway_checkout` | `3001` | Checkout UI & `checkout.js` SDK |
| Redis | `redis_gateway` | `6379` | Background message queue |
| PostgreSQL | `postgres_gateway` | `5432` | Relational database |

---

## Local Setup & Execution

### 1. Start Services via Docker Compose
```bash
docker compose up -d --build
```

### 2. Verify Service Health
```bash
docker compose ps
curl http://localhost:8000/api/v1/test/jobs/status
```

### 3. Run Automated End-to-End Test Suite
```bash
python tests/e2e_verification.py
```

### 4. Stop Services
```bash
docker compose down -v
```

---

## License
MIT License. Developed independently by KALARI SRISUCHA.
