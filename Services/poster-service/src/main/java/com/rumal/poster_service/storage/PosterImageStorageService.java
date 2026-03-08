package com.rumal.poster_service.storage;

import com.rumal.poster_service.dto.PosterImagePrepareUploadRequest;
import com.rumal.poster_service.dto.PosterImagePrepareUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PosterImageStorageService {
    List<String> generateImageNames(List<String> fileNames);
    List<String> uploadImages(List<MultipartFile> files, List<String> keys);
    PosterImagePrepareUploadResponse prepareUploads(PosterImagePrepareUploadRequest request);
    String assertImageReady(String key);
    StoredImage getImage(String key);
}
