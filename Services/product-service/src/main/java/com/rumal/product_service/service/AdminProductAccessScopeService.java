package com.rumal.product_service.service;

import com.rumal.product_service.client.VendorAccessClient;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.dto.VendorAccessMembershipResponse;
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

    private final VendorAccessClient vendorAccessClient;
    private final ProductRepository productRepository;

    public UUID resolveScopedVendorFilter(String userSub, String userRolesHeader, UUID requestedVendorId, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin()) {
            return requestedVendorId;
        }
        return resolveVendorIdForVendorAdmin(scope.vendorIds(), requestedVendorId);
    }

    public UpsertProductRequest scopeCreateRequest(String userSub, String userRolesHeader, UpsertProductRequest request, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin()) {
            return request;
        }
        UUID resolvedVendorId = request.productType() == com.rumal.product_service.entity.ProductType.VARIATION
                ? request.vendorId()
                : resolveVendorIdForVendorAdmin(scope.vendorIds(), request.vendorId());
        return copyRequestWithVendorId(request, resolvedVendorId);
    }

    public UpsertProductRequest scopeUpdateRequest(UUID productId, String userSub, String userRolesHeader, UpsertProductRequest request, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin()) {
            return request;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        assertVendorAdminCanManageProduct(scope.vendorIds(), product);

        UUID vendorId = request.vendorId();
        if (product.getProductType() != com.rumal.product_service.entity.ProductType.VARIATION) {
            UUID existingVendorId = product.getVendorId();
            if (existingVendorId == null) {
                throw new ValidationException("Existing product vendorId is missing");
            }
            if (vendorId != null && !existingVendorId.equals(vendorId)) {
                throw new UnauthorizedException("vendor_admin cannot reassign product to another vendor");
            }
            vendorId = existingVendorId;
        }

        return copyRequestWithVendorId(request, vendorId);
    }

    public void assertCanManageProduct(UUID productId, String userSub, String userRolesHeader, String internalAuth) {
        ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
        if (scope.superAdmin()) {
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        assertVendorAdminCanManageProduct(scope.vendorIds(), product);
    }

    private void assertVendorAdminCanManageProduct(Set<UUID> vendorIds, Product product) {
        if (product.getVendorId() == null || !vendorIds.contains(product.getVendorId())) {
            throw new UnauthorizedException("vendor_admin cannot manage products of another vendor");
        }
    }

    private UUID resolveVendorIdForVendorAdmin(Set<UUID> vendorIds, UUID requestedVendorId) {
        if (vendorIds.isEmpty()) {
            throw new UnauthorizedException("No active vendor membership found for vendor_admin user");
        }
        if (requestedVendorId != null) {
            if (!vendorIds.contains(requestedVendorId)) {
                throw new UnauthorizedException("vendor_admin cannot use another vendorId");
            }
            return requestedVendorId;
        }
        if (vendorIds.size() == 1) {
            return vendorIds.iterator().next();
        }
        throw new ValidationException("vendorId is required when vendor_admin belongs to multiple vendors");
    }

    private ActorScope resolveScope(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return new ActorScope(true, Set.of());
        }
        if (!roles.contains("vendor_admin")) {
            throw new UnauthorizedException("Caller does not have product admin access");
        }
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }

        List<VendorAccessMembershipResponse> memberships = vendorAccessClient.listAccessibleVendorsByKeycloakUser(userSub.trim(), internalAuth);
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (VendorAccessMembershipResponse membership : memberships) {
            if (membership == null || membership.vendorId() == null) {
                continue;
            }
            vendorIds.add(membership.vendorId());
        }
        return new ActorScope(false, Set.copyOf(vendorIds));
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
                request.images(),
                request.regularPrice(),
                request.discountedPrice(),
                vendorId,
                request.categories(),
                request.productType(),
                request.variations(),
                request.sku(),
                request.active()
        );
    }

    private record ActorScope(boolean superAdmin, Set<UUID> vendorIds) {
    }
}
