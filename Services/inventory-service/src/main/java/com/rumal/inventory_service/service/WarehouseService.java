package com.rumal.inventory_service.service;

import com.rumal.inventory_service.dto.WarehouseCreateRequest;
import com.rumal.inventory_service.dto.WarehouseResponse;
import com.rumal.inventory_service.dto.WarehouseUpdateRequest;
import com.rumal.inventory_service.entity.Warehouse;
import com.rumal.inventory_service.entity.WarehouseType;
import com.rumal.inventory_service.exception.ResourceNotFoundException;
import com.rumal.inventory_service.exception.ValidationException;
import com.rumal.inventory_service.repo.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> list(Pageable pageable, UUID vendorId, WarehouseType warehouseType, Boolean active) {
        return warehouseRepository.findFiltered(vendorId, warehouseType, active, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest request) {
        WarehouseType type = parseWarehouseType(request.warehouseType());

        Warehouse warehouse = Warehouse.builder()
                .name(request.name())
                .description(request.description())
                .vendorId(request.vendorId())
                .warehouseType(type)
                .addressLine1(request.addressLine1())
                .addressLine2(request.addressLine2())
                .city(request.city())
                .state(request.state())
                .postalCode(request.postalCode())
                .countryCode(request.countryCode())
                .contactName(request.contactName())
                .contactPhone(request.contactPhone())
                .contactEmail(request.contactEmail())
                .active(true)
                .build();

        return toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseUpdateRequest request) {
        Warehouse warehouse = findById(id);

        if (request.name() != null) warehouse.setName(request.name());
        if (request.description() != null) warehouse.setDescription(request.description());
        if (request.addressLine1() != null) warehouse.setAddressLine1(request.addressLine1());
        if (request.addressLine2() != null) warehouse.setAddressLine2(request.addressLine2());
        if (request.city() != null) warehouse.setCity(request.city());
        if (request.state() != null) warehouse.setState(request.state());
        if (request.postalCode() != null) warehouse.setPostalCode(request.postalCode());
        if (request.countryCode() != null) warehouse.setCountryCode(request.countryCode());
        if (request.contactName() != null) warehouse.setContactName(request.contactName());
        if (request.contactPhone() != null) warehouse.setContactPhone(request.contactPhone());
        if (request.contactEmail() != null) warehouse.setContactEmail(request.contactEmail());

        return toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse updateStatus(UUID id, boolean active) {
        Warehouse warehouse = findById(id);
        warehouse.setActive(active);
        return toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> listByVendor(UUID vendorId, Pageable pageable) {
        return warehouseRepository.findByVendorIdAndActive(vendorId, true, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public WarehouseResponse createForVendor(UUID vendorId, WarehouseCreateRequest request) {
        WarehouseCreateRequest vendorRequest = new WarehouseCreateRequest(
                request.name(), request.description(), vendorId, "VENDOR_OWNED",
                request.addressLine1(), request.addressLine2(), request.city(),
                request.state(), request.postalCode(), request.countryCode(),
                request.contactName(), request.contactPhone(), request.contactEmail()
        );
        return create(vendorRequest);
    }

    @Transactional
    public WarehouseResponse updateForVendor(UUID vendorId, UUID warehouseId, WarehouseUpdateRequest request) {
        Warehouse warehouse = findById(warehouseId);
        if (!vendorId.equals(warehouse.getVendorId())) {
            throw new ResourceNotFoundException("Warehouse not found: " + warehouseId);
        }
        return update(warehouseId, request);
    }

    public Warehouse findById(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + id));
    }

    private WarehouseType parseWarehouseType(String type) {
        try {
            return WarehouseType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid warehouse type: " + type);
        }
    }

    private WarehouseResponse toResponse(Warehouse w) {
        return new WarehouseResponse(
                w.getId(), w.getName(), w.getDescription(), w.getVendorId(),
                w.getWarehouseType().name(), w.getAddressLine1(), w.getAddressLine2(),
                w.getCity(), w.getState(), w.getPostalCode(), w.getCountryCode(),
                w.getContactName(), w.getContactPhone(), w.getContactEmail(),
                w.isActive(), w.getCreatedAt(), w.getUpdatedAt()
        );
    }
}
