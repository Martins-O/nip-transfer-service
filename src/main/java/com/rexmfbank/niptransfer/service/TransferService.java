package com.rexmfbank.niptransfer.service;

import com.rexmfbank.niptransfer.dto.TransferRequest;
import com.rexmfbank.niptransfer.dto.TransferResponse;
import com.rexmfbank.niptransfer.exception.DuplicateTransactionException;
import com.rexmfbank.niptransfer.exception.TransactionNotFoundException;
import com.rexmfbank.niptransfer.model.Transaction;
import com.rexmfbank.niptransfer.model.TransactionStatus;
import com.rexmfbank.niptransfer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private static final String REX_INSTITUTION_CODE = "090175"; // Rex MFBank CBN institution code
    private static final DateTimeFormatter SESSION_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // NIBSS NIP response codes
    private static final String NIBSS_SUCCESS = "00";
    private static final String NIBSS_PENDING = "09";
    private static final String NIBSS_INSUFFICIENT_FUNDS = "51";
    private static final String NIBSS_INVALID_ACCOUNT = "25";
    private static final String NIBSS_SYSTEM_ERROR = "96";

    private final TransactionRepository transactionRepository;
    private final NipGatewayService nipGatewayService;

    /**
     * Initiates a NIP interbank transfer.
     *
     * Idempotency contract:
     * - If the idempotency key already exists and transaction is SUCCESSFUL/FAILED,
     *   return the existing result immediately (no re-processing).
     * - If PENDING/PROCESSING, return current state for client to poll.
     * - Only process if no existing record found.
     */
    @Transactional
    public TransferResponse initiateTransfer(TransferRequest request) {
        log.info("Initiating NIP transfer: idempotencyKey={}, amount={}, from={} to={}",
                request.getIdempotencyKey(), request.getAmount(),
                request.getSenderAccountNumber(), request.getBeneficiaryAccountNumber());

        // Idempotency check — critical for payment systems
        Optional<Transaction> existing = transactionRepository
                .findByIdempotencyKey(request.getIdempotencyKey());

        if (existing.isPresent()) {
            Transaction tx = existing.get();
            log.info("Duplicate request detected for idempotencyKey={}. Returning existing transaction: status={}",
                    request.getIdempotencyKey(), tx.getStatus());

            if (tx.getStatus() == TransactionStatus.PENDING ||
                tx.getStatus() == TransactionStatus.PROCESSING) {
                // Return current state — client should poll for completion
                return mapToResponse(tx);
            }

            // Already completed (SUCCESS/FAILED/REVERSED) — return cached result
            throw new DuplicateTransactionException(
                    "Transaction already processed with status: " + tx.getStatus(),
                    mapToResponse(tx));
        }

        // Build and persist the transaction record
        Transaction transaction = Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .sessionId(generateSessionId())
                .senderAccountNumber(request.getSenderAccountNumber())
                .senderBankCode(request.getSenderBankCode())
                .senderName(request.getSenderName())
                .beneficiaryAccountNumber(request.getBeneficiaryAccountNumber())
                .beneficiaryBankCode(request.getBeneficiaryBankCode())
                .amount(request.getAmount())
                .narration(request.getNarration())
                .status(TransactionStatus.PENDING)
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Transaction persisted: id={}, sessionId={}", transaction.getId(), transaction.getSessionId());

        // Dispatch to NIP gateway asynchronously
        processTransferAsync(transaction.getId());

        return mapToResponse(transaction);
    }

    /**
     * Queries current transaction status by ID.
     * Used for client polling after initiating a transfer.
     */
    @Transactional(readOnly = true)
    public TransferResponse getTransactionStatus(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + transactionId));

        return mapToResponse(transaction);
    }

    /**
     * Queries by session ID — useful for NIBSS reconciliation flows.
     */
    @Transactional(readOnly = true)
    public TransferResponse getBySessionId(String sessionId) {
        Transaction transaction = transactionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found for sessionId: " + sessionId));

        return mapToResponse(transaction);
    }

    /**
     * Paginated transaction history for an account number.
     */
    @Transactional(readOnly = true)
    public Page<TransferResponse> getTransactionsBySender(String accountNumber, Pageable pageable) {
        return transactionRepository
                .findBySenderAccountNumber(accountNumber, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Async NIP gateway processing — runs off the request thread
     * so the HTTP response returns immediately with PENDING status.
     *
     * In production this would be a message queue consumer (Kafka/RabbitMQ).
     */
    @Async
    @Transactional
    public void processTransferAsync(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found during async processing: " + transactionId));

        try {
            // Update to PROCESSING before hitting NIP gateway
            transaction.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            // Name enquiry — verify beneficiary account before sending funds
            String beneficiaryName = nipGatewayService.nameEnquiry(
                    transaction.getBeneficiaryAccountNumber(),
                    transaction.getBeneficiaryBankCode());
            transaction.setBeneficiaryName(beneficiaryName);

            // Dispatch to NIP gateway (NIBSS NIP 2.0 funds transfer)
            NipGatewayService.NipResponse nipResponse = nipGatewayService.sendFundsTransfer(transaction);

            // Map NIBSS response codes to internal status
            if (NIBSS_SUCCESS.equals(nipResponse.getResponseCode())) {
                transaction.setStatus(TransactionStatus.SUCCESSFUL);
                transaction.setCompletedAt(LocalDateTime.now());
                log.info("Transfer SUCCESSFUL: sessionId={}, amount={}",
                        transaction.getSessionId(), transaction.getAmount());
            } else if (NIBSS_PENDING.equals(nipResponse.getResponseCode())) {
                // NIBSS is still processing — leave as PENDING for retry scheduler
                transaction.setStatus(TransactionStatus.PENDING);
                log.warn("Transfer still PENDING at NIBSS: sessionId={}", transaction.getSessionId());
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setCompletedAt(LocalDateTime.now());
                log.error("Transfer FAILED: sessionId={}, responseCode={}, message={}",
                        transaction.getSessionId(),
                        nipResponse.getResponseCode(),
                        nipResponse.getResponseMessage());
            }

            transaction.setResponseCode(nipResponse.getResponseCode());
            transaction.setResponseMessage(nipResponse.getResponseMessage());

        } catch (Exception e) {
            log.error("Unexpected error processing transfer: transactionId={}", transactionId, e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setResponseCode(NIBSS_SYSTEM_ERROR);
            transaction.setResponseMessage("Internal processing error: " + e.getMessage());
            transaction.setCompletedAt(LocalDateTime.now());
        }

        transaction.setRetryCount(transaction.getRetryCount() + 1);
        transactionRepository.save(transaction);
    }

    /**
     * Generates a NIBSS-format session ID.
     * Format: YYYYMMDDHHMMSS + institutionCode(6) + sequence(6)
     */
    private String generateSessionId() {
        String timestamp = LocalDateTime.now().format(SESSION_ID_FORMAT);
        String sequence = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 999999));
        return timestamp + REX_INSTITUTION_CODE + sequence;
    }

    private TransferResponse mapToResponse(Transaction tx) {
        return TransferResponse.builder()
                .transactionId(tx.getId())
                .idempotencyKey(tx.getIdempotencyKey())
                .sessionId(tx.getSessionId())
                .status(tx.getStatus())
                .responseCode(tx.getResponseCode())
                .responseMessage(tx.getResponseMessage())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .senderAccountNumber(tx.getSenderAccountNumber())
                .senderBankCode(tx.getSenderBankCode())
                .beneficiaryAccountNumber(tx.getBeneficiaryAccountNumber())
                .beneficiaryBankCode(tx.getBeneficiaryBankCode())
                .beneficiaryName(tx.getBeneficiaryName())
                .narration(tx.getNarration())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
