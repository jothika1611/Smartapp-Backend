package com.smartpay.fraud.repository;

import com.smartpay.fraud.entity.FraudRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FraudRuleRepository extends JpaRepository<FraudRule, Long> {

    Optional<FraudRule> findByCode(String code);

    boolean existsByCode(String code);

    List<FraudRule> findAllByEnabledTrue();
}
