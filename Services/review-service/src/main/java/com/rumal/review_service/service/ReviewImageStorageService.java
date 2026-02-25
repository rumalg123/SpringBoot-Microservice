package com.rumal.review_service.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface ReviewImageStorageService {
    List<String> uploadImages(List<MultipartFile> files);
    void deleteImages(List<String> keys);
    StoredImage getImage(String key);
}
