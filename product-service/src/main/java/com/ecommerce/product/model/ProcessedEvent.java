package com.ecommerce.product.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "processed_events", uniqueConstraints =
@UniqueConstraint(name = "uk_processed_event", columnNames = {"handler", "event_key"}))
@Getter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String handler;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt = Instant.now();

    public ProcessedEvent(String handler, String eventKey) {
        this.handler = handler;
        this.eventKey = eventKey;
    }
}