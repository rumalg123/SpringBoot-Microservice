package com.rumal.admin_service.service;

import com.rumal.admin_service.client.PosterClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPosterService {

    private final PosterClient posterClient;

    public List<Map<String, Object>> listAll(String internalAuth) {
        return posterClient.listAll(internalAuth);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth) {
        return posterClient.listDeleted(internalAuth);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth) {
        return posterClient.create(request, internalAuth);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth) {
        return posterClient.update(id, request, internalAuth);
    }

    public void delete(UUID id, String internalAuth) {
        posterClient.delete(id, internalAuth);
    }

    public Map<String, Object> restore(UUID id, String internalAuth) {
        return posterClient.restore(id, internalAuth);
    }

    public Map<String, Object> generateImageNames(Map<String, Object> request, String internalAuth) {
        return posterClient.generateImageNames(request, internalAuth);
    }

    public Map<String, Object> uploadImages(List<MultipartFile> files, List<String> keys, String internalAuth) {
        return posterClient.uploadImages(files, keys, internalAuth);
    }
}
