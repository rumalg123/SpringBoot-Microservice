package com.rumal.inventory_service.controller;

import com.rumal.inventory_service.dto.WarehouseCreateRequest;
import com.rumal.inventory_service.dto.WarehouseResponse;
import com.rumal.inventory_service.dto.WarehouseStatusRequest;
import com.rumal.inventory_service.dto.WarehouseUpdateRequest;
import com.rumal.inventory_service.entity.WarehouseType;
import com.rumal.inventory_service.security.InternalRequestVerifier;
import com.rumal.inventory_service.service.AdminInventoryAccessScopeService;
import com.rumal.inventory_service.service.AdminInventoryAccessScopeService.AdminActorScope;
import com.rumal.inventory_service.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/inventory/warehouses")
@RequiredArgsConstructor
public class AdminWarehouseController {

    private final WarehouseService warehouseService;
    private final AdminInventoryAccessScopeService adminAccessScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<WarehouseResponse> list(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) WarehouseType warehouseType,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        UUID scopedVendorId = adminAccessScopeService.resolveScopedVendorFilter(scope, vendorId);
        return warehouseService.list(pageable, scopedVendorId, warehouseType, active);
    }

    @GetMapping("/{id}")
    public WarehouseResponse get(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        WarehouseResponse warehouse = warehouseService.get(id);
        adminAccessScopeService.assertCanManageWarehouse(scope, warehouse.vendorId());
        return warehouse;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody WarehouseCreateRequest request
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    public WarehouseResponse update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseUpdateRequest request
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        WarehouseResponse existing = warehouseService.get(id);
        adminAccessScopeService.assertCanManageWarehouse(scope, existing.vendorId());
        return warehouseService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public WarehouseResponse updateStatus(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseStatusRequest request
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        WarehouseResponse existing = warehouseService.get(id);
        adminAccessScopeService.assertCanManageWarehouse(scope, existing.vendorId());
        return warehouseService.updateStatus(id, request.active());
    }

    private AdminActorScope resolveScope(String internalAuth, String userSub, String userRoles) {
        internalRequestVerifier.verify(internalAuth);
        return adminAccessScopeService.resolveActorScope(userSub, userRoles, internalAuth);
    }
}
