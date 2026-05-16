-- ============================================================
-- NIP Transfer Service - Initial Schema
-- Migration: V1__create_transactions_table.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS transactions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key             VARCHAR(64)     NOT NULL UNIQUE,
    session_id                  VARCHAR(40)     UNIQUE,

    -- Sender
    sender_account_number       VARCHAR(10)     NOT NULL,
    sender_bank_code            VARCHAR(6)      NOT NULL,
    sender_name                 VARCHAR(100)    NOT NULL,

    -- Beneficiary
    beneficiary_account_number  VARCHAR(10)     NOT NULL,
    beneficiary_bank_code       VARCHAR(6)      NOT NULL,
    beneficiary_name            VARCHAR(100),

    -- Transaction Details
    amount                      NUMERIC(15, 2)  NOT NULL,
    currency                    VARCHAR(3)      NOT NULL DEFAULT 'NGN',
    narration                   VARCHAR(100),

    -- Status tracking
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    response_code               VARCHAR(10),
    response_message            VARCHAR(255),
    retry_count                 INTEGER         NOT NULL DEFAULT 0,

    -- Timestamps
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    completed_at                TIMESTAMP
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_transactions_status        ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_sender_acct   ON transactions(sender_account_number);
CREATE INDEX IF NOT EXISTS idx_transactions_beneficiary   ON transactions(beneficiary_account_number);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at    ON transactions(created_at DESC);

-- Comment for documentation
COMMENT ON TABLE transactions IS 'NIP interbank transfer transactions. Tracks full lifecycle from PENDING to SUCCESSFUL/FAILED/REVERSED.';
COMMENT ON COLUMN transactions.idempotency_key IS 'Client-supplied key for deduplication. Prevents double-processing on network retries.';
COMMENT ON COLUMN transactions.session_id IS 'NIBSS-assigned session ID. Format: YYYYMMDDHHMMSS + institutionCode(6) + sequence(6)';
COMMENT ON COLUMN transactions.status IS 'PENDING | PROCESSING | SUCCESSFUL | FAILED | REVERSED';
