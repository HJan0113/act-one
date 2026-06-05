package com.hjan.actone.script.controller;

import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.web.Results;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.model.ScriptYamlGenerateResponse;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/script/drafts")
public class ScriptYamlDraftController {

    private final ScriptYamlDraftService scriptYamlDraftService;

    public ScriptYamlDraftController(ScriptYamlDraftService scriptYamlDraftService) {
        this.scriptYamlDraftService = scriptYamlDraftService;
    }

    @PostMapping("/yaml/from-text")
    public Result<ScriptYamlGenerateResponse> generateYamlFromText(
            @Valid @RequestBody ScriptYamlGenerateRequest request) {
        return Results.success(scriptYamlDraftService.generateFromText(request));
    }
}
