package com.ecommerce.product.service;

import com.ecommerce.commondto.order.StockItem;

import java.util.List;

public interface InventoryService {

    void reserveStock(Long orderId, List<StockItem> items);

    void releaseStock(Long orderId, List<StockItem> items);

    void decreaseStock(long productId, int quantity);

    void increaseStock(long productId, int quantity);

}
