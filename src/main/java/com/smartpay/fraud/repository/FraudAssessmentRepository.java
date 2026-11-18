package com.smartpay.fraud.repository;

import com.smartpay.fraud.entity.FraudAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FraudAssessmentRepository extends JpaRepository<FraudAssessment, Long> {

    Optional<FraudAssessment> findByTransactionId(Long transactionId);

    @Query("select a from FraudAssessment a where a.transactionId in " +
            "(select t.id from Transaction t where t.reference = :reference)")
    Optional<FraudAssessment> findByTransactionReference(@Param("reference") String reference);
}
