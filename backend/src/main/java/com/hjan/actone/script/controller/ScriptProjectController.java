package com.hjan.actone.script.controller;

import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.web.Results;
import com.hjan.actone.script.model.ScriptProjectCreateRequest;
import com.hjan.actone.script.model.ScriptProjectCreateResponse;
import com.hjan.actone.script.model.ScriptProjectListItemResponse;
import com.hjan.actone.script.service.ScriptProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/my")
    public Result<List<ScriptProjectListItemResponse>> listMine() {
        return Results.success(scriptProjectService.listMine());
    }

    @DeleteMapping("/{projectId}")
    public Result<Void> delete(@PathVariable String projectId) {
        scriptProjectService.delete(projectId);
        return Results.success();
    }
}
