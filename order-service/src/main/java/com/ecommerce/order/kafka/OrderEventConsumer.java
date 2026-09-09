package com.ecommerce.order.kafka;

import com.ecommerce.commonexception.exception.ResourceNotFoundException;
import com.ecommerce.kafka.utils.KafkaTopics;
import com.ecommerce.commondto.kafka.PaymentFailedEvent;
import com.ecommerce.commondto.kafka.PaymentSucceededEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderModifiedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderModifiedService orderModifiedService;
    private final OrderRepository orderRepository;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_SUCCEEDED,
            groupId = "order-group"
    )
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Received PaymentSucceededEvent for order: {}", event.orderId());

        if (orderRepository.existsByIdAndStatus(event.orderId(), OrderStatus.PAID)) {
            log.warn("Order {} already processed ", event.orderId());
            return;
        }

        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Order with id '%d' not found", event.orderId())));

        orderModifiedService.updateOrderStatus(order, OrderStatus.PAID);
        order.setOrderUpdateDate(LocalDateTime.now(ZoneId.systemDefault()));
        orderRepository.save(order);

        log.info("Order {} marked as paid", event.orderId());
    }


        @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "order-group"
    )
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Payment failed for orderId: {}, reason: {}", event.orderId(), event.reason());
        orderModifiedService.cancelOrder(event.orderId());
    }

}
