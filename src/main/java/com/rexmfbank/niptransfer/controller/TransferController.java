package com.rexmfbank.niptransfer.controller;

import com.rexmfbank.niptransfer.dto.ApiResponse;
import com.rexmfbank.niptransfer.dto.TransferRequest;
import com.rexmfbank.niptransfer.dto.TransferResponse;
import com.rexmfbank.niptransfer.exception.DuplicateTransactionException;
import com.rexmfbank.niptransfer.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * NIP Transfer REST Controller
 *
 * Endpoints:
 * POST   /api/v1/transfers              - Initiate new transfer
 * GET    /api/v1/transfers/{id}         - Query transfer status by ID
 * GET    /api/v1/transfers/session/{id} - Query by NIBSS session ID
 * GET    /api/v1/transfers/account/{no} - Transfer history for account
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

    private final TransferService transferService;

    /**
     * Initiates a NIP interbank transfer.
     *
     * Returns 202 ACCEPTED immediately with PENDING status.
     * Client should poll GET /transfers/{id} for final status.
     *
     * Returns 200 OK if idempotency key matches an in-flight transaction.
     * Returns 409 CONFLICT if idempotency key matches a completed transaction.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> initiateTransfer(
            @Valid @RequestBody TransferRequest request) {

        log.info("POST /api/v1/transfers: idempotencyKey={}", request.getIdempotencyKey());

        try {
            TransferResponse response = transferService.initiateTransfer(request);
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(response, "Transfer initiated. Poll status endpoint for updates."));

        } catch (DuplicateTransactionException ex) {
            // Return 409 with the existing transaction details
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<TransferResponse>builder()
                            .success(false)
                            .message("Duplicate transaction: " + ex.getMessage())
                            .data(ex.getExistingTransaction())
                            .build());
        }
    }

    /**
     * Polls transfer status by internal transaction ID.
     * Call this after initiating a transfer to get final result.
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransferResponse>> getTransactionStatus(
            @PathVariable UUID transactionId) {

        log.info("GET /api/v1/transfers/{}", transactionId);
        TransferResponse response = transferService.getTransactionStatus(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction retrieved."));
    }

    /**
     * Queries by NIBSS session ID — for reconciliation and dispute resolution.
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<TransferResponse>> getBySessionId(
            @PathVariable String sessionId) {

        log.info("GET /api/v1/transfers/session/{}", sessionId);
        TransferResponse response = transferService.getBySessionId(sessionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction retrieved."));
    }

    /**
     * Paginated transfer history for a sender account.
     */
    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<Page<TransferResponse>>> getAccountTransfers(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/transfers/account/{}", accountNumber);
        Page<TransferResponse> transfers = transferService
                .getTransactionsBySender(accountNumber, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(transfers, "Transactions retrieved."));
    }
}
