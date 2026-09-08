package com.ecommerce.gateway;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"SECURITY_INTERNAL_API_KEY=some-key",})
class TestApiGatewayRouter {

    @Autowired
    RouteLocator routeLocator;

    @Test
    void testApiGatewayContainsAllServiceRouts() {

        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).extracting(Route::getId)
                .contains(
                        "auth-service",
                        "product-service",
                        "order-service",
                        "payment-service");
    }



}