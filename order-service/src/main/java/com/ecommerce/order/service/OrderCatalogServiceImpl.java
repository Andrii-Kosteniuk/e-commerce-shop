package com.ecommerce.order.service;

import com.ecommerce.commondto.order.OrderResponse;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCatalogServiceImpl implements OrderCatalogService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<OrderResponse> getAllOrders() {

        return orderRepository.findAllWithItems().stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }

    @Override
    public Optional<Order> getOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }
}
