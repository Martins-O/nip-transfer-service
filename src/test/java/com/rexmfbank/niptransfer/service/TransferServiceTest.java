package com.rexmfbank.niptransfer.service;

import com.rexmfbank.niptransfer.dto.TransferRequest;
import com.rexmfbank.niptransfer.dto.TransferResponse;
import com.rexmfbank.niptransfer.exception.DuplicateTransactionException;
import com.rexmfbank.niptransfer.exception.TransactionNotFoundException;
import com.rexmfbank.niptransfer.model.Transaction;
import com.rexmfbank.niptransfer.model.TransactionStatus;
import com.rexmfbank.niptransfer.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService Unit Tests")
class TransferServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private NipGatewayService nipGatewayService;

    @InjectMocks
    private TransferService transferService;

    private TransferRequest validRequest;
    private Transaction pendingTransaction;

    @BeforeEach
    void setUp() {
        validRequest = new TransferRequest();
        validRequest.setIdempotencyKey("test-idempotency-key-001");
        validRequest.setSenderAccountNumber("0123456789");
        validRequest.setSenderBankCode("090175");
        validRequest.setSenderName("MARTINS JOJOLOLA");
        validRequest.setBeneficiaryAccountNumber("9876543210");
        validRequest.setBeneficiaryBankCode("058");
        validRequest.setAmount(new BigDecimal("5000.00"));
        validRequest.setNarration("Test transfer");

        pendingTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("test-idempotency-key-001")
                .sessionId("20240101120000090175000001")
                .senderAccountNumber("0123456789")
                .senderBankCode("090175")
                .senderName("MARTINS JOJOLOLA")
                .beneficiaryAccountNumber("9876543210")
                .beneficiaryBankCode("058")
                .amount(new BigDecimal("5000.00"))
                .status(TransactionStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Should initiate new transfer and return PENDING status")
    void initiateTransfer_newRequest_returnsPending() {
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(pendingTransaction);

        TransferResponse response = transferService.initiateTransfer(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.getIdempotencyKey()).isEqualTo("test-idempotency-key-001");
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should return existing PENDING transaction on duplicate idempotency key")
    void initiateTransfer_duplicatePendingKey_returnsExistingTransaction() {
        when(transactionRepository.findByIdempotencyKey("test-idempotency-key-001"))
                .thenReturn(Optional.of(pendingTransaction));

        TransferResponse response = transferService.initiateTransfer(validRequest);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.getSessionId()).isEqualTo(pendingTransaction.getSessionId());
        // Should NOT save a new transaction
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DuplicateTransactionException for completed idempotency key")
    void initiateTransfer_duplicateCompletedKey_throwsDuplicateException() {
        Transaction completedTx = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("test-idempotency-key-001")
                .status(TransactionStatus.SUCCESSFUL)
                .amount(new BigDecimal("5000.00"))
                .responseCode("00")
                .build();

        when(transactionRepository.findByIdempotencyKey("test-idempotency-key-001"))
                .thenReturn(Optional.of(completedTx));

        assertThatThrownBy(() -> transferService.initiateTransfer(validRequest))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining("SUCCESSFUL");
    }

    @Test
    @DisplayName("Should return transaction status by ID")
    void getTransactionStatus_validId_returnsTransaction() {
        when(transactionRepository.findById(pendingTransaction.getId()))
                .thenReturn(Optional.of(pendingTransaction));

        TransferResponse response = transferService.getTransactionStatus(pendingTransaction.getId());

        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo(pendingTransaction.getId());
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Should throw TransactionNotFoundException for unknown ID")
    void getTransactionStatus_unknownId_throwsNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(transactionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.getTransactionStatus(unknownId))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("Should generate unique session IDs for consecutive transfers")
    void initiateTransfer_consecutiveRequests_generatesUniqueSessionIds() {
        TransferRequest request1 = buildRequest("key-001");
        TransferRequest request2 = buildRequest("key-002");

        Transaction tx1 = buildTransactionWithSessionId("20240101120000090175000001");
        Transaction tx2 = buildTransactionWithSessionId("20240101120000090175000002");

        when(transactionRepository.findByIdempotencyKey("key-001")).thenReturn(Optional.empty());
        when(transactionRepository.findByIdempotencyKey("key-002")).thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenReturn(tx1).thenReturn(tx2);

        TransferResponse r1 = transferService.initiateTransfer(request1);
        TransferResponse r2 = transferService.initiateTransfer(request2);

        assertThat(r1.getSessionId()).isNotEqualTo(r2.getSessionId());
    }

    // ---- Helpers ----

    private TransferRequest buildRequest(String idempotencyKey) {
        TransferRequest req = new TransferRequest();
        req.setIdempotencyKey(idempotencyKey);
        req.setSenderAccountNumber("0123456789");
        req.setSenderBankCode("090175");
        req.setSenderName("TEST SENDER");
        req.setBeneficiaryAccountNumber("9876543210");
        req.setBeneficiaryBankCode("058");
        req.setAmount(new BigDecimal("1000.00"));
        return req;
    }

    private Transaction buildTransactionWithSessionId(String sessionId) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .status(TransactionStatus.PENDING)
                .amount(new BigDecimal("1000.00"))
                .build();
    }
}
