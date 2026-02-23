package com.rumal.product_service.controller;

import com.rumal.product_service.dto.CategoryResponse;
import com.rumal.product_service.dto.UpsertCategoryRequest;
import com.rumal.product_service.security.InternalRequestVerifier;
import com.rumal.product_service.service.AdminProductAccessScopeService;
import com.rumal.product_service.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final AdminProductAccessScopeService adminProductAccessScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public List<CategoryResponse> listActive(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageCategories(userSub, userRoles, internalAuth);
        return categoryService.listActive(null, null);
    }

    @GetMapping("/deleted")
    public List<CategoryResponse> listDeleted(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageCategories(userSub, userRoles, internalAuth);
        return categoryService.listDeleted();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody UpsertCategoryRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageCategories(userSub, userRoles, internalAuth);
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertCategoryRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageCategories(userSub, userRoles, internalAuth);
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageCategories(userSub, userRoles, internalAuth);
        categoryService.softDelete(id);
    }

    @PostMapping("/{id}/restore")
    public CategoryResponse restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageCategories(userSub, userRoles, internalAuth);
        return categoryService.restore(id);
    }
}
