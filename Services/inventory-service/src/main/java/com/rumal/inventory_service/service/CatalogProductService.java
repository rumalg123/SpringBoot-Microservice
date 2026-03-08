package com.rumal.inventory_service.service;

import com.rumal.inventory_service.dto.InventoryCatalogSyncRequest;
import com.rumal.inventory_service.entity.CatalogProduct;
import com.rumal.inventory_service.exception.ValidationException;
import com.rumal.inventory_service.repo.CatalogProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogProductService {

    private final CatalogProductRepository catalogProductRepository;

    @Transactional(readOnly = false)
    public void upsert(InventoryCatalogSyncRequest request) {
        if (request == null || request.productId() == null) {
            throw new ValidationException("Product catalog sync payload must include productId");
        }

        CatalogProduct product = catalogProductRepository.findById(request.productId())
                .orElseGet(() -> CatalogProduct.builder().productId(request.productId()).build());

        product.setVendorId(requireUuid(request.vendorId(), "vendorId"));
        product.setName(requireText(request.name(), "name", 150));
        product.setSku(requireText(request.sku(), "sku", 80));
        product.setActive(request.active());
        product.setDeleted(request.deleted());
        catalogProductRepository.save(product);
    }

    @Transactional(readOnly = false)
    public void delete(UUID productId) {
        if (productId == null) {
            return;
        }
        catalogProductRepository.deleteById(productId);
    }

    public CatalogProduct requireOwnedProduct(UUID productId, UUID vendorId) {
        CatalogProduct product = catalogProductRepository.findById(productId)
                .orElseThrow(() -> new ValidationException("Product is not registered in inventory catalog: " + productId));
        if (!product.getVendorId().equals(vendorId)) {
            throw new ValidationException("Product " + productId + " belongs to another vendor");
        }
        if (product.isDeleted()) {
            throw new ValidationException("Deleted product cannot receive inventory");
        }
        return product;
    }

    public CatalogProduct findByProductId(UUID productId) {
        if (productId == null) {
            return null;
        }
        return catalogProductRepository.findById(productId).orElse(null);
    }

    public Map<UUID, CatalogProduct> findByProductIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return catalogProductRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(CatalogProduct::getProductId, Function.identity(), (left, right) -> left));
    }

    private UUID requireUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new ValidationException(fieldName + " is required");
        }
        return value;
    }

    private String requireText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new ValidationException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ValidationException(fieldName + " exceeds max length of " + maxLength);
        }
        return normalized;
    }
}
