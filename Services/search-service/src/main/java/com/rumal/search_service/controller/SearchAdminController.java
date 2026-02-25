package com.rumal.search_service.controller;

import com.rumal.search_service.dto.ReindexResponse;
import com.rumal.search_service.security.InternalRequestVerifier;
import com.rumal.search_service.service.ProductIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
