package com.ecommerce.commondto.kafka;

public record StockReservationFailedEvent(Long orderId, Long userId, String reason) {
}
