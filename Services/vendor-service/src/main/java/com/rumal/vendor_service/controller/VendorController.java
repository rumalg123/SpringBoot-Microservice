package com.rumal.vendor_service.controller;

import com.rumal.vendor_service.dto.SlugAvailabilityResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    public Page<VendorResponse> listActive(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return vendorService.listPublicActive(category, pageable);
    }

    @GetMapping("/slug-available")
    public SlugAvailabilityResponse slugAvailable(
            @RequestParam String slug,
            @RequestParam(required = false) UUID excludeId
    ) {
        return new SlugAvailabilityResponse(vendorService.isSlugAvailable(slug, excludeId));
    }

    @GetMapping("/{idOrSlug}")
    public VendorResponse getByIdOrSlug(@PathVariable String idOrSlug) {
        return vendorService.getByIdOrSlug(idOrSlug);
    }
}
