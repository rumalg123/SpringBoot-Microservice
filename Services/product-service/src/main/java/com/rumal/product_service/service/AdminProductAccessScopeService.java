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

    public ProductMutationAuthority resolveAuthority(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return new ProductMutationAuthority(true, true, true, Set.of(), Set.of(), Set.of());
        }

        boolean platformProductsManage = false;
        boolean platformCategoriesManage = false;
        if (roles.contains("platform_staff")) {
            PlatformAccessLookupResponse platformAccess = accessClient.getPlatformAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
            platformProductsManage = platformAccess.active() && permissions.contains(PLATFORM_PRODUCTS_MANAGE);
            platformCategoriesManage = platformAccess.active() && permissions.contains(PLATFORM_CATEGORIES_MANAGE);
            if (platformProductsManage || platformCategoriesManage) {
                return new ProductMutationAuthority(
                        false,
                        platformProductsManage,
                        platformCategoriesManage,
                        Set.of(),
                        Set.of(),
                        Set.of()
                );
            }
        }

        Set<UUID> vendorAdminVendorIds = new LinkedHashSet<>();
        if (roles.contains("vendor_admin")) {
            List<VendorAccessMembershipResponse> memberships = vendorAccessClient.listAccessibleVendorsByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorAccessMembershipResponse membership : memberships) {
                if (membership != null && membership.vendorId() != null) {
                    vendorAdminVendorIds.add(membership.vendorId());
                }
            }
        }

        Set<UUID> vendorStaffVendorIds = new LinkedHashSet<>();
        if (roles.contains("vendor_staff")) {
            List<VendorStaffAccessLookupResponse> vendorAccessRows = accessClient.listVendorStaffAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorStaffAccessLookupResponse row : vendorAccessRows) {
                if (row == null || row.vendorId() == null || !row.active()) {
                    continue;
                }
                Set<String> permissions = row.permissions() == null ? Set.of() : row.permissions();
                if (permissions.contains(VENDOR_PRODUCTS_MANAGE)) {
                    vendorStaffVendorIds.add(row.vendorId());
                }
            }
        }

        Set<UUID> manageableVendorIds = new LinkedHashSet<>(vendorAdminVendorIds);
        manageableVendorIds.addAll(vendorStaffVendorIds);
        if (!manageableVendorIds.isEmpty()) {
            return new ProductMutationAuthority(
                    false,
                    false,
                    false,
                    Set.copyOf(vendorAdminVendorIds),
                    Set.copyOf(vendorStaffVendorIds),
                    Set.copyOf(manageableVendorIds)
            );
        }

        throw new UnauthorizedException("Caller does not have product admin access");
    }

    public UUID resolveScopedVendorFilter(String userSub, String userRolesHeader, UUID requestedVendorId, String internalAuth) {
        return resolveScopedVendorFilter(resolveAuthority(userSub, userRolesHeader, internalAuth), requestedVendorId);
    }

    public UUID resolveScopedVendorFilter(ProductMutationAuthority authority, UUID requestedVendorId) {
        if (authority.isPlatformPrivileged()) {
            return requestedVendorId;
        }
        return resolveVendorIdForVendorScopedActor(authority.manageableVendorIds(), requestedVendorId);
    }

    public UpsertProductRequest scopeCreateRequest(String userSub, String userRolesHeader, UpsertProductRequest request, String internalAuth) {
        return scopeCreateRequest(resolveAuthority(userSub, userRolesHeader, internalAuth), request);
    }

    public UpsertProductRequest scopeCreateRequest(ProductMutationAuthority authority, UpsertProductRequest request) {
        if (authority.isPlatformPrivileged()) {
            return request;
        }
        UUID resolvedVendorId = resolveVendorIdForVendorScopedActor(authority.manageableVendorIds(), request.vendorId());
        return copyRequestWithVendorId(request, resolvedVendorId);
    }

    public UpsertProductRequest scopeVariationCreateRequest(
            String userSub,
            String userRolesHeader,
            UUID parentId,
            UpsertProductRequest request,
            String internalAuth
    ) {
        return scopeVariationCreateRequest(resolveAuthority(userSub, userRolesHeader, internalAuth), parentId, request);
    }

    public UpsertProductRequest scopeVariationCreateRequest(ProductMutationAuthority authority, UUID parentId, UpsertProductRequest request) {
        Product parent = productRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + parentId));
        assertCanManageProduct(authority, parent);
        UUID parentVendorId = requireProductVendorId(parent);
        if (request.vendorId() != null && !parentVendorId.equals(request.vendorId())) {
            throw new UnauthorizedException("Variation vendorId must match parent product vendorId");
        }
        return copyRequestWithVendorId(request, parentVendorId);
    }

    public UpsertProductRequest scopeUpdateRequest(UUID productId, String userSub, String userRolesHeader, UpsertProductRequest request, String internalAuth) {
        return scopeUpdateRequest(resolveAuthority(userSub, userRolesHeader, internalAuth), productId, request);
    }

    public UpsertProductRequest scopeUpdateRequest(ProductMutationAuthority authority, UUID productId, UpsertProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        assertCanManageProduct(authority, product);
        UUID existingVendorId = requireProductVendorId(product);
        if (request.vendorId() != null && !existingVendorId.equals(request.vendorId())) {
            throw new UnauthorizedException("Product vendorId is immutable after creation");
        }
        return copyRequestWithVendorId(request, existingVendorId);
    }

    public void assertCanManageProduct(UUID productId, String userSub, String userRolesHeader, String internalAuth) {
        assertCanManageProduct(resolveAuthority(userSub, userRolesHeader, internalAuth), productId);
    }

    public void assertCanManageProduct(ProductMutationAuthority authority, UUID productId) {
        assertCanManageProductOperations(authority);
        if (authority.isPlatformPrivileged()) {
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        assertCanManageProduct(authority, product);
    }

    public void assertCanManageProductOperations(String userSub, String userRolesHeader, String internalAuth) {
        assertCanManageProductOperations(resolveAuthority(userSub, userRolesHeader, internalAuth));
    }

    public void assertCanManageProductOperations(ProductMutationAuthority authority) {
        if (authority.isPlatformPrivileged()) {
            return;
        }
        if (authority.manageableVendorIds().isEmpty()) {
            throw new UnauthorizedException("Caller does not have product management access");
        }
    }

    public Set<UUID> resolveAllowedVendorIds(String userSub, String userRolesHeader, String internalAuth) {
        return resolveAllowedVendorIds(resolveAuthority(userSub, userRolesHeader, internalAuth));
    }

    public Set<UUID> resolveAllowedVendorIds(ProductMutationAuthority authority) {
        if (authority.isPlatformPrivileged()) {
            return null;
        }
        if (authority.manageableVendorIds().isEmpty()) {
            throw new UnauthorizedException("Caller does not have product management access");
        }
        return authority.manageableVendorIds();
    }

    public void assertPlatformLevelProductManagement(String userSub, String userRolesHeader, String internalAuth) {
        assertPlatformLevelProductManagement(resolveAuthority(userSub, userRolesHeader, internalAuth));
    }

    public void assertPlatformLevelProductManagement(ProductMutationAuthority authority) {
        if (authority.isPlatformPrivileged()) {
            return;
        }
        throw new UnauthorizedException("Only platform-level admins can perform this operation");
    }

    public void assertCanManageCategories(String userSub, String userRolesHeader, String internalAuth) {
        assertCanManageCategories(resolveAuthority(userSub, userRolesHeader, internalAuth));
    }

    public void assertCanManageCategories(ProductMutationAuthority authority) {
        if (authority.superAdmin() || authority.platformCategoriesManage()) {
            return;
        }
        throw new UnauthorizedException("Caller does not have category management access");
    }

    public void assertCanSubmitProductForReview(String userSub, String userRolesHeader, String internalAuth, UUID productId) {
        assertCanSubmitProductForReview(resolveAuthority(userSub, userRolesHeader, internalAuth), productId);
    }

    public void assertCanSubmitProductForReview(ProductMutationAuthority authority, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        if (authority.isPlatformPrivileged()) {
            return;
        }
        UUID vendorId = requireProductVendorId(product);
        if (authority.hasVendorAdminAccess(vendorId)) {
            return;
        }
        throw new UnauthorizedException("Only vendor_admin or platform staff can submit a product for review");
    }

    public ProductWorkflowActor resolveWorkflowActorForVendor(ProductMutationAuthority authority, UUID vendorId) {
        return authority.workflowActorForVendor(vendorId);
    }

    public ProductWorkflowActor resolveWorkflowActorForProduct(ProductMutationAuthority authority, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return authority.workflowActorForVendor(requireProductVendorId(product));
    }

    private void assertCanManageProduct(ProductMutationAuthority authority, Product product) {
        if (authority.isPlatformPrivileged()) {
            return;
        }
        UUID vendorId = requireProductVendorId(product);
        if (!authority.canManageVendor(vendorId)) {
            throw new UnauthorizedException("Vendor-scoped user cannot manage products of another vendor");
        }
    }

    private UUID requireProductVendorId(Product product) {
        UUID vendorId = product.getVendorId();
        if (vendorId == null) {
            throw new ValidationException("Existing product vendorId is missing");
        }
        return vendorId;
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
            String normalized = normalizeRole(role);
            if (!normalized.isEmpty()) {
                roles.add(normalized);
            }
        }
        return Set.copyOf(roles);
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("role_")) {
            normalized = normalized.substring("role_".length());
        } else if (normalized.startsWith("role-")) {
            normalized = normalized.substring("role-".length());
        } else if (normalized.startsWith("role:")) {
            normalized = normalized.substring("role:".length());
        }
        return normalized.replace('-', '_').replace(' ', '_');
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

    public record ProductMutationAuthority(
            boolean superAdmin,
            boolean platformProductsManage,
            boolean platformCategoriesManage,
            Set<UUID> vendorAdminVendorIds,
            Set<UUID> vendorStaffVendorIds,
            Set<UUID> manageableVendorIds
    ) {
        public boolean isPlatformPrivileged() {
            return superAdmin || platformProductsManage;
        }

        public boolean canManageVendor(UUID vendorId) {
            return vendorId != null && manageableVendorIds.contains(vendorId);
        }

        public boolean hasVendorAdminAccess(UUID vendorId) {
            return vendorId != null && vendorAdminVendorIds.contains(vendorId);
        }

        public ProductWorkflowActor workflowActorForVendor(UUID vendorId) {
            if (isPlatformPrivileged()) {
                return ProductWorkflowActor.PLATFORM;
            }
            if (hasVendorAdminAccess(vendorId)) {
                return ProductWorkflowActor.VENDOR_ADMIN;
            }
            if (vendorId != null && vendorStaffVendorIds.contains(vendorId)) {
                return ProductWorkflowActor.VENDOR_STAFF;
            }
            throw new UnauthorizedException("Caller does not have authority over vendor " + vendorId);
        }
    }
}
