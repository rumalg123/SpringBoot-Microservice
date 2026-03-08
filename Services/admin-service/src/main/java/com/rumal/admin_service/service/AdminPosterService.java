package com.rumal.admin_service.service;

import com.rumal.admin_service.client.PosterClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPosterService {

    private final PosterClient posterClient;

    public List<Map<String, Object>> listAll(String internalAuth, String userSub, String userRoles) {
        return posterClient.listAll(internalAuth, userSub, userRoles);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth, String userSub, String userRoles) {
        return posterClient.listDeleted(internalAuth, userSub, userRoles);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return posterClient.create(request, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return posterClient.update(id, request, internalAuth, userSub, userRoles);
    }

    public void delete(UUID id, String internalAuth, String userSub, String userRoles) {
        posterClient.delete(id, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> restore(UUID id, String internalAuth, String userSub, String userRoles) {
        return posterClient.restore(id, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> prepareImageUploads(Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return posterClient.prepareImageUploads(request, internalAuth, userSub, userRoles);
    }
}
