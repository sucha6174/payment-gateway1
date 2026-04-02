-- 1. Merchants Table (Users of your system)
CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    api_key VARCHAR(64) UNIQUE NOT NULL,
    api_secret VARCHAR(64) NOT NULL,
    webhook_url VARCHAR(255),
    webhook_secret VARCHAR(64), -- Added for HMAC security
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert a Test Merchant (REQUIRED by instructions)
INSERT INTO merchants (email, name, api_key, api_secret, webhook_secret)
VALUES ('test@example.com', 'Test Merchant', 'key_test_abc123', 'secret_test_xyz789', 'whsec_test_abc123')
ON CONFLICT (email) DO NOTHING;

-- 2. Payments Table (Transactions)
CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(64) PRIMARY KEY, -- "pay_..."
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    order_id VARCHAR(64) NOT NULL,
    amount INTEGER NOT NULL, -- in smallest unit (e.g., paise/cents)
    currency VARCHAR(3) NOT NULL,
    method VARCHAR(20) NOT NULL, -- 'upi', 'card'
    status VARCHAR(20) DEFAULT 'pending', -- 'pending', 'success', 'failed'
    captured BOOLEAN DEFAULT false, -- Added for settlement
    vpa VARCHAR(100), -- for UPI
    error_code VARCHAR(50),
    error_description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Refunds Table (New for this task)
CREATE TABLE IF NOT EXISTS refunds (
    id VARCHAR(64) PRIMARY KEY, -- "rfnd_..."
    payment_id VARCHAR(64) NOT NULL REFERENCES payments(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    amount INTEGER NOT NULL,
    reason TEXT,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- 4. Webhook Logs Table (New for this task)
CREATE TABLE IF NOT EXISTS webhook_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    event VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    attempts INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    response_code INTEGER,
    response_body TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Idempotency Keys (New for this task - prevents double charges)
CREATE TABLE IF NOT EXISTS idempotency_keys (
    key VARCHAR(255),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    response JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (key, merchant_id)
);