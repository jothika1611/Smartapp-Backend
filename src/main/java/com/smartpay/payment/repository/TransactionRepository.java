package com.smartpay.payment.repository;

import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findBySenderUserIdAndIdempotencyKey(Long senderUserId, String idempotencyKey);

    Optional<Transaction> findByReference(String reference);

    /** Locks the row so two ADMIN decisions cannot both release the same money. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.reference = :reference")
    Optional<Transaction> findByReferenceForUpdate(@Param("reference") String reference);

    // Fraud rule history queries. These are read-only and never expose entities outside the monolith.
    List<Transaction> findBySenderUserIdAndStatusAndCreatedAtAfter(
            Long senderUserId, TransactionStatus status, LocalDateTime from);

    long countBySenderUserIdAndCreatedAtAfter(Long senderUserId, LocalDateTime from);

    long countBySenderUserIdAndReceiverUserIdAndAmountAndCreatedAtAfter(
            Long senderUserId, Long receiverUserId, BigDecimal amount, LocalDateTime from);

    long countBySenderUserIdAndStatusAndCreatedAtAfter(
            Long senderUserId, TransactionStatus status, LocalDateTime from);

    boolean existsBySenderUserIdAndDeviceFingerprintAndCreatedAtAfter(
            Long senderUserId, String deviceFingerprint, LocalDateTime from);

    boolean existsBySenderUserIdAndLocationHintAndCreatedAtAfter(
            Long senderUserId, String locationHint, LocalDateTime from);
}
