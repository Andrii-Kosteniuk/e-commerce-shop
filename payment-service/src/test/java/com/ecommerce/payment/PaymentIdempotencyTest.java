package com.ecommerce.payment;

import com.ecommerce.commondto.kafka.PaymentCreateEvent;
import com.ecommerce.kafka.producers.PaymentEventPublisher;
import com.ecommerce.payment.kafka.PaymentEventConsumer;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentService;
import com.ecommerce.payment.service.impl.PaymentServiceImpl;
import com.ecommerce.payment.stripe.StripeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = "spring.cloud.config.enabled=false")
@Import(PaymentServiceImpl.class)
class PaymentIdempotencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentEventPublisher paymentEventPublisher;

    @MockitoBean
    private StripeGateway stripeGateway;

    @MockitoBean
    private PaymentMapper paymentMapper;


    @Test
    void sameEventDeliveredTwice_createsExactlyOnePayment() {
        PaymentEventConsumer consumer = new PaymentEventConsumer(paymentService);
        PaymentCreateEvent event = new PaymentCreateEvent(42L, 7L, new BigDecimal("99.90"));

        consumer.handleOrderConfirmed(event);
        consumer.handleOrderConfirmed(event);

        assertEquals(1, paymentRepository.count());
        Payment saved = paymentRepository.findByOrderId(42L).orElseThrow();
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
        assertEquals(PaymentEventConsumer.idempotencyKeyFor(event), saved.getIdempotencyKey());
    }

    @Test
    void differentOrders_getDifferentKeysAndSeparatePayments() {
        PaymentEventConsumer consumer = new PaymentEventConsumer(paymentService);

        consumer.handleOrderConfirmed(new PaymentCreateEvent(1L, 7L, BigDecimal.TEN));
        consumer.handleOrderConfirmed(new PaymentCreateEvent(2L, 7L, BigDecimal.TEN));

        assertEquals(2, paymentRepository.count());
    }

    @Test
    void database_rejectsSecondPaymentForSameOrder() {
        paymentRepository.saveAndFlush(payment(5L, "key-a"));

        assertThrows(DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(payment(5L, "key-b")));
    }

    @Test
    void database_rejectsDuplicateIdempotencyKey() {
        paymentRepository.saveAndFlush(payment(5L, "same-key"));

        assertThrows(DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(payment(6L, "same-key")));
    }

    private static Payment payment(Long orderId, String key) {
        return Payment.builder()
                .orderId(orderId)
                .userId(7L)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .idempotencyKey(key)
                .build();
    }
}
