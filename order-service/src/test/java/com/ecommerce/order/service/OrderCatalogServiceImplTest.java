package com.ecommerce.order.service;

import com.ecommerce.commondto.order.OrderResponse;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCatalogServiceImplTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderMapper orderMapper;

    @InjectMocks
    OrderCatalogServiceImpl orderCatalogService;

    List<Order> orders;

    @BeforeEach
     void setUp() {

        List<OrderItem> orderItems = List.of(
                new OrderItem(1L, 1L, "Jeans pant", BigDecimal.TEN, "MENS_CLOTHING", 2, null),
                new OrderItem(2L, 1L, "Jeans pant", BigDecimal.TEN, "MENS_CLOTHING", 2, null)
        );
        orders = List.of(
                new Order(1L, 1L, orderItems, BigDecimal.valueOf(12L), OrderStatus.NEW, LocalDateTime.now(), null)
        );
    }

    @Test
    void shouldReturnAllOrders() {
        // ARRANGE
        when(orderRepository.findAllWithItems()).thenReturn(orders);

        // ACT
        List<OrderResponse> allOrders = orderCatalogService.getAllOrders();

        // ASSERT
        assertEquals(allOrders.size(), orders.size());
        verify(orderRepository).findAllWithItems();
    }

    @Test
    void shouldReturnOrderById() {
        // ARRANGE
        when(orderRepository.findById(1L)).thenReturn(Optional.of(orders.getFirst()));

        // ACT
        Optional<Order> orderById = orderCatalogService.getOrderById(1L);

        // ASSERT
        assertTrue(orderById.isPresent());
        assertEquals(orders.getFirst(), orderById.get());
        verify(orderRepository).findById(1L);

    }
}