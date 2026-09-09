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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderModifiedServiceImpl implements OrderModifiedService {

    private final OrderRepository orderRepository;
    private final UserFeignClient userClient;
    private final ProductFeignClient productClient;
    private final OrderMapper orderMapper;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final OrderCatalogService orderCatalogService;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request, Long id) {

        UserResponse user = userClient.getUserById(id);

        List<OrderItem> items = processBuildingOrderItemsFromRequest(request);

        BigDecimal total = getTotalPrice(items);

        Order order = Order.builder()
                .userId(user.id())
                .items(items)
                .totalPrice(total)
                .status(OrderStatus.NEW)
                .orderCreateDate(LocalDateTime.now(ZoneId.systemDefault()))
                .build();

        items.forEach(item -> item.setOrder(order));
        orderRepository.save(order);

        kafkaEventPublisher.publish(
                KafkaTopics.ORDER_CREATED,
                order.getId().toString(),
                new OrderCreatedEvent(
                        order.getId(),
                        user.id(),
                        user.email(),
                        order.getTotalPrice(),
                        order.getStatus().name(),
                        orderMapper.toOrderResponse(order))
        );

        log.info("Order created and event published for orderId: {}", order.getId());

        return orderMapper.toOrderResponse(order);
    }

    @Override
    public void updateOrderStatus(Order order, OrderStatus newStatus) {

        if (validateStatusTransition(order.getStatus(), newStatus)) {
            order.setStatus(newStatus);
        }

        log.info("Order status updated for orderId: {}", order.getId());
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Order with id '%d' not found", orderId)));

        updateOrderStatus(order, OrderStatus.CANCELLED);
        order.setOrderUpdateDate(LocalDateTime.now(ZoneId.systemDefault()));
        orderRepository.save(order);

        var items = order.getItems()
                .stream()
                .map(item -> new StockItem(item.getProductId(), item.getQuantity()))
                .toList();


        kafkaEventPublisher.publish(
                KafkaTopics.ORDER_CANCELED,
                orderId.toString(),
                new OrderCanceledEvent(
                        orderId,
                        order.getUserId(),
                        "Order has been canceled",
                        items));

        log.info("Order {} cancelled", orderId);

        orderMapper.toOrderResponse(order);
    }


    @Override
    @Transactional
    public OrderResponse confirmOrder(Long orderId, Long userId) {

        Order orderById = orderCatalogService.getOrderById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Order with id '%d' not found", orderId)));

        if (!orderById.getUserId().equals(userId)) {
            throw new AccessDeniedException("You are not allowed to confirm this order");
        }

        log.info("Order with ID {} was found", orderId);

        updateOrderStatus(orderById, OrderStatus.CONFIRMED);
        orderById.setOrderUpdateDate(LocalDateTime.now(ZoneId.systemDefault()));

        kafkaEventPublisher.publish(
                KafkaTopics.PAYMENT_CREATE,
                orderId.toString(), new PaymentCreateEvent(orderId, userId, orderById.getTotalPrice())
        );

        log.info("Order confirmed event was published for orderId: {} ", orderId);

        return orderMapper.toOrderResponse(orderRepository.save(orderById));
    }

    private boolean validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (current == next) {
            return false;
        }

        Map<OrderStatus, Set<OrderStatus>> allowed = Map.of(
                OrderStatus.NEW, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
                OrderStatus.CONFIRMED, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
                OrderStatus.PAID, Set.of(),
                OrderStatus.CANCELLED, Set.of()
        );

        if (!allowed.get(current).contains(next)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot transition order status from %s to %s",
                    current, next));
        }

        log.info("Order status {} is allowed to transition to {}",
                current, next);
        return true;
    }

    private BigDecimal getTotalPrice(List<OrderItem> items) {
        return items.stream()
                .map(orderItem -> orderItem.getPrice()
                        .multiply(BigDecimal.valueOf(orderItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<OrderItem> processBuildingOrderItemsFromRequest(OrderCreateRequest request) {
        return request.products().stream()
                .map(item -> {
                    var productInfo = productClient.getProductById(item.productId());

                    checkStock(productInfo, item);

                    return OrderItem.builder()
                            .productId(productInfo.id())
                            .productName(productInfo.name())
                            .price(productInfo.price())
                            .category(productInfo.category())
                            .quantity(item.quantity())
                            .build();
                })
                .toList();
    }

    private void checkStock(ProductResponse productResponse, StockItem stockItem) {
        if (productResponse.quantity() < stockItem.quantity()) {
            log.warn("Insufficient stock for product: {}. Available: {}", stockItem.productId(), productResponse.quantity());
            throw new IllegalArgumentException(String.format(
                    "Insufficient stock for '%s'. Available: %d", productResponse.name(), productResponse.quantity()));
        }
    }
}
