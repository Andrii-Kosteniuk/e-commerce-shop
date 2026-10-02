package com.ecommerce.kafka.producers;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String id, Object event) {

        log.info("Publishing {} event for ID {}", event.getClass().getSimpleName(), id);

        try {
            kafkaTemplate.send(topic, id, event).get(5, TimeUnit.SECONDS);
            log.info("Published {} for ID={}", event.getClass().getSimpleName(), id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaException("Interrupted while publishing to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new KafkaException("Failed to publish " + event.getClass().getSimpleName()
                    + " for ID=" + id + " to " + topic, e);
        }
    }
}