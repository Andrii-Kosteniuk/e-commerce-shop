package com.ecommerce.order.controller;

import com.ecommerce.commondto.order.OrderResponse;
import com.ecommerce.commondto.order.OrderCreateRequest;
import com.ecommerce.order.service.OrderCatalogService;
import com.ecommerce.order.service.OrderModifiedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderModifiedService modifiedService;
    private final OrderCatalogService catalogService;

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(catalogService.getAllOrders());
    }

    @PostMapping("/confirmation/{orderId}")
    public ResponseEntity<OrderResponse> confirmOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(modifiedService.confirmOrder(orderId, userId));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request, @RequestHeader("X-User-Id") Long userId) {

        var order = modifiedService.createOrder(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
