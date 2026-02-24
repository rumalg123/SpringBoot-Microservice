package com.rumal.admin_service.service;

import com.rumal.admin_service.dto.FeatureFlagResponse;
import com.rumal.admin_service.dto.UpsertFeatureFlagRequest;
import com.rumal.admin_service.entity.FeatureFlag;
import com.rumal.admin_service.repo.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository flagRepository;

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> listAll() {
        return flagRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FeatureFlagResponse getByKey(String key) {
        return flagRepository.findByFlagKey(key)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature flag not found: " + key));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String key) {
        return flagRepository.findByFlagKey(key)
                .map(FeatureFlag::isEnabled)
                .orElse(false);
    }

    @Transactional
    public FeatureFlagResponse upsert(UpsertFeatureFlagRequest request) {
        FeatureFlag flag = flagRepository.findByFlagKey(request.flagKey())
                .orElseGet(() -> FeatureFlag.builder().flagKey(request.flagKey()).build());
        if (request.description() != null) flag.setDescription(request.description());
        if (request.enabled() != null) flag.setEnabled(request.enabled());
        if (request.enabledForRoles() != null) flag.setEnabledForRoles(request.enabledForRoles());
        if (request.rolloutPercentage() != null) flag.setRolloutPercentage(request.rolloutPercentage());
        return toResponse(flagRepository.save(flag));
    }

    @Transactional
    public void delete(UUID id) {
        flagRepository.deleteById(id);
    }

    private FeatureFlagResponse toResponse(FeatureFlag f) {
        return new FeatureFlagResponse(f.getId(), f.getFlagKey(), f.getDescription(),
                f.isEnabled(), f.getEnabledForRoles(), f.getRolloutPercentage(),
                f.getCreatedAt(), f.getUpdatedAt());
    }
}
