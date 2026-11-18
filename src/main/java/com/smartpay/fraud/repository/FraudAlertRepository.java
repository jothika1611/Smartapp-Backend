package com.smartpay.fraud.repository;

import com.smartpay.fraud.entity.FraudAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    List<FraudAlert> findByTransactionId(Long transactionId);

    Page<FraudAlert> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);
}
