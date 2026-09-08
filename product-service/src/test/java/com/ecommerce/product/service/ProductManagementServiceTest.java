package com.ecommerce.product.service;

import com.ecommerce.commondto.product.ProductCreateRequest;
import com.ecommerce.commondto.product.ProductResponse;
import com.ecommerce.commondto.product.ProductUpdateRequest;
import com.ecommerce.commonexception.exception.ResourceAlreadyExistsException;
import com.ecommerce.commonexception.exception.ResourceNotFoundException;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.model.Category;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.impl.ProductManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductManagementServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductManagementServiceImpl productManagementService;

    private Product product;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .name("T-Shirt")
                .price(BigDecimal.valueOf(1000))
                .category(Category.MENS_CLOTHING)
                .imageUrl("T-Shirt.jpg")
                .quantity(10)
                .available(true)
                .build();

        productResponse = mock(ProductResponse.class);
    }

    @Test
    void createProduct_shouldCreateAndReturnProduct() {
        // Given
        ProductCreateRequest request = new ProductCreateRequest(
                "T-Shirt",
                BigDecimal.valueOf(1000),
                5,
                "MENS_CLOTHING",
                "T-Shirt.jpg",
                true

        );

        when(productRepository.getProductByName(request.name()))
                .thenReturn(Optional.empty());

        when(productMapper.toProductResponse(any(Product.class)))
                .thenReturn(productResponse);

        // When
        ProductResponse result =
                productManagementService.createProduct(request);

        // Then
        assertSame(productResponse, result);

        ArgumentCaptor<Product> productCaptor =
                ArgumentCaptor.forClass(Product.class);

        verify(productRepository).save(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();

        assertEquals("T-Shirt", savedProduct.getName());
        assertEquals(
                BigDecimal.valueOf(1000),
                savedProduct.getPrice()
        );
        assertEquals(
                Category.MENS_CLOTHING,
                savedProduct.getCategory()
        );
        assertEquals("T-Shirt.jpg", savedProduct.getImageUrl());
        assertEquals(5, savedProduct.getQuantity());
        assertTrue(savedProduct.isAvailable());

        verify(productRepository)
                .getProductByName("T-Shirt");

        verify(productMapper)
                .toProductResponse(savedProduct);
    }

    @Test
    void createProduct_shouldThrowExceptionWhenProductAlreadyExists() {
        // Given
        ProductCreateRequest request = new ProductCreateRequest(
                "T-Shirt",
                BigDecimal.valueOf(1000),
                5,
                "MENS_CLOTHING",
                "T-Shirt.jpg",
                true

        );

        when(productRepository.getProductByName(request.name()))
                .thenReturn(Optional.of(product));

        // When
        ResourceAlreadyExistsException exception = assertThrows(
                ResourceAlreadyExistsException.class,
                () -> productManagementService.createProduct(request)
        );

        // Then
        assertEquals(
                "Product with name 'T-Shirt' already exists",
                exception.getMessage()
        );

        verify(productRepository)
                .getProductByName("T-Shirt");

        verify(productRepository, never())
                .save(any(Product.class));

        verifyNoInteractions(productMapper);
    }

    @Test
    void updateProduct_shouldUpdateAndReturnProduct() {
        // Given
        Long productId = 1L;

        ProductUpdateRequest request = new ProductUpdateRequest(
                "Gaming T-Shirt",
                BigDecimal.valueOf(1500),
                "MENS_CLOTHING",
                1,
                "gaming-T-Shirt.jpg"
        );

        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        when(productMapper.toProductResponse(product))
                .thenReturn(productResponse);

        // When
        ProductResponse result =
                productManagementService.updateProduct(
                        productId,
                        request
                );

        // Then
        assertSame(productResponse, result);

        assertEquals("Gaming T-Shirt", product.getName());
        assertEquals(
                BigDecimal.valueOf(1500),
                product.getPrice()
        );
        assertEquals(
                Category.MENS_CLOTHING,
                product.getCategory()
        );
        assertEquals(
                "gaming-T-Shirt.jpg",
                product.getImageUrl()
        );
        assertEquals(1, product.getQuantity());

        verify(productRepository)
                .findById(productId);

        verify(productRepository)
                .save(product);

        verify(productMapper)
                .toProductResponse(product);
    }

    @Test
    void updateProduct_shouldThrowExceptionWhenProductDoesNotExist() {
        // Given
        Long productId = 999L;

        ProductUpdateRequest request = new ProductUpdateRequest(
                "Gaming T-Shirt",
                BigDecimal.valueOf(1500),
                "MENS_CLOTHING",
                1,
                "gaming-T-Shirt.jpg"
        );

        when(productRepository.findById(productId))
                .thenReturn(Optional.empty());

        // When
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productManagementService.updateProduct(
                        productId,
                        request
                )
        );

        // Then
        assertEquals(
                "Product with id 999 not found",
                exception.getMessage()
        );

        verify(productRepository)
                .findById(productId);

        verify(productRepository, never())
                .save(any(Product.class));

        verifyNoInteractions(productMapper);
    }

    @Test
    void updateProduct_shouldChangeCategory() {
        // Given
        Long productId = 1L;

        ProductUpdateRequest request = new ProductUpdateRequest(
                "Gaming T-Shirt",
                BigDecimal.valueOf(1500),
                "MENS_CLOTHING",
                1,
                "gaming-T-Shirt.jpg"
        );

        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        when(productMapper.toProductResponse(product))
                .thenReturn(productResponse);

        // When
        productManagementService.updateProduct(
                productId,
                request
        );

        // Then
        assertEquals(
                Category.MENS_CLOTHING,
                product.getCategory()
        );

        verify(productRepository).save(product);
    }

    @Test
    void deleteProduct_shouldDeleteProductById() {
        // Given
        Long productId = 1L;

        // When
        productManagementService.deleteProduct(productId);

        // Then
        verify(productRepository)
                .deleteById(productId);
    }

    @Test
    void deleteProduct_shouldNotInteractWithMapper() {
        // Given
        Long productId = 1L;

        // When
        productManagementService.deleteProduct(productId);

        // Then
        verify(productRepository)
                .deleteById(productId);

        verifyNoInteractions(productMapper);
    }
}