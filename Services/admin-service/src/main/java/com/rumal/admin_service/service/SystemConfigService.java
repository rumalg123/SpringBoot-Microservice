package com.rumal.admin_service.service;

import com.rumal.admin_service.dto.SystemConfigResponse;
import com.rumal.admin_service.dto.UpsertSystemConfigRequest;
import com.rumal.admin_service.entity.SystemConfig;
import com.rumal.admin_service.exception.DownstreamHttpException;
import com.rumal.admin_service.repo.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    @Transactional(readOnly = true)
    public List<SystemConfigResponse> listAll() {
        return configRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SystemConfigResponse getByKey(String key) {
        return configRepository.findByConfigKey(key)
                .map(this::toResponse)
                .orElseThrow(() -> new DownstreamHttpException(404, "Config key not found: " + key));
    }

    @Transactional
    public SystemConfigResponse upsert(UpsertSystemConfigRequest request) {
        SystemConfig config = configRepository.findByConfigKey(request.configKey())
                .orElseGet(() -> SystemConfig.builder().configKey(request.configKey()).build());
        if (request.configValue() != null) config.setConfigValue(request.configValue());
        if (request.description() != null) config.setDescription(request.description());
        if (request.valueType() != null) config.setValueType(request.valueType());
        if (request.active() != null) config.setActive(request.active());
        return toResponse(configRepository.save(config));
    }

    @Transactional
    public void delete(UUID id) {
        configRepository.deleteById(id);
    }

    private SystemConfigResponse toResponse(SystemConfig c) {
        return new SystemConfigResponse(c.getId(), c.getConfigKey(), c.getConfigValue(),
                c.getDescription(), c.getValueType(), c.isActive(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
