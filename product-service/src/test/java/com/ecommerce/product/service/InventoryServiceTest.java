package com.ecommerce.product.service;

import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    InventoryServiceImpl inventoryService;


    @Test
    void shouldDecreaseStockIfQuantityIsCorrect() {

        // ARRANGE
        when(productRepository.decreaseStock(1L, 2)).thenReturn(1);

        // ACT
        inventoryService.decreaseStock(1L, 2);

        // ASSERT
        verify(productRepository).decreaseStock(1L, 2);
        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    void shouldThrowWhenDecreaseQuantityIsZero() {

        // ARRANGE

        // ACT
        // ASSERT
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.decreaseStock(1L, 0));

        assertEquals(
                "Quantity to decrease must be positive, got: 0",
                ex.getMessage());

        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldThrowWhenDecreaseQuantityIsNegative() {

        // ARRANGE

        // ACT
        // ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.decreaseStock(1L, -5));

        verifyNoInteractions(productRepository);
    }


    @Test
    void shouldIncreaseStock() {

        // ARRANGE
        when(productRepository.increaseStock(1L, 5)).thenReturn(1);

        // ACT
        inventoryService.increaseStock(1L, 5);

        // ASSERT
        verify(productRepository).increaseStock(1L, 5);
    }

    @Test
    void shouldThrowWhenIncreaseQuantityIsNegative() {
        // ARRANGE

        // ACT
        // ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.increaseStock(1L, -1));

        verifyNoInteractions(productRepository);
    }

}
