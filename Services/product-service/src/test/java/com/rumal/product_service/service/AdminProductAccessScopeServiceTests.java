package com.rumal.product_service.service;

import com.rumal.product_service.client.AccessClient;
import com.rumal.product_service.client.VendorAccessClient;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.entity.Product;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.exception.UnauthorizedException;
import com.rumal.product_service.repo.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminProductAccessScopeServiceTests {

    private final AccessClient accessClient = mock(AccessClient.class);
    private final VendorAccessClient vendorAccessClient = mock(VendorAccessClient.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final AdminProductAccessScopeService service = new AdminProductAccessScopeService(
            accessClient,
            vendorAccessClient,
            productRepository
    );

    @Test
    void scopeUpdateRequestRejectsVendorReassignmentForPlatformActors() {
        UUID productId = UUID.randomUUID();
        UUID existingVendorId = UUID.randomUUID();
        UUID attemptedVendorId = UUID.randomUUID();

        when(productRepository.findById(productId)).thenReturn(Optional.of(Product.builder()
                .id(productId)
                .vendorId(existingVendorId)
                .productType(ProductType.SINGLE)
                .build()));

        AdminProductAccessScopeService.ProductMutationAuthority authority =
                new AdminProductAccessScopeService.ProductMutationAuthority(
                        true,
                        true,
                        true,
                        Set.of(),
                        Set.of(),
                        Set.of()
                );

        assertThrows(UnauthorizedException.class, () -> service.scopeUpdateRequest(
                authority,
                productId,
                request(ProductType.SINGLE, attemptedVendorId)
        ));
    }

    @Test
    void assertCanSubmitProductForReviewRejectsVendorStaffActors() {
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();

        when(productRepository.findByIdAndVendorIdIn(productId, Set.of(vendorId))).thenReturn(Optional.of(Product.builder()
                .id(productId)
                .vendorId(vendorId)
                .productType(ProductType.SINGLE)
                .build()));

        AdminProductAccessScopeService.ProductMutationAuthority authority =
                new AdminProductAccessScopeService.ProductMutationAuthority(
                        false,
                        false,
                        false,
                        Set.of(),
                        Set.of(vendorId),
                        Set.of(vendorId)
                );

        assertThrows(UnauthorizedException.class, () -> service.assertCanSubmitProductForReview(authority, productId));
    }

    @Test
    void scopeVariationCreateRequestPinsVendorIdToParentVendor() {
        UUID parentId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();

        when(productRepository.findByIdAndVendorIdIn(parentId, Set.of(vendorId))).thenReturn(Optional.of(Product.builder()
                .id(parentId)
                .vendorId(vendorId)
                .productType(ProductType.PARENT)
                .build()));

        AdminProductAccessScopeService.ProductMutationAuthority authority =
                new AdminProductAccessScopeService.ProductMutationAuthority(
                        false,
                        false,
                        false,
                        Set.of(vendorId),
                        Set.of(),
                        Set.of(vendorId)
                );

        UpsertProductRequest scoped = service.scopeVariationCreateRequest(
                authority,
                parentId,
                request(ProductType.VARIATION, null)
        );

        assertEquals(vendorId, scoped.vendorId());
    }

    @Test
    void assertCanManageProductRejectsVendorScopedLookupOutsideAllowedVendorSet() {
        UUID productId = UUID.randomUUID();
        UUID allowedVendorId = UUID.randomUUID();

        when(productRepository.findByIdAndVendorIdIn(productId, Set.of(allowedVendorId))).thenReturn(Optional.empty());

        AdminProductAccessScopeService.ProductMutationAuthority authority =
                new AdminProductAccessScopeService.ProductMutationAuthority(
                        false,
                        false,
                        false,
                        Set.of(allowedVendorId),
                        Set.of(),
                        Set.of(allowedVendorId)
                );

        assertThrows(com.rumal.product_service.exception.ResourceNotFoundException.class,
                () -> service.assertCanManageProduct(authority, productId));
    }

    private UpsertProductRequest request(ProductType productType, UUID vendorId) {
        return new UpsertProductRequest(
                "Secure Product",
                "secure-product",
                "Short description",
                "<p>Long description</p>",
                "Brand",
                List.of("image.jpg"),
                new BigDecimal("10.00"),
                new BigDecimal("9.00"),
                vendorId,
                Set.of("Category"),
                productType,
                List.of(),
                "SKU-1",
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                List.of()
        );
    }
}
