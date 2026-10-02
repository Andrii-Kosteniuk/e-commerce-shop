package com.ecommerce.product.repository;

import com.ecommerce.product.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByHandlerAndEventKey(String handler, String eventKey);
}
