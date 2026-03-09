package com.rumal.access_service.controller;

import com.rumal.access_service.dto.ActiveSessionResponse;
import com.rumal.access_service.exception.UnauthorizedException;
import com.rumal.access_service.security.InternalRequestVerifier;
import com.rumal.access_service.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/me/sessions")
@RequiredArgsConstructor
public class SelfSessionController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";
    private static final String USER_SUB_HEADER = "X-User-Sub";

    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @GetMapping
    public Page<ActiveSessionResponse> listMine(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = USER_SUB_HEADER, required = false) String userSub,
            @PageableDefault(size = 25, sort = "lastActivityAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listSessionsByKeycloakId(requireUserSub(userSub), pageable);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeMine(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = USER_SUB_HEADER, required = false) String userSub,
            @PathVariable UUID sessionId
    ) {
        internalRequestVerifier.verify(internalAuth);
        accessService.revokeOwnSession(sessionId, requireUserSub(userSub));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllMine(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = USER_SUB_HEADER, required = false) String userSub
    ) {
        internalRequestVerifier.verify(internalAuth);
        accessService.revokeAllSessions(requireUserSub(userSub));
    }

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        return userSub.trim();
    }
}
