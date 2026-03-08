package com.rumal.personalization_service.controller;

import com.rumal.personalization_service.dto.RecordEventRequest;
import com.rumal.personalization_service.service.EventIngestionStreamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

    private final EventIngestionStreamService eventIngestionStreamService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recordEvents(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Size(min = 1, max = 50) @RequestBody List<@Valid RecordEventRequest> requests
    ) {
        UUID userId = parseUuidOrNull(userSub);
        if (userId == null && (sessionId == null || sessionId.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either X-User-Sub or X-Session-Id header is required");
        }
        eventIngestionStreamService.enqueue(userId, sessionId, requests);
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
