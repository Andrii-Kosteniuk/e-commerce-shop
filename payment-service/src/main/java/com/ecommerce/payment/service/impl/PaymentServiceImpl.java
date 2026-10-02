package com.ecommerce.payment.service.impl;

import com.ecommerce.commondto.kafka.PaymentFailedEvent;
import com.ecommerce.commondto.kafka.PaymentSucceededEvent;
import com.ecommerce.commondto.payment.PaymentRequest;
import com.ecommerce.commondto.payment.PaymentResponse;
import com.ecommerce.commonexception.exception.ResourceNotFoundException;
import com.ecommerce.commonexception.exception.StripePaymentException;
import com.ecommerce.kafka.producers.PaymentEventPublisher;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentService;
import com.ecommerce.payment.stripe.StripeGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final StripeGateway stripeGateway;
    private final PaymentMapper paymentMapper;


    @Override
    @Transactional
    public void createPayment(PaymentRequest request, Long userId) {

        if (paymentRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
            log.info("Duplicate payment request ignored, idempotencyKey={}", request.idempotencyKey());
            return;
        }

        Payment payment = Payment.builder()
                .userId(request.userId())
                .orderId(request.orderId())
                .amount(request.amount())
                .currency(request.currency())
                .idempotencyKey(request.idempotencyKey())
                .status(PaymentStatus.PENDING)
                .build();

        paymentRepository.saveAndFlush(payment);

        log.info("Payment created for orderId={}, userId={}", request.orderId(), request.userId());
    }

    @Override
    @Transactional
    public PaymentResponse confirmPayment(Long paymentId, Long userId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (!payment.getUserId().equals(userId)) {
            throw new AccessDeniedException("You have no permission to confirm this payment");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.error("Payment is not in PENDING status: The actual status is {}", payment.getStatus());
            throw new IllegalStateException("Payment is not in PENDING status: " + payment.getStatus());
        }

        try {
            String stripePaymentId = stripeGateway.processPayment();
            if (stripePaymentId.isBlank()) throw new StripePaymentException("Stripe payment id is empty");

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setStripePaymentId(stripePaymentId);

            paymentRepository.save(payment);

            log.info("Payment {} completed successfully", paymentId);

            paymentEventPublisher.publishPaymentSucceeded(
                    new PaymentSucceededEvent(
                            paymentId,
                            payment.getOrderId(),
                            payment.getUserId(),
                            stripePaymentId
                    )
            );
        } catch (StripePaymentException e) {
            payment.setStatus(PaymentStatus.FAILED);
            log.error("Payment {} failed. Reason: {}", paymentId, e.getMessage());

            paymentRepository.save(payment);

            paymentEventPublisher.publishPaymentFailed(
                    new PaymentFailedEvent(
                            paymentId,
                            payment.getOrderId(),
                            payment.getUserId(),
                            e.getMessage()
                    ));
        }

        return paymentMapper.toPaymentResponse(payment);
    }

    @Override
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order ID: " + orderId));

        return paymentMapper.toPaymentResponse(payment);
    }
}
