package com.ecommerce.kafka.utils;

public class KafkaTopics {
    public static final String ORDER_CREATED        = "order.created";
    public static final String ORDER_CONFIRMED      = "order.confirmed";
    public static final String ORDER_CANCELED       = "order.canceled";
    public static final String PAYMENT_CREATE       = "payment.create";
    public static final String PAYMENT_SUCCEEDED    = "payment.succeeded";
    public static final String PAYMENT_FAILED       = "payment.failed";
    public static final String STOCK_RESERVED       = "stock.reserved";
    public static final String STOCK_RELEASED       = "stock.released";
    public static final String STOCK_RESERVATION_FAILED = "stock.reservation.failed";

    private KafkaTopics() {
    }
}
