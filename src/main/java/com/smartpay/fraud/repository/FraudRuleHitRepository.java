package com.smartpay.fraud.repository;

import com.smartpay.fraud.entity.FraudRuleHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudRuleHitRepository extends JpaRepository<FraudRuleHit, Long> {

    List<FraudRuleHit> findByTransactionIdOrderByIdAsc(Long transactionId);
}
