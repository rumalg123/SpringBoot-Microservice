package com.rumal.poster_service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PosterImageStorageService {
    List<String> generateImageNames(List<String> fileNames);
    List<String> uploadImages(List<MultipartFile> files, List<String> keys);
    StoredImage getImage(String key);
}
