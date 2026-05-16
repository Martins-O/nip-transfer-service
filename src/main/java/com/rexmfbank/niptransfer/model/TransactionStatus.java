package com.rexmfbank.niptransfer.model;

/**
 * NIP transaction lifecycle states.
 *
 * NIBSS NIP 2.0 response codes mapped to these states:
 * 00 -> SUCCESSFUL
 * 09 -> PENDING (transaction in progress at destination bank)
 * 25 -> FAILED (unable to locate record)
 * Any other -> FAILED
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    SUCCESSFUL,
    FAILED,
    REVERSED
}
