package com.smartpay.event.repository;

import com.smartpay.event.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            String status, LocalDateTime now, Pageable pageable);

    long countByStatus(String status);
}
