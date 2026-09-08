package com.ecommerce.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "SECURITY_INTERNAL_API_KEY=some-key",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false"
        }
)
class TestGatewaySecurityFilter {

    @LocalServerPort
    int port;

    private WebTestClient client;

    static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(9001);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Test
    void shouldRouteToAuthService() {

        client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        wireMockServer.stubFor(
                get("/api/v1/auth/login")
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                        )
        );

        client.get()
                .uri("/api/v1/auth/login")
                .exchange()
                .expectStatus()
                .isOk();
    }
}