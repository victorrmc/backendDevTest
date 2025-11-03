package com.example.backendDevTest.controller;


import com.example.backendDevTest.dto.ProductDetail;
import com.example.backendDevTest.service.SimilarProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.util.List;

import static org.mockito.Mockito.when;


@WebFluxTest(SimilarProductController.class)
public class SimilarProductControllerTest {

   @Autowired
   private WebTestClient webTestClient;

   @MockitoBean
   private SimilarProductService similarProductService;

    @Test
    void getSimilarProducts() {
        ProductDetail productDetail1 = new ProductDetail("1", "product1", 100.0, true);
        ProductDetail productDetail2 = new ProductDetail("2", "product2", 100.0, true);

        when(similarProductService.getSimilarProducts("1"))
                .thenReturn(Mono.just(List.of(productDetail1, productDetail2)));

        webTestClient.get().uri("/product/1/similar")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.size()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("1")
                .jsonPath("$[1].price").isEqualTo(100);
    }

    @Test
    void getErrrorSimilarProducts() {
        when(similarProductService.getSimilarProducts("999"))
                .thenReturn(Mono.error(WebClientResponseException.NotFound.create
                        (404, "Not found", null, null, null, null)));

        webTestClient.get().uri("/product/999/similar")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
}
