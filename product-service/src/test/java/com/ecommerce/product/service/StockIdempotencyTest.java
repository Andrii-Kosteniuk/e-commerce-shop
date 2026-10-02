package com.ecommerce.product.service;

import com.ecommerce.commondto.order.StockItem;
import com.ecommerce.product.repository.ProcessedEventRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockIdempotencyTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    ProcessedEventRepository processedEventRepository;
    @InjectMocks
    InventoryServiceImpl inventoryService;

    private final List<StockItem> items = List.of(new StockItem(2L, 3), new StockItem(1L, 1));

    @Test
    void reserveStock_firstDelivery_decrementsEachItemInProductIdOrder() {
        when(processedEventRepository.existsByHandlerAndEventKey("STOCK_RESERVE", "10")).thenReturn(false);
        when(productRepository.decreaseStock(anyLong(), anyInt())).thenReturn(1);

        inventoryService.reserveStock(10L, items);

        var order = inOrder(productRepository);
        order.verify(productRepository).decreaseStock(1L, 1);
        order.verify(productRepository).decreaseStock(2L, 3);
    }

    @Test
    void reserveStock_redelivery_isNoOp() {
        when(processedEventRepository.existsByHandlerAndEventKey("STOCK_RESERVE", "10")).thenReturn(true);

        inventoryService.reserveStock(10L, items);

        verifyNoInteractions(productRepository);
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void releaseStock_withoutReservation_doesNothing() {
        when(processedEventRepository.existsByHandlerAndEventKey("STOCK_RESERVE", "10")).thenReturn(false);

        inventoryService.releaseStock(10L, items);

        verifyNoInteractions(productRepository);
    }

    @Test
    void releaseStock_twice_incrementsOnlyOnce() {
        when(processedEventRepository.existsByHandlerAndEventKey("STOCK_RESERVE", "10")).thenReturn(true);
        when(processedEventRepository.existsByHandlerAndEventKey("STOCK_RELEASE", "10"))
                .thenReturn(false).thenReturn(true);
        when(productRepository.increaseStock(anyLong(), anyInt())).thenReturn(1);

        inventoryService.releaseStock(10L, items);
        inventoryService.releaseStock(10L, items);

        verify(productRepository, times(2)).increaseStock(anyLong(), anyInt());
    }
}