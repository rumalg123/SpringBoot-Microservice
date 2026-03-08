package com.rumal.product_service.service;

import com.rumal.product_service.dto.InventoryCatalogSyncRequest;
import com.rumal.product_service.entity.Product;
import com.rumal.product_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductInventorySyncPayloadFactory {

    private final ProductRepository productRepository;

    public Optional<InventoryCatalogSyncRequest> build(UUID productId) {
        if (productId == null) {
            return Optional.empty();
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return Optional.empty();
        }

        return Optional.of(new InventoryCatalogSyncRequest(
                product.getId(),
                product.getVendorId(),
                product.getName(),
                product.getSku(),
                product.isActive(),
                product.isDeleted()
        ));
    }
}
