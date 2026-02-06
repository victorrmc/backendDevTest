package com.example.backendDevTest.service;

import com.example.backendDevTest.dto.ProductDetail;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarProductServiceTest {

    public static final String BODY_SIMILAR_IDS_2 = "[\"1\", \"6\"]";
    public static final String BODY_PRODUCT_ID_1 = "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}";
    public static final String BODY_SIMILAR_IDS = "[\"2\", \"3\"]";
    public static final String BODY_PRODUCT_ID_2 = "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}";
    public static final String BODY_PRODUCT_ID_3 = "{\"id\":\"3\",\"name\":\"Blazer\",\"price\":29.99,\"availability\":false}";

    private MockWebServer mockWebServer;
    private SimilarProductService productService;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private static final int MAX_ACTIVE_REQUEST = 5;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(5)
                .build();
        circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .build();
        retryRegistry = RetryRegistry.of(retryConfig);

        productService = new SimilarProductService(
                webClient,
                MAX_ACTIVE_REQUEST,
                circuitBreakerRegistry,
                retryRegistry
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getSimilarProducts_Success() {

        mockWebServer.enqueue(new MockResponse()
                .setBody(BODY_SIMILAR_IDS)
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody(BODY_PRODUCT_ID_2)
                .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setBody(BODY_PRODUCT_ID_3)
                .addHeader("Content-Type", "application/json"));


        Mono<List<ProductDetail>> resultMono = productService.getSimilarProducts("1");


        StepVerifier.create(resultMono)
                .assertNext(products -> {
                        assertThat(products).hasSize(2);
                        assertThat(products).extracting(ProductDetail::getId)
                                .contains("2", "3");
                }).verifyComplete();
    }

    @Test
    void getSimilarProducts_ResilienceTest_WithPartialFailure() {

        mockWebServer.enqueue(new MockResponse()
                .setBody(BODY_SIMILAR_IDS_2)
                .addHeader("Content-Type", "application/json"));


        mockWebServer.enqueue(new MockResponse()
                .setBody(BODY_PRODUCT_ID_1)
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        Mono<List<ProductDetail>> resultMono = productService.getSimilarProducts("5");

        StepVerifier.create(resultMono)
                .assertNext(products -> {
                    assertThat(products).hasSize(1);

                    assertThat(products.getFirst()).returns("1", ProductDetail::getId);
                })
                .verifyComplete();
    }

    @Test
    void getProductDetail_Retry_SuccessAfterFailure() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse()
                .setBody(BODY_PRODUCT_ID_1)
                .addHeader("Content-Type", "application/json"));

        Mono<ProductDetail> resultMono = productService.getProductDetail("1");

        StepVerifier.create(resultMono)
                .assertNext(product -> {
                    assertThat(product.getId()).isEqualTo("1");
                    assertThat(product.getName()).isEqualTo("Shirt");
                })
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }
}
