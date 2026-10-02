package com.ecommerce.product.service.impl;

import com.ecommerce.commondto.order.StockItem;
import com.ecommerce.product.model.ProcessedEvent;
import com.ecommerce.product.repository.ProcessedEventRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    static final String RESERVE = "STOCK_RESERVE";
    static final String RELEASE = "STOCK_RELEASE";

    private final ProductRepository productRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void reserveStock(Long orderId, List<StockItem> items) {
        String key = String.valueOf(orderId);

        if (processedEventRepository.existsByHandlerAndEventKey(RESERVE, key)) {
            log.info("Stock for order {} already reserved, skipping duplicate event", orderId);
            return;
        }

        processedEventRepository.saveAndFlush(new ProcessedEvent(RESERVE, key));

        items.stream()
                .sorted(Comparator.comparing(StockItem::productId))
                .forEach(item -> applyDecrease(item.productId(), item.quantity()));
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void releaseStock(Long orderId, List<StockItem> items) {
        String key = String.valueOf(orderId);

        if (!processedEventRepository.existsByHandlerAndEventKey(RESERVE, key)) {
            log.info("No reservation found for order {}, nothing to release", orderId);
            return;
        }
        if (processedEventRepository.existsByHandlerAndEventKey(RELEASE, key)) {
            log.info("Stock for order {} already released, skipping duplicate event", orderId);
            return;
        }

        processedEventRepository.saveAndFlush(new ProcessedEvent(RELEASE, key));

        items.stream()
                .sorted(Comparator.comparing(StockItem::productId))
                .forEach(item -> applyIncrease(item.productId(), item.quantity()));
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public void decreaseStock(long productId, int quantity) {
        applyDecrease(productId, quantity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public void increaseStock(long productId, int quantity) {
        applyIncrease(productId, quantity);
    }

    private void applyDecrease(long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity to decrease must be positive, got: " + quantity);
        }

        productRepository.decreaseStock(productId, quantity);
        log.info("Decreased stock for product {} by {}", productId, quantity);
    }

    private void applyIncrease(long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity to increase must be positive, got: " + quantity);
        }
        productRepository.increaseStock(productId, quantity);
        log.info("Increased stock for product {} by {}", productId, quantity);
    }
}

