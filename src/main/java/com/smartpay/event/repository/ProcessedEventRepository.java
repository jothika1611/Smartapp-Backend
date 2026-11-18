package com.smartpay.event.repository;

import com.smartpay.event.entity.ProcessedEvent;
import com.smartpay.event.entity.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
