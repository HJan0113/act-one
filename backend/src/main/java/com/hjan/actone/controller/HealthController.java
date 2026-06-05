package com.hjan.actone.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "act-one-backend", Instant.now().toString());
    }

    public record HealthResponse(String status, String service, String timestamp) {
    }
}

