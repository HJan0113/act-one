package com.hjan.actone.controller;

import java.time.Instant;

import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<HealthResponse> health() {
        return Results.success(new HealthResponse("ok", "act-one-backend", Instant.now().toString()));
    }

    public record HealthResponse(String status, String service, String timestamp) {
    }
}
