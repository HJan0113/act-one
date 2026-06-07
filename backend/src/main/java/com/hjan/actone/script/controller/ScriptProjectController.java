package com.hjan.actone.script.controller;

import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.web.Results;
import com.hjan.actone.script.model.ScriptProjectCreateRequest;
import com.hjan.actone.script.model.ScriptProjectCreateResponse;
import com.hjan.actone.script.service.ScriptProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/script/projects")
public class ScriptProjectController {

    private final ScriptProjectService scriptProjectService;

    public ScriptProjectController(ScriptProjectService scriptProjectService) {
        this.scriptProjectService = scriptProjectService;
    }

    @PostMapping("/from-text")
    public Result<ScriptProjectCreateResponse> createFromText(@Valid @RequestBody ScriptProjectCreateRequest request) {
        return Results.success(scriptProjectService.createFromText(request));
    }
}

