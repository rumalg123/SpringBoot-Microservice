package com.rumal.product_service.service;

import com.rumal.product_service.client.AccessClient;
import com.rumal.product_service.client.VendorAccessClient;
import com.rumal.product_service.dto.PlatformAccessLookupResponse;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.dto.VendorAccessMembershipResponse;
import com.rumal.product_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.product_service.entity.Product;
import com.rumal.product_service.exception.ResourceNotFoundException;
import com.rumal.product_service.exception.UnauthorizedException;
import com.rumal.product_service.exception.ValidationException;
import com.rumal.product_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminProductAccessScopeService {

    public static final String PLATFORM_PRODUCTS_MANAGE = "platform.products.manage";
    public static final String PLATFORM_CATEGORIES_MANAGE = "platform.categories.manage";
    public static final String VENDOR_PRODUCTS_MANAGE = "vendor.products.manage";

    private final AccessClient accessClient;
    private final VendorAccessClient vendorAccessClient;
    private final ProductRepository productRepository;

    public UUID resolveScopedVendorFilter(String userSub, String userRolesHeader, UUID requestedVendorId, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin() || scope.platformProductsManage()) {
            return requestedVendorId;
        }
        return resolveVendorIdForVendorScopedActor(scope.vendorProductVendorIds(), requestedVendorId);
    }

    public UpsertProductRequest scopeCreateRequest(String userSub, String userRolesHeader, UpsertProductRequest request, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin() || scope.platformProductsManage()) {
            return request;
        }
        UUID resolvedVendorId = request.productType() == com.rumal.product_service.entity.ProductType.VARIATION
                ? request.vendorId()
                : resolveVendorIdForVendorScopedActor(scope.vendorProductVendorIds(), request.vendorId());
        return copyRequestWithVendorId(request, resolvedVendorId);
    }

    public UpsertProductRequest scopeUpdateRequest(UUID productId, String userSub, String userRolesHeader, UpsertProductRequest request, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin() || scope.platformProductsManage()) {
            return request;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        assertVendorScopedActorCanManageProduct(scope.vendorProductVendorIds(), product);

        UUID vendorId = request.vendorId();
        if (product.getProductType() != com.rumal.product_service.entity.ProductType.VARIATION) {
            UUID existingVendorId = product.getVendorId();
            if (existingVendorId == null) {
                throw new ValidationException("Existing product vendorId is missing");
            }
            if (vendorId != null && !existingVendorId.equals(vendorId)) {
                throw new UnauthorizedException("Vendor-scoped user cannot reassign product to another vendor");
            }
            vendorId = existingVendorId;
        }

        return copyRequestWithVendorId(request, vendorId);
    }

    public void assertCanManageProduct(UUID productId, String userSub, String userRolesHeader, String internalAuth) {
        assertCanManageProductOperations(userSub, userRolesHeader, internalAuth);
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin() || scope.platformProductsManage()) {
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        assertVendorScopedActorCanManageProduct(scope.vendorProductVendorIds(), product);
    }

    public void assertCanManageProductOperations(String userSub, String userRolesHeader, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin() || scope.platformProductsManage()) {
            return;
        }
        if (scope.vendorProductVendorIds().isEmpty()) {
            throw new UnauthorizedException("Caller does not have product management access");
        }
    }

    public void assertCanManageCategories(String userSub, String userRolesHeader, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin() || scope.platformCategoriesManage()) {
            return;
        }
        throw new UnauthorizedException("Caller does not have category management access");
    }

    private void assertVendorScopedActorCanManageProduct(Set<UUID> vendorIds, Product product) {
        if (product.getVendorId() == null || !vendorIds.contains(product.getVendorId())) {
            throw new UnauthorizedException("Vendor-scoped user cannot manage products of another vendor");
        }
    }

    private UUID resolveVendorIdForVendorScopedActor(Set<UUID> vendorIds, UUID requestedVendorId) {
        if (vendorIds.isEmpty()) {
            throw new UnauthorizedException("No vendor product access found");
        }
        if (requestedVendorId != null) {
            if (!vendorIds.contains(requestedVendorId)) {
                throw new UnauthorizedException("Vendor-scoped user cannot use another vendorId");
            }
            return requestedVendorId;
        }
        if (vendorIds.size() == 1) {
            return vendorIds.iterator().next();
        }
        throw new ValidationException("vendorId is required when user has access to multiple vendors");
    }

    private ActorScope resolveScope(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return new ActorScope(true, true, true, Set.of());
        }

        boolean platformProductsManage = false;
        boolean platformCategoriesManage = false;
        if (roles.contains("platform_staff")) {
            PlatformAccessLookupResponse platformAccess = accessClient.getPlatformAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
            platformProductsManage = platformAccess.active() && permissions.contains(PLATFORM_PRODUCTS_MANAGE);
            platformCategoriesManage = platformAccess.active() && permissions.contains(PLATFORM_CATEGORIES_MANAGE);
            if (platformProductsManage || platformCategoriesManage) {
                return new ActorScope(false, platformProductsManage, platformCategoriesManage, Set.of());
            }
        }

        Set<UUID> vendorIds = new LinkedHashSet<>();
        if (roles.contains("vendor_admin")) {
            List<VendorAccessMembershipResponse> memberships = vendorAccessClient.listAccessibleVendorsByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorAccessMembershipResponse membership : memberships) {
                if (membership != null && membership.vendorId() != null) {
                    vendorIds.add(membership.vendorId());
                }
            }
        }
        if (roles.contains("vendor_staff")) {
            List<VendorStaffAccessLookupResponse> vendorAccessRows = accessClient.listVendorStaffAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorStaffAccessLookupResponse row : vendorAccessRows) {
                if (row == null || row.vendorId() == null || !row.active()) {
                    continue;
                }
                Set<String> perms = row.permissions() == null ? Set.of() : row.permissions();
                if (perms.contains(VENDOR_PRODUCTS_MANAGE)) {
                    vendorIds.add(row.vendorId());
                }
            }
        }
        if (!vendorIds.isEmpty()) {
            return new ActorScope(false, false, false, Set.copyOf(vendorIds));
        }
        throw new UnauthorizedException("Caller does not have product admin access");
    }

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        return userSub.trim();
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : rolesHeader.split(",")) {
            if (role != null && !role.isBlank()) {
                roles.add(role.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(roles);
    }

    private UpsertProductRequest copyRequestWithVendorId(UpsertProductRequest request, UUID vendorId) {
        return new UpsertProductRequest(
                request.name(),
                request.slug(),
                request.shortDescription(),
                request.description(),
                request.brandName(),
                request.images(),
                request.regularPrice(),
                request.discountedPrice(),
                vendorId,
                request.categories(),
                request.productType(),
                request.variations(),
                request.sku(),
                request.active(),
                request.weightGrams(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                request.metaTitle(),
                request.metaDescription(),
                request.specifications(),
                request.digital(),
                request.bundledProductIds()
        );
    }

    private record ActorScope(
            boolean superAdmin,
            boolean platformProductsManage,
            boolean platformCategoriesManage,
            Set<UUID> vendorProductVendorIds
    ) {
    }
}
