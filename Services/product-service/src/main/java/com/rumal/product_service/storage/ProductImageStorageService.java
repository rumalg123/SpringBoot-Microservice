package com.rumal.product_service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageStorageService {
    List<String> generateImageNames(List<String> fileNames);
    List<String> uploadImages(List<MultipartFile> files, List<String> keys);
}
