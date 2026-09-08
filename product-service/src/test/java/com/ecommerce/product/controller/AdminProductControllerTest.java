package com.ecommerce.product.controller;

import com.ecommerce.commondto.product.ProductCreateRequest;
import com.ecommerce.commondto.product.ProductResponse;
import com.ecommerce.commondto.product.ProductUpdateRequest;
import com.ecommerce.product.model.Category;
import com.ecommerce.product.service.ProductManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProductController.class)
@TestPropertySource(properties = {"spring.cloud.config.enabled=false"})
@AutoConfigureMockMvc(addFilters = false)
class AdminProductControllerTest {


    private static final String URL = "/api/v1/admin/products";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductManagementService productService;

    @Autowired
    ObjectMapper mapper;


    @Test
    void testCreateProduct() throws Exception {

        // ARRANGE
        var request = new ProductCreateRequest("T-Shirt", new BigDecimal("20.0"), 5, Category.MENS_CLOTHING.name(), "some-url", true);
        var productResponse = new ProductResponse(1L, "T-Shirt", new BigDecimal("20.0"), 5, Category.MENS_CLOTHING.name(), "some-url", true);

        when(productService.createProduct(request)).thenReturn(productResponse);

        // ACT
        // ASSERT
        mockMvc.perform(post(URL + "/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("T-Shirt"))
                .andExpect(jsonPath("$.price").value(20.00))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.category").value("MENS_CLOTHING"))
                .andExpect(jsonPath("$.imageUrl").value("some-url"))
                .andExpect(jsonPath("$.available").value(true));

        verify(productService).createProduct(request);
    }

    @Test
    void testUpdateProduct() throws Exception {

        // ARRANGE
        long id = 1L;
        var request = new ProductUpdateRequest("T-Shirt", new BigDecimal("20.0"), Category.MENS_CLOTHING.name(), 10, "some-url");
        var productResponse = new ProductResponse(1L, "Updated T-Shirt", new BigDecimal("25.0"), 5, Category.MENS_CLOTHING.name(), "updated-url", true);

        when(productService.updateProduct(1L, request)).thenReturn(productResponse);

        // ACT
        // ASSERT
        mockMvc.perform(put(URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated T-Shirt"))
                .andExpect(jsonPath("$.price").value(25.00))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.category").value("MENS_CLOTHING"))
                .andExpect(jsonPath("$.imageUrl").value("updated-url"))
                .andExpect(jsonPath("$.available").value(true));

        verify(productService).updateProduct(1L, request);
    }

    @Test
    void testDeleteProduct() throws Exception {

        // ARRANGE
        long id = 1L;

        // ACT
        // ASSERT
        mockMvc.perform(delete(URL + "/" + id))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(1L);
    }
}
