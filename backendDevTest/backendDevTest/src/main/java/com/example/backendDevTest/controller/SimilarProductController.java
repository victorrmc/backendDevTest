package com.example.backendDevTest.controller;

import com.example.backendDevTest.dto.ProductDetail;
import com.example.backendDevTest.service.SimilarProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@RestController
@RequestMapping("/product")
public class SimilarProductController {

    public static final String PRODUCT_NOT_FOUND = "Product not found: {}";
    public static final String ERROR_FETCHING_SIMILAR_PRODUCTS = "Error getting similar products for product id: {}";
    private final SimilarProductService similarProductService;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public SimilarProductController(SimilarProductService similarProductService) {
        this.similarProductService = similarProductService;
    }

    @GetMapping("/{productid}/similar")
    public Mono<ResponseEntity<List<ProductDetail>>> getSimilarProducts(@PathVariable String productid) {

        return similarProductService.getSimilarProducts(productid)
                .map(ResponseEntity::ok)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                    log.warn(PRODUCT_NOT_FOUND, productid, e);
                    return Mono.just(ResponseEntity.notFound().build());
                })
                .onErrorResume(e -> {
                    log.error(ERROR_FETCHING_SIMILAR_PRODUCTS, productid, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }


}
