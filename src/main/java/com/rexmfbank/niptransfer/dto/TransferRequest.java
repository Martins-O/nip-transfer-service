package com.rexmfbank.niptransfer.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Inbound transfer request — validates all required NIP fields
 * before the transaction is persisted or dispatched.
 */
@Data
public class TransferRequest {

    /**
     * Client-generated idempotency key.
     * Must be unique per transaction attempt.
     * Recommended: UUID v4 or timestamp + reference combo.
     */
    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 64, message = "Idempotency key must be between 8 and 64 characters")
    private String idempotencyKey;

    @NotBlank(message = "Sender account number is required")
    @Pattern(regexp = "^\\d{10}$", message = "Sender account number must be exactly 10 digits (NUBAN format)")
    private String senderAccountNumber;

    @NotBlank(message = "Sender bank code is required")
    @Pattern(regexp = "^\\d{3,6}$", message = "Sender bank code must be 3-6 digits")
    private String senderBankCode;

    @NotBlank(message = "Sender name is required")
    @Size(max = 100, message = "Sender name must not exceed 100 characters")
    private String senderName;

    @NotBlank(message = "Beneficiary account number is required")
    @Pattern(regexp = "^\\d{10}$", message = "Beneficiary account number must be exactly 10 digits (NUBAN format)")
    private String beneficiaryAccountNumber;

    @NotBlank(message = "Beneficiary bank code is required")
    @Pattern(regexp = "^\\d{3,6}$", message = "Beneficiary bank code must be 3-6 digits")
    private String beneficiaryBankCode;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum transfer amount is ₦1.00")
    @DecimalMax(value = "10000000.00", message = "Single transfer limit is ₦10,000,000 (CBN NIP single transaction limit)")
    @Digits(integer = 13, fraction = 2, message = "Amount must be a valid monetary value")
    private BigDecimal amount;

    @Size(max = 100, message = "Narration must not exceed 100 characters")
    private String narration;
}
