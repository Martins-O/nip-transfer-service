package com.rexmfbank.niptransfer.exception;

import com.rexmfbank.niptransfer.dto.TransferResponse;
import lombok.Getter;

@Getter
public class DuplicateTransactionException extends RuntimeException {
    private final TransferResponse existingTransaction;

    public DuplicateTransactionException(String message, TransferResponse existingTransaction) {
        super(message);
        this.existingTransaction = existingTransaction;
    }
}
