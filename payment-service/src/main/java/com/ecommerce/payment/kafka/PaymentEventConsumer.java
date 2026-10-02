package com.ecommerce.payment.kafka;

import com.ecommerce.commondto.kafka.PaymentCreateEvent;
import com.ecommerce.commondto.payment.PaymentRequest;
import com.ecommerce.commonexception.exception.KafkaEventException;
import com.ecommerce.kafka.utils.KafkaTopics;
import com.ecommerce.payment.model.Currency;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {
    private final PaymentService paymentService;


    @KafkaListener(
            topics = KafkaTopics.PAYMENT_CREATE,
            groupId = "payment-group"
    )
    public void handleOrderConfirmed(PaymentCreateEvent event) {
        log.info("Receiving 'PAYMENT_CREATE' event for orderId: {} ...", event.orderId());
        try {
            paymentService.createPayment(
                    new PaymentRequest(
                            event.orderId(),
                            event.userId(),
                            event.amount(),
                            Currency.USD.name(),
                            idempotencyKeyFor(event)
                    ), event.userId()
            );

            log.info("Payment for order {} created", event.orderId());

        } catch (Exception e) {
            throw new KafkaEventException("Failed to confirm order", e);
        }
    }

    public static String idempotencyKeyFor(PaymentCreateEvent event) {
        return UUID.nameUUIDFromBytes(
                ("payment:" + event.orderId() + ":" + event.userId()).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

}
