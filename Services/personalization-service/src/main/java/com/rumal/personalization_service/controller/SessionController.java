package com.rumal.personalization_service.controller;

import com.rumal.personalization_service.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/personalization/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mergeSession(
            @RequestHeader("X-User-Sub") String userSub,
            @RequestHeader("X-Session-Id") String sessionId
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(userSub);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-User-Sub header");
        }
        if (sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Session-Id header must not be blank");
        }
        sessionService.mergeSession(userId, sessionId);
    }
}
