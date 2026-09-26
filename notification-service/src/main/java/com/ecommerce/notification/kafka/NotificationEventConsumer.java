package com.ecommerce.notification.kafka;

import com.ecommerce.commondto.kafka.OrderCanceledEvent;
import com.ecommerce.commondto.kafka.OrderCreatedEvent;
import com.ecommerce.commondto.kafka.PaymentFailedEvent;
import com.ecommerce.commondto.kafka.PaymentSucceededEvent;
import com.ecommerce.kafka.utils.KafkaTopics;
import com.ecommerce.notification.mail.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @Value("${notification.confirmation-base-url}")
    private String confirmationUrl;

    @Value("${notification.confirmation-order-url}")
    private String confirmOrderUrl;

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "notification-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Sending 'ORDER_CREATED' notification to userId: {}", event.userId());

        UUID id = UUID.randomUUID();

        String uri = UriComponentsBuilder
                .fromUriString(confirmationUrl + confirmOrderUrl + "{id}")
                .buildAndExpand(id)
                .toUriString();

        notificationService.notifyOrderCreated(
                event.userEmail(),
                event.orderId(),
                event.totalPrice().toString(),
                event.response().items(), uri

        );
    }


    @KafkaListener(topics = KafkaTopics.ORDER_CANCELED, groupId = "notification-group")
    public void handleOrderCanceled(OrderCanceledEvent event) {
        log.info("Sending 'ORDER_CANCELED' notification to userId: {}", event.userId());

        notificationService.notifyOrderCanceled(
                event.userId(),
                event.orderId()
        );

    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCEEDED, groupId = "notification-group")
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Sending 'PAYMENT_SUCCEEDED' notification to userId: {}", event.userId());

        notificationService.notifyPaymentSucceeded(
                event.userId(),
                event.orderId()
        );
    }


    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Sending 'PAYMENT_FAILED' notification to userId: {}", event.userId());

        notificationService.notifyPaymentFailed(
                event.userId(),
                event.orderId(),
                event.reason()
        );

    }

}