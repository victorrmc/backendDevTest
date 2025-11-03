package com.example.backendDevTest.service;

import com.example.backendDevTest.dto.ProductDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;

@Service
public class SimilarProductService {

    public static final String ERROR_FETCHING_PRODUCT_DETAIL = "Error fetching product detail {}";
    private final WebClient webClient;
    private final int maxActiveRequest;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public SimilarProductService(WebClient webClient, @Value("${product.external.api.max.active.request}") int maxActiveRequest ) {
        this.webClient = webClient;
        this.maxActiveRequest = maxActiveRequest;
    }

    public Mono<List<ProductDetail>> getSimilarProducts(String productID){
        return getSimilarProductsIds(productID)
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::getProductDetail, maxActiveRequest)
                .collectList();
    }

    public Mono<List<String>> getSimilarProductsIds(String productID){
        return webClient.get()
                .uri("/product/{productid}/similarids", productID)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public Mono<ProductDetail> getProductDetail(String productID){
        return webClient.get()
                .uri("/product/{productid}", productID)
                .retrieve()
                .bodyToMono(ProductDetail.class)
                .onErrorResume(e -> {
                    log.error(ERROR_FETCHING_PRODUCT_DETAIL, productID,  e);
                    return Mono.empty();
                });
    }

}
