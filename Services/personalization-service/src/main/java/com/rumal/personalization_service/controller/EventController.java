package com.rumal.personalization_service.controller;

import com.rumal.personalization_service.dto.RecordEventRequest;
import com.rumal.personalization_service.security.InternalRequestVerifier;
import com.rumal.personalization_service.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personalization/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recordEvents(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Valid @RequestBody List<RecordEventRequest> requests
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID userId = parseUuidOrNull(userSub);
        if (userId == null && (sessionId == null || sessionId.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either X-User-Sub or X-Session-Id header is required");
        }
        for (RecordEventRequest request : requests) {
            eventService.recordEvent(userId, sessionId, request);
        }
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
