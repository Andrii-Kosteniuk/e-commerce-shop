package com.ecommerce.order.service;

import com.ecommerce.commondto.kafka.OrderCanceledEvent;
import com.ecommerce.commondto.kafka.OrderCreatedEvent;
import com.ecommerce.commondto.kafka.PaymentCreateEvent;
import com.ecommerce.commondto.order.OrderCreateRequest;
import com.ecommerce.commondto.order.OrderResponse;
import com.ecommerce.commondto.order.StockItem;
import com.ecommerce.commondto.product.ProductResponse;
import com.ecommerce.commondto.user.UserResponse;
import com.ecommerce.commonexception.exception.ResourceNotFoundException;
import com.ecommerce.kafka.producers.KafkaEventPublisher;
import com.ecommerce.kafka.utils.KafkaTopics;
import com.ecommerce.order.feign.ProductFeignClient;
import com.ecommerce.order.feign.UserFeignClient;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderModifiedServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserFeignClient userClient;
    @Mock
    private ProductFeignClient productClient;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private KafkaEventPublisher kafkaEventPublisher;
    @Mock
    private OrderCatalogService orderCatalogService;
    @InjectMocks
    private OrderModifiedServiceImpl orderService;
    private UserResponse user;
    private ProductResponse product;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        user = new UserResponse(1L, "Andrii", "Kosteniuk", "andrii@gmail.com", "USER");

        product = new ProductResponse(
                100L,
                "T-Shirt",
                BigDecimal.valueOf(1000),
                10,
                "MENS_CLOTHING",
                null,
                true);

        orderResponse = mock(OrderResponse.class);
    }

    @Test
    void createOrder_shouldCreateOrderAndPublishEvent() {
        // Given
        Long userId = 1L;

        StockItem requestedProduct = new StockItem(100L, 2);

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(requestedProduct)
        );

        when(userClient.getUserById(userId))
                .thenReturn(user);

        when(productClient.getProductById(100L))
                .thenReturn(product);

        when(orderMapper.toOrderResponse(any(Order.class)))
                .thenReturn(orderResponse);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setId(50L);
                    return order;
                });

        // When
        OrderResponse result = orderService.createOrder(request, userId);

        // Then
        assertSame(orderResponse, result);

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertEquals(50L, savedOrder.getId());
        assertEquals(user.id(), savedOrder.getUserId());
        assertEquals(BigDecimal.valueOf(2000), savedOrder.getTotalPrice());
        assertEquals(OrderStatus.NEW, savedOrder.getStatus());

        assertEquals(1, savedOrder.getItems().size());

        OrderItem item = savedOrder.getItems().getFirst();

        assertEquals(100L, item.getProductId());
        assertEquals("T-Shirt", item.getProductName());
        assertEquals(BigDecimal.valueOf(1000), item.getPrice());
        assertEquals("MENS_CLOTHING", item.getCategory());
        assertEquals(2, item.getQuantity());

        assertSame(savedOrder, item.getOrder());

        verify(kafkaEventPublisher).publish(
                eq(KafkaTopics.ORDER_CREATED),
                eq("50"),
                any(OrderCreatedEvent.class)
        );

        verify(orderMapper, atLeastOnce())
                .toOrderResponse(savedOrder);
    }

    @Test
    void createOrder_shouldCalculateTotalForMultipleProducts() {
        // Given
        Long userId = 1L;

        ProductResponse secondProduct = new ProductResponse(
                200L,
                "T-Shirt",
                BigDecimal.valueOf(50),
                10,
                "MENS_CLOTHING",
                null,
                true
        );

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(
                        new StockItem(100L, 2),
                        new StockItem(200L, 3)
                )
        );

        when(userClient.getUserById(userId))
                .thenReturn(user);

        when(productClient.getProductById(100L))
                .thenReturn(product);

        when(productClient.getProductById(200L))
                .thenReturn(secondProduct);

        when(orderMapper.toOrderResponse(any(Order.class)))
                .thenReturn(orderResponse);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setId(1L);
                    return order;
                });

        // When
        orderService.createOrder(request, userId);

        // Then
        ArgumentCaptor<Order> captor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(captor.capture());

        Order order = captor.getValue();

        assertEquals(
                BigDecimal.valueOf(2150),
                order.getTotalPrice()
        );

        assertEquals(2, order.getItems().size());
    }

    @Test
    void createOrder_shouldThrowExceptionWhenStockIsInsufficient() {
        // Given
        Long userId = 1L;

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new StockItem(100L, 20))
        );

        when(userClient.getUserById(userId))
                .thenReturn(user);

        when(productClient.getProductById(100L))
                .thenReturn(product);

        // product has only 10 available

        // When
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request, userId)
        );

        // Then
        assertEquals(
                "Insufficient stock for 'T-Shirt'. Available: 10",
                exception.getMessage()
        );

        verify(orderRepository, never()).save(any());
        verify(kafkaEventPublisher, never())
                .publish(anyString(), anyString(), any());
        verify(orderMapper, never())
                .toOrderResponse(any(Order.class));
    }

    @Test
    void createOrder_shouldRetrieveEveryProductFromProductService() {
        // Given
        Long userId = 1L;

        ProductResponse secondProduct = new ProductResponse(
                200L,
                "T-Shirt",
                BigDecimal.valueOf(50),
                10,
                "MENS_CLOTHING",
                null,
                true
        );

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(
                        new StockItem(100L, 1),
                        new StockItem(200L, 2)
                )
        );

        when(userClient.getUserById(userId))
                .thenReturn(user);

        when(productClient.getProductById(100L))
                .thenReturn(product);

        when(productClient.getProductById(200L))
                .thenReturn(secondProduct);

        when(orderMapper.toOrderResponse(any(Order.class)))
                .thenReturn(orderResponse);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setId(1L);
                    return order;
                });

        // When
        orderService.createOrder(request, userId);

        // Then
        verify(productClient).getProductById(100L);
        verify(productClient).getProductById(200L);
    }

    @Test
    void updateOrderStatus_shouldChangeNewToConfirmed() {
        // Given
        Order order = createOrder(OrderStatus.NEW);

        // When
        orderService.updateOrderStatus(
                order,
                OrderStatus.CONFIRMED
        );

        // Then
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void updateOrderStatus_shouldChangeNewToCancelled() {
        // Given
        Order order = createOrder(OrderStatus.NEW);

        // When
        orderService.updateOrderStatus(
                order,
                OrderStatus.CANCELLED
        );

        // Then
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void updateOrderStatus_shouldChangeConfirmedToPaid() {
        // Given
        Order order = createOrder(OrderStatus.CONFIRMED);

        // When
        orderService.updateOrderStatus(
                order,
                OrderStatus.PAID
        );

        // Then
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void updateOrderStatus_shouldChangeConfirmedToCancelled() {
        // Given
        Order order = createOrder(OrderStatus.CONFIRMED);

        // When
        orderService.updateOrderStatus(
                order,
                OrderStatus.CANCELLED
        );

        // Then
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void updateOrderStatus_shouldNotChangeStatusWhenSameStatusProvided() {
        // Given
        Order order = createOrder(OrderStatus.NEW);

        // When
        orderService.updateOrderStatus(
                order,
                OrderStatus.NEW
        );

        // Then
        assertEquals(OrderStatus.NEW, order.getStatus());
    }

    @Test
    void updateOrderStatus_shouldThrowExceptionForInvalidTransition() {
        // Given
        Order order = createOrder(OrderStatus.NEW);

        // When
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(
                        order,
                        OrderStatus.PAID
                )
        );

        // Then
        assertEquals(
                "Cannot transition order status from NEW to PAID",
                exception.getMessage()
        );

        assertEquals(OrderStatus.NEW, order.getStatus());
    }

    @Test
    void updateOrderStatus_shouldThrowExceptionWhenTryingToChangePaidOrder() {
        // Given
        Order order = createOrder(OrderStatus.PAID);

        // When
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(
                        order,
                        OrderStatus.CANCELLED
                )
        );

        // Then
        assertEquals(
                "Cannot transition order status from PAID to CANCELLED",
                exception.getMessage()
        );
    }

    @Test
    void updateOrderStatus_shouldThrowExceptionWhenTryingToChangeCancelledOrder() {
        // Given
        Order order = createOrder(OrderStatus.CANCELLED);

        // When
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(
                        order,
                        OrderStatus.CONFIRMED
                )
        );

        // Then
        assertEquals(
                "Cannot transition order status from CANCELLED to CONFIRMED",
                exception.getMessage()
        );
    }

    @Test
    void cancelOrder_shouldThrowExceptionWhenOrderDoesNotExist() {
        // Given
        Long orderId = 10L;

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.empty());

        // When
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.cancelOrder(orderId, "Order with id '10' not found", false )
        );

        // Then
        assertEquals(
                "Order with id '10' not found",
                exception.getMessage()
        );

        verify(orderRepository, never()).save(any());

        verify(kafkaEventPublisher, never())
                .publish(anyString(), anyString(), any());
    }

    @Test
    void cancelOrder_whenStockWasNeverReserved_publishesEventWithNoItemsToRelease() {
        Long orderId = 11L;
        Order order = createOrder(OrderStatus.NEW);
        order.setId(orderId);
        order.setUserId(1L);
        OrderItem item = OrderItem.builder().productId(100L).quantity(2).price(BigDecimal.TEN).build();
        item.setOrder(order);
        order.setItems(List.of(item));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.cancelOrder(orderId, "Insufficient stock: product 100", false);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        ArgumentCaptor<OrderCanceledEvent> captor = ArgumentCaptor.forClass(OrderCanceledEvent.class);
        verify(kafkaEventPublisher).publish(eq(KafkaTopics.ORDER_CANCELED), eq("11"), captor.capture());
        assertTrue(captor.getValue().items().isEmpty());
        assertEquals("Insufficient stock: product 100", captor.getValue().reason());
    }

    @Test
    void cancelOrder_whenAlreadyCancelled_isIdempotentAndPublishesNothing() {
        Long orderId = 12L;
        Order order = createOrder(OrderStatus.CANCELLED);
        order.setId(orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.cancelOrder(orderId, "duplicate delivery", false);

        verify(orderRepository, never()).save(any());
        verify(kafkaEventPublisher, never()).publish(anyString(), anyString(), any());
    }


    @Test
    void confirmOrder_shouldThrowExceptionWhenOrderDoesNotExist() {
        // Given
        Long orderId = 10L;
        Long userId = 1L;

        when(orderCatalogService.getOrderById(orderId))
                .thenReturn(Optional.empty());

        // When
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.confirmOrder(orderId, userId)
        );

        // Then
        assertEquals(
                "Order with id '10' not found",
                exception.getMessage()
        );

        verify(orderRepository, never()).save(any());

        verify(kafkaEventPublisher, never())
                .publish(anyString(), anyString(), any());
    }

    @Test
    void confirmOrder_shouldThrowAccessDeniedExceptionForAnotherUser() {
        // Given
        Long orderId = 10L;
        Long ownerId = 1L;
        Long anotherUserId = 2L;

        Order order = createOrder(OrderStatus.NEW);
        order.setId(orderId);
        order.setUserId(ownerId);

        when(orderCatalogService.getOrderById(orderId))
                .thenReturn(Optional.of(order));

        // When
        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> orderService.confirmOrder(
                        orderId,
                        anotherUserId
                )
        );

        // Then
        assertEquals(
                "You are not allowed to confirm this order",
                exception.getMessage()
        );

        assertEquals(OrderStatus.NEW, order.getStatus());

        verify(orderRepository, never()).save(any());

        verify(kafkaEventPublisher, never())
                .publish(anyString(), anyString(), any());
    }

    @Test
    void confirmOrder_shouldConfirmOrderAndPublishPaymentEvent() {
        // Given
        Long orderId = 10L;
        Long userId = 1L;

        Order order = createOrder(OrderStatus.NEW);

        order.setId(orderId);
        order.setUserId(userId);
        order.setTotalPrice(BigDecimal.valueOf(250));

        when(orderCatalogService.getOrderById(orderId))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(order))
                .thenReturn(order);

        when(orderMapper.toOrderResponse(order))
                .thenReturn(orderResponse);

        // When
        OrderResponse result =
                orderService.confirmOrder(orderId, userId);

        // Then
        assertSame(orderResponse, result);

        assertEquals(
                OrderStatus.CONFIRMED,
                order.getStatus()
        );

        verify(orderRepository).save(order);

        ArgumentCaptor<PaymentCreateEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentCreateEvent.class);

        verify(kafkaEventPublisher).publish(
                eq(KafkaTopics.PAYMENT_CREATE),
                eq("10"),
                eventCaptor.capture()
        );

        PaymentCreateEvent event = eventCaptor.getValue();

        assertEquals(orderId, event.orderId());
        assertEquals(userId, event.userId());
        assertEquals(
                BigDecimal.valueOf(250),
                event.amount()
        );

        assertSame(orderResponse, result);
    }

    @Test
    void confirmOrder_shouldNotSaveOrderWhenTransitionIsInvalid() {
        // Given
        Long orderId = 10L;
        Long userId = 1L;

        Order order = createOrder(OrderStatus.PAID);
        order.setId(orderId);
        order.setUserId(userId);

        when(orderCatalogService.getOrderById(orderId))
                .thenReturn(Optional.of(order));

        // When
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.confirmOrder(orderId, userId)
        );

        // Then
        verify(orderRepository, never()).save(any());

        verify(kafkaEventPublisher, never())
                .publish(anyString(), anyString(), any());
    }

    private Order createOrder(OrderStatus status) {
        return Order.builder()
                .id(1L)
                .userId(1L)
                .totalPrice(BigDecimal.ZERO)
                .status(status)
                .build();
    }

}