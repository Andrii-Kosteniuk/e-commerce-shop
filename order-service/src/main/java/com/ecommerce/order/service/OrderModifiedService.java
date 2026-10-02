package com.ecommerce.order.service;

import com.ecommerce.commondto.order.OrderCreateRequest;
import com.ecommerce.commondto.order.OrderResponse;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;

public interface OrderModifiedService {

    OrderResponse createOrder(OrderCreateRequest request, Long id);
    OrderResponse confirmOrder(Long orderId, Long userId);
    void updateOrderStatus(Order order, OrderStatus newStatus);
    void cancelOrder(Long orderId, String reason, boolean releaseStock);
}
