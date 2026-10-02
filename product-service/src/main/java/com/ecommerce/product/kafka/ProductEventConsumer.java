package com.ecommerce.product.kafka;

import com.ecommerce.commondto.kafka.OrderCanceledEvent;
import com.ecommerce.commondto.kafka.OrderCreatedEvent;
import com.ecommerce.commondto.kafka.StockReservationFailedEvent;
import com.ecommerce.commondto.order.StockItem;
import com.ecommerce.commonexception.exception.InsufficientStockException;
import com.ecommerce.kafka.producers.KafkaEventPublisher;
import com.ecommerce.kafka.utils.KafkaTopics;
import com.ecommerce.product.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final InventoryService inventoryService;
    private final KafkaEventPublisher kafkaEventPublisher;

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "product-group")
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {

        log.info("Reserving stock for orderId: {}", event.orderId());

        List<StockItem> items = event.response().items().stream()
                .map(item -> new StockItem(item.productId(), item.quantity()))
                .toList();

        try {

            inventoryService.reserveStock(event.orderId(), items);
        } catch (InsufficientStockException | ResourceNotFoundException e) {

            log.warn("Stock reservation failed for orderId: {} - {}", event.orderId(), e.getMessage());

            kafkaEventPublisher.publishAndAwait(
                    KafkaTopics.STOCK_RESERVATION_FAILED,
                    String.valueOf(event.orderId()),
                    new StockReservationFailedEvent(event.orderId(), event.userId(), e.getMessage()));
        }
    }


    @KafkaListener(topics = KafkaTopics.ORDER_CANCELED, groupId = "product-group")
    public void handleOrderCanceled(OrderCanceledEvent event) {

        log.info("Receiving {} event for orderId: {}", KafkaTopics.ORDER_CANCELED, event.orderId());
        inventoryService.releaseStock(event.orderId(), event.items());
    }

}