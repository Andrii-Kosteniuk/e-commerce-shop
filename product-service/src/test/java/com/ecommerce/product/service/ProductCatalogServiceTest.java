package com.ecommerce.product.service;

import com.ecommerce.commondto.product.ProductResponse;
import com.ecommerce.commonexception.exception.ResourceNotFoundException;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.model.Category;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.impl.ProductCatalogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductCatalogServiceImpl productCatalogService;

    private Product product;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .name("T-Shirt")
                .price(BigDecimal.valueOf(1000))
                .quantity(10)
                .category(Category.MENS_CLOTHING)
                .build();

        productResponse = new ProductResponse(
                1L,
                "T-Shirt",
                BigDecimal.valueOf(1000),
                5,
                Category.MENS_CLOTHING.name(),
                null,
                true
        );
    }

    @Test
    void getProducts_shouldReturnMappedProductsWithPagination() {
        // Given
        Pageable pageable = PageRequest.of(1, 2);

        Product secondProduct = Product.builder()
                .id(2L)
                .name("T-Shirt")
                .price(BigDecimal.valueOf(50))
                .quantity(20)
                .category(Category.MENS_CLOTHING)
                .build();

        ProductResponse secondProductResponse = new ProductResponse(
                2L,
                "T-Shirt",
                BigDecimal.valueOf(50),
                1,
                Category.MENS_CLOTHING.name(),
                null,
                true
        );

        Page<Product> productPage = new PageImpl<>(
                List.of(product, secondProduct),
                pageable,
                10
        );

        when(productRepository.findAll(pageable))
                .thenReturn(productPage);

        when(productMapper.toProductResponse(product))
                .thenReturn(productResponse);

        when(productMapper.toProductResponse(secondProduct))
                .thenReturn(secondProductResponse);

        // When
        Page<ProductResponse> result =
                productCatalogService.getProducts(pageable);

        // Then
        assertNotNull(result);

        assertEquals(1, result.getNumber());
        assertEquals(2, result.getSize());
        assertEquals(10, result.getTotalElements());
        assertEquals(5, result.getTotalPages());

        assertEquals(
                List.of(productResponse, secondProductResponse),
                result.getContent()
        );

        verify(productRepository).findAll(pageable);

        verify(productMapper).toProductResponse(product);
        verify(productMapper).toProductResponse(secondProduct);
    }

    @Test
    void getProducts_shouldReturnEmptyPageWhenRepositoryReturnsNoProducts() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        Page<Product> emptyPage = new PageImpl<>(
                List.of(),
                pageable,
                0
        );

        when(productRepository.findAll(pageable))
                .thenReturn(emptyPage);

        // When
        Page<ProductResponse> result =
                productCatalogService.getProducts(pageable);

        // Then
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());

        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getTotalPages());
        assertEquals(0, result.getNumber());
        assertEquals(10, result.getSize());

        verify(productRepository).findAll(pageable);

        verifyNoInteractions(productMapper);
    }

    @Test
    void getProductByName_shouldReturnMappedProduct() {
        // Given
        String productName = "T-Shirt";

        when(productRepository.getProductByName(productName))
                .thenReturn(Optional.of(product));

        when(productMapper.toProductResponse(product))
                .thenReturn(productResponse);

        // When
        ProductResponse result =
                productCatalogService.getProductByName(productName);

        // Then
        assertSame(productResponse, result);

        verify(productRepository)
                .getProductByName(productName);

        verify(productMapper)
                .toProductResponse(product);
    }

    @Test
    void getProductByName_shouldThrowExceptionWhenProductDoesNotExist() {
        // Given
        String productName = "Unknown";

        when(productRepository.getProductByName(productName))
                .thenReturn(Optional.empty());

        // When
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productCatalogService.getProductByName(productName)
        );

        // Then
        assertEquals(
                "Product with name Unknown not found",
                exception.getMessage()
        );

        verify(productRepository)
                .getProductByName(productName);

        verifyNoInteractions(productMapper);
    }

    @Test
    void getProductsByCategoryName_shouldReturnProductsFromRepository() {
        // Given
        Category category = Category.MENS_CLOTHING;

        List<Product> products = List.of(product);

        when(productRepository.getProductsByCategory(category))
                .thenReturn(products);

        // When
        List<Product> result =
                productCatalogService.getProductsByCategoryName(category);

        // Then
        assertSame(products, result);

        verify(productRepository)
                .getProductsByCategory(category);

        verifyNoInteractions(productMapper);
    }

    @Test
    void getProductsByCategoryName_shouldReturnEmptyListWhenNoProductsExist() {
        // Given
        Category category = Category.MENS_CLOTHING;

        when(productRepository.getProductsByCategory(category))
                .thenReturn(List.of());

        // When
        List<Product> result =
                productCatalogService.getProductsByCategoryName(category);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(productRepository)
                .getProductsByCategory(category);

        verifyNoInteractions(productMapper);
    }

    @Test
    void getProductById_shouldReturnProduct() {
        // Given
        Long productId = 1L;

        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        // When
        Product result =
                productCatalogService.getProductById(productId);

        // Then
        assertSame(product, result);

        verify(productRepository).findById(productId);
    }

    @Test
    void getProductById_shouldThrowExceptionWhenProductDoesNotExist() {
        // Given
        Long productId = 999L;

        when(productRepository.findById(productId))
                .thenReturn(Optional.empty());

        // When
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productCatalogService.getProductById(productId)
        );

        // Then
        assertEquals(
                "Product with id 999 not found",
                exception.getMessage()
        );

        verify(productRepository).findById(productId);
    }
}