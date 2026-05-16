package com.rexmfbank.niptransfer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rexmfbank.niptransfer.model.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbound transfer response — wraps transaction state
 * for API consumers. Mirrors NIBSS NIP response structure.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferResponse {

    private UUID transactionId;
    private String idempotencyKey;
    private String sessionId;
    private TransactionStatus status;
    private String responseCode;
    private String responseMessage;
    private BigDecimal amount;
    private String currency;
    private String senderAccountNumber;
    private String senderBankCode;
    private String beneficiaryAccountNumber;
    private String beneficiaryBankCode;
    private String beneficiaryName;
    private String narration;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
