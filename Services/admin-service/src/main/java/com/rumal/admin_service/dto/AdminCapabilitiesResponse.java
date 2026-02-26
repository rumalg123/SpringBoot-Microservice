package com.rumal.admin_service.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record AdminCapabilitiesResponse(
        boolean superAdmin,
        boolean platformStaff,
        boolean vendorAdmin,
        boolean vendorStaff,
        Set<String> platformPermissions,
        List<Map<String, Object>> vendorMemberships,
        List<Map<String, Object>> vendorStaffAccess,
        boolean canManageAdminOrders,
        boolean canManageAdminProducts,
        boolean canManageAdminCategories,
        boolean canManageAdminPosters,
        boolean canManageAdminVendors,
        boolean canManageAdminPromotions,
        boolean canManageAdminReviews
) {
}
