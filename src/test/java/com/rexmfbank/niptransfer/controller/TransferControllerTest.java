package com.rexmfbank.niptransfer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rexmfbank.niptransfer.dto.TransferRequest;
import com.rexmfbank.niptransfer.dto.TransferResponse;
import com.rexmfbank.niptransfer.exception.DuplicateTransactionException;
import com.rexmfbank.niptransfer.exception.TransactionNotFoundException;
import com.rexmfbank.niptransfer.model.TransactionStatus;
import com.rexmfbank.niptransfer.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferController.class)
@DisplayName("TransferController Integration Tests")
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TEST_API_KEY = "test-api-key";

    @Test
    @DisplayName("POST /transfers - Should return 202 for valid transfer request")
    void initiateTransfer_validRequest_returns202() throws Exception {
        TransferRequest request = buildValidRequest();
        TransferResponse mockResponse = TransferResponse.builder()
                .transactionId(UUID.randomUUID())
                .idempotencyKey(request.getIdempotencyKey())
                .sessionId("20240101120000090175000001")
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency("NGN")
                .build();

        when(transferService.initiateTransfer(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/transfers")
                .header(API_KEY_HEADER, TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.currency").value("NGN"));
    }

    @Test
    @DisplayName("POST /transfers - Should return 400 for invalid account number format")
    void initiateTransfer_invalidAccountNumber_returns400() throws Exception {
        TransferRequest request = buildValidRequest();
        request.setSenderAccountNumber("12345"); // Not 10 digits

        mockMvc.perform(post("/api/v1/transfers")
                .header(API_KEY_HEADER, TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.senderAccountNumber").exists());
    }

    @Test
    @DisplayName("POST /transfers - Should return 400 when amount below minimum")
    void initiateTransfer_amountBelowMinimum_returns400() throws Exception {
        TransferRequest request = buildValidRequest();
        request.setAmount(new BigDecimal("0.50")); // Below ₦1 minimum

        mockMvc.perform(post("/api/v1/transfers")
                .header(API_KEY_HEADER, TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfers - Should return 409 for duplicate completed transaction")
    void initiateTransfer_duplicateKey_returns409() throws Exception {
        TransferRequest request = buildValidRequest();
        TransferResponse existing = TransferResponse.builder()
                .transactionId(UUID.randomUUID())
                .status(TransactionStatus.SUCCESSFUL)
                .responseCode("00")
                .build();

        when(transferService.initiateTransfer(any()))
                .thenThrow(new DuplicateTransactionException("Already processed", existing));

        mockMvc.perform(post("/api/v1/transfers")
                .header(API_KEY_HEADER, TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /transfers/{id} - Should return transaction for valid ID")
    void getTransactionStatus_validId_returns200() throws Exception {
        UUID txId = UUID.randomUUID();
        TransferResponse mockResponse = TransferResponse.builder()
                .transactionId(txId)
                .status(TransactionStatus.SUCCESSFUL)
                .responseCode("00")
                .amount(new BigDecimal("5000.00"))
                .currency("NGN")
                .build();

        when(transferService.getTransactionStatus(txId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/transfers/{id}", txId)
                .header(API_KEY_HEADER, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.data.responseCode").value("00"));
    }

    @Test
    @DisplayName("GET /transfers/{id} - Should return 404 for unknown ID")
    void getTransactionStatus_unknownId_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(transferService.getTransactionStatus(unknownId))
                .thenThrow(new TransactionNotFoundException("Not found: " + unknownId));

        mockMvc.perform(get("/api/v1/transfers/{id}", unknownId)
                .header(API_KEY_HEADER, TEST_API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Any endpoint - Should return 401 without API key")
    void anyEndpoint_missingApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---- Helper ----
    private TransferRequest buildValidRequest() {
        TransferRequest req = new TransferRequest();
        req.setIdempotencyKey("test-key-" + UUID.randomUUID());
        req.setSenderAccountNumber("0123456789");
        req.setSenderBankCode("090175");
        req.setSenderName("MARTINS JOJOLOLA");
        req.setBeneficiaryAccountNumber("9876543210");
        req.setBeneficiaryBankCode("058");
        req.setAmount(new BigDecimal("5000.00"));
        req.setNarration("Test NIP transfer");
        return req;
    }
}
