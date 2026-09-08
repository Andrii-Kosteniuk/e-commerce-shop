package com.ecommerce.product.controller;

import com.ecommerce.commondto.product.ProductResponse;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.model.Category;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.service.ProductCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
@TestPropertySource(properties = {"spring.cloud.config.enabled=false"})
@AutoConfigureMockMvc(addFilters = false)
class CatalogControllerTest {

    private static final String URL = "/api/v1/products";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductCatalogService catalogService;
    @MockitoBean
    ProductMapper productMapper;

    @Test
    void testGetAllProducts() throws Exception {

        // ARRANGE
        Page<ProductResponse> productPageResponse = new PageImpl<>(List.of(
                new ProductResponse(1L, "T-Shirt", new BigDecimal("20.0"), 5, Category.MENS_CLOTHING.name(), "some-url", true),
                new ProductResponse(2L, "Jeans", new BigDecimal("10.0"), 10, Category.MENS_CLOTHING.name(), "some-url", true)));

        when(catalogService.getProducts(any(Pageable.class))).thenReturn(productPageResponse);

        // ACT
        // ASSERT
        mockMvc.perform(get(URL)
                        .param("size", "20")
                        .param("sort", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("T-Shirt"))
                .andExpect(jsonPath("$.content[1].name").value("Jeans"));

        verify(catalogService).getProducts(any(Pageable.class));

    }

    @Test
    void testGetProductByName() throws Exception {

        // ARRANGE
        var productPageResponse =
                new ProductResponse(1L, "T-Shirt", new BigDecimal("20.0"), 5, Category.MENS_CLOTHING.name(), "some-url", true);

        when(catalogService.getProductByName(any(String.class))).thenReturn(productPageResponse);

        // ACT
        // ASSERT
        mockMvc.perform(get(URL + "/name")
                        .param("name", "T-Shirt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("T-Shirt"));

        verify(catalogService).getProductByName(any(String.class));

    }

    @Test
    void testGetProductById() throws Exception {

        // ARRANGE
        long id = 1L;

        Product product = new Product();
        product.setId(id);
        product.setName("T-Shirt");
        product.setPrice(new BigDecimal("20.0"));
        product.setQuantity(5);
        product.setCategory(Category.MENS_CLOTHING);
        product.setImageUrl("some-url");
        product.setAvailable(true);
        when(catalogService.getProductById(id)).thenReturn(product);

        when(productMapper.toProductResponse(product))
                .thenReturn(
                        new ProductResponse(
                                id,
                                "T-Shirt",
                                new BigDecimal("20.0"),
                                5,
                                Category.MENS_CLOTHING.name(),
                                "some-url",
                                true
                        )
                );

        // ACT
        // ASSERT
        mockMvc.perform(get(URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("T-Shirt"));

        verify(catalogService).getProductById(id);

    }

    @Test
    void testProductsByCategory() throws Exception {

        // ARRANGE
        long id = 1L;
        List<Product> products = List.of(
                new Product(id,
                        "T-Shirt-1",
                        new BigDecimal("20.0"),
                        5,
                        Category.MENS_CLOTHING,
                        "some-url",
                        LocalDateTime.of(2026, Month.APRIL, 19, 15, 45),
                        LocalDateTime.now(), true),
                new Product(id,
                        "T-Shirt-2",
                        new BigDecimal("20.0"),
                        5,
                        Category.MENS_CLOTHING,
                        "some-url",
                        LocalDateTime.of(2026, Month.APRIL, 19, 14, 50),
                        LocalDateTime.now(), true)
        );

        when(catalogService.getProductsByCategoryName(Category.MENS_CLOTHING)).thenReturn(products);

        when(productMapper.toProductResponse(products.get(0)))
                .thenReturn(new ProductResponse(
                        1L,
                        "T-Shirt-1",
                        new BigDecimal("20.0"),
                        5,
                        Category.MENS_CLOTHING.name(),
                        "some-url",
                        true));

        when(productMapper.toProductResponse(products.get(1)))
                .thenReturn(new ProductResponse(
                        1L,
                        "T-Shirt-2",
                        new BigDecimal("20.0"),
                        5,
                        Category.MENS_CLOTHING.name(),
                        "some-url",
                        true));
        // ACT
        // ASSERT
        mockMvc.perform(get(URL + "/category")
                        .param("category", Category.MENS_CLOTHING.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("T-Shirt-1"))
                .andExpect(jsonPath("$[1].name").value("T-Shirt-2"));


    }
}
