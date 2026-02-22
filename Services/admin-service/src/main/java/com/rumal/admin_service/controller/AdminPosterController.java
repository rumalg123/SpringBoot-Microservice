package com.rumal.admin_service.controller;

import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminPosterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/posters")
@RequiredArgsConstructor
public class AdminPosterController {

    private final AdminPosterService adminPosterService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public List<Map<String, Object>> listAll(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminPosterService.listAll(internalAuth);
    }

    @GetMapping("/deleted")
    public List<Map<String, Object>> listDeleted(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminPosterService.listDeleted(internalAuth);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestBody Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminPosterService.create(request, internalAuth);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminPosterService.update(id, request, internalAuth);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminPosterService.delete(id, internalAuth);
    }

    @PostMapping("/{id}/restore")
    public Map<String, Object> restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminPosterService.restore(id, internalAuth);
    }

    @PostMapping("/images/names")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> generateImageNames(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestBody Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminPosterService.generateImageNames(request, internalAuth);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> uploadImages(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "keys", required = false) List<String> keys
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminPosterService.uploadImages(files, keys, internalAuth);
    }
}
