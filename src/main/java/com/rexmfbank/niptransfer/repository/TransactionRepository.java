package com.rexmfbank.niptransfer.repository;

import com.rexmfbank.niptransfer.model.Transaction;
import com.rexmfbank.niptransfer.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Idempotency check — find existing transaction by client key.
     * Called before processing to prevent duplicate disbursements.
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findBySessionId(String sessionId);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    Page<Transaction> findBySenderAccountNumber(String accountNumber, Pageable pageable);

    Page<Transaction> findByBeneficiaryAccountNumber(String accountNumber, Pageable pageable);

    /**
     * Fetch pending transactions older than a threshold for retry processing.
     * Matches NIBSS recommendation to re-query status after 30s.
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING' " +
           "AND t.createdAt < :threshold AND t.retryCount < :maxRetries")
    Page<Transaction> findStaleTransactionsForRetry(
            @Param("threshold") LocalDateTime threshold,
            @Param("maxRetries") int maxRetries,
            Pageable pageable);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = :status " +
           "AND t.createdAt BETWEEN :from AND :to")
    long countByStatusAndDateRange(
            @Param("status") TransactionStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
