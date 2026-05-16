package com.rexmfbank.niptransfer.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a NIP interbank transfer transaction.
 * Models the NIBSS NIP 2.0 transaction lifecycle:
 * PENDING -> PROCESSING -> SUCCESSFUL | FAILED | REVERSED
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_session_id", columnList = "session_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Client-supplied idempotency key — prevents duplicate processing
     * on network retries. Must be unique per transaction attempt.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    /**
     * NIBSS-assigned session ID for tracking across the NIP network.
     * Format: YYYYMMDDHHMMSS + institutionCode + 6-digit sequence
     */
    @Column(name = "session_id", length = 40)
    private String sessionId;

    // ---- Sender Details ----
    @Column(name = "sender_account_number", nullable = false, length = 10)
    private String senderAccountNumber;

    @Column(name = "sender_bank_code", nullable = false, length = 6)
    private String senderBankCode;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    // ---- Beneficiary Details ----
    @Column(name = "beneficiary_account_number", nullable = false, length = 10)
    private String beneficiaryAccountNumber;

    @Column(name = "beneficiary_bank_code", nullable = false, length = 6)
    private String beneficiaryBankCode;

    @Column(name = "beneficiary_name", length = 100)
    private String beneficiaryName;

    // ---- Transaction Details ----
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "NGN";

    @Column(length = 100)
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "response_message", length = 255)
    private String responseMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
