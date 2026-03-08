package com.rumal.search_service.controller;

import com.rumal.search_service.client.dto.ProductIndexData;
import com.rumal.search_service.dto.ReindexResponse;
import com.rumal.search_service.security.InternalRequestVerifier;
import com.rumal.search_service.service.ProductIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class SearchAdminController {

    private final ProductIndexService productIndexService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping("/reindex")
    public ReindexResponse triggerReindex(
            @RequestHeader("X-Internal-Auth") String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        return productIndexService.triggerFullReindex();
    }

    @PutMapping("/index/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertProduct(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID productId,
            @RequestBody(required = false) ProductIndexData request
    ) {
        internalRequestVerifier.verify(internalAuth);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product index payload is required");
        }
        ProductIndexData normalized = new ProductIndexData(
                productId,
                request.slug(),
                request.name(),
                request.shortDescription(),
                request.brandName(),
                request.mainImage(),
                request.regularPrice(),
                request.discountedPrice(),
                request.sellingPrice(),
                request.sku(),
                request.mainCategory(),
                request.subCategories(),
                request.categories(),
                request.productType(),
                request.vendorId(),
                request.viewCount(),
                request.soldCount(),
                request.active(),
                request.stockAvailable(),
                request.stockStatus(),
                request.backorderable(),
                request.variations(),
                request.createdAt(),
                request.updatedAt()
        );
        productIndexService.upsertProduct(normalized);
    }

    @DeleteMapping("/index/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProduct(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID productId
    ) {
        internalRequestVerifier.verify(internalAuth);
        productIndexService.deleteProduct(productId.toString());
    }
}
