package com.rumal.access_service.controller;

import com.rumal.access_service.dto.AccessChangeAuditPageResponse;
import com.rumal.access_service.security.InternalRequestVerifier;
import com.rumal.access_service.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/access-audit")
@RequiredArgsConstructor
public class AdminAccessAuditController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";

    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @GetMapping
    public AccessChangeAuditPageResponse list(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorQuery,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer limit
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listAccessAudit(targetType, targetId, vendorId, action, actorQuery, from, to, page, size, limit);
    }
}
