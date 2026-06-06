package com.hjan.actone.script.controller;

import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.web.Results;
import com.hjan.actone.script.model.ScriptDraftDetailResponse;
import com.hjan.actone.script.model.ScriptDraftListItemResponse;
import com.hjan.actone.script.model.ScriptDraftSaveRequest;
import com.hjan.actone.script.model.ScriptDraftSaveResponse;
import com.hjan.actone.script.model.ScriptConversionTaskResponse;
import com.hjan.actone.script.model.ScriptDialogueVoiceRewriteRequest;
import com.hjan.actone.script.model.ScriptDialogueVoiceRewriteResponse;
import com.hjan.actone.script.model.ScriptSceneRefineRequest;
import com.hjan.actone.script.model.ScriptSceneRefineResponse;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.model.ScriptYamlGenerateResponse;
import com.hjan.actone.script.service.ScriptDraftService;
import com.hjan.actone.script.service.ScriptSceneRefinementService;
import com.hjan.actone.script.service.ScriptYamlGenerationService;
import com.hjan.actone.script.service.ScriptYamlStreamService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/script/drafts")
public class ScriptYamlDraftController {

    private final ScriptYamlGenerationService scriptYamlGenerationService;
    private final ScriptYamlStreamService scriptYamlStreamService;
    private final ScriptDraftService scriptDraftService;
    private final ScriptSceneRefinementService scriptSceneRefinementService;

    public ScriptYamlDraftController(ScriptYamlGenerationService scriptYamlGenerationService,
                                     ScriptYamlStreamService scriptYamlStreamService,
                                     ScriptDraftService scriptDraftService,
                                     ScriptSceneRefinementService scriptSceneRefinementService) {
        this.scriptYamlGenerationService = scriptYamlGenerationService;
        this.scriptYamlStreamService = scriptYamlStreamService;
        this.scriptDraftService = scriptDraftService;
        this.scriptSceneRefinementService = scriptSceneRefinementService;
    }

    @PostMapping("/yaml/from-text")
    public Result<ScriptYamlGenerateResponse> generateYamlFromText(
            @Valid @RequestBody ScriptYamlGenerateRequest request) {
        return Results.success(scriptYamlGenerationService.generateFromText(request));
    }

    @PostMapping(value = "/yaml/from-text/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamGenerateYamlFromText(@Valid @RequestBody ScriptYamlGenerateRequest request) {
        return scriptYamlStreamService.streamGenerateFromText(request);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public Result<ScriptConversionTaskResponse> cancelTask(@PathVariable String taskId) {
        return Results.success(scriptYamlStreamService.cancel(taskId));
    }

    @PostMapping
    public Result<ScriptDraftSaveResponse> save(@Valid @RequestBody ScriptDraftSaveRequest request) {
        return Results.success(scriptDraftService.save(request));
    }

    @GetMapping
    public Result<List<ScriptDraftListItemResponse>> list(@RequestParam String projectId) {
        return Results.success(scriptDraftService.listByProjectId(projectId));
    }

    @GetMapping("/{draftId}")
    public Result<ScriptDraftDetailResponse> detail(@PathVariable String draftId) {
        return Results.success(scriptDraftService.detail(draftId));
    }

    @PostMapping("/{draftId}/scenes/{sceneId}/refine")
    public Result<ScriptSceneRefineResponse> refineScene(@PathVariable String draftId,
                                                         @PathVariable String sceneId,
                                                         @Valid @RequestBody ScriptSceneRefineRequest request) {
        return Results.success(scriptSceneRefinementService.refine(new ScriptSceneRefineRequest(
                draftId,
                sceneId,
                request.instruction()
        )));
    }

    @PostMapping("/{draftId}/dialogues/voice-rewrite")
    public Result<ScriptDialogueVoiceRewriteResponse> rewriteDialogueVoice(
            @PathVariable String draftId,
            @Valid @RequestBody ScriptDialogueVoiceRewriteRequest request) {
        return Results.success(scriptSceneRefinementService.rewriteDialogueVoice(new ScriptDialogueVoiceRewriteRequest(
                draftId,
                request.speaker(),
                request.instruction(),
                request.sceneIds()
        )));
    }

    @GetMapping(value = "/{draftId}/export.yaml", produces = "application/x-yaml")
    public ResponseEntity<String> exportYaml(@PathVariable String draftId) {
        ScriptDraftDetailResponse draft = scriptDraftService.exportYaml(draftId);
        String filename = sanitizeFilename(draft.title()) + "-" + draft.draftId() + ".yaml";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-yaml; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(draft.yaml());
    }

    private String sanitizeFilename(String value) {
        String filename = value == null ? "script-draft" : value.trim();
        filename = filename.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        filename = filename.replaceAll("-+", "-");
        if (filename.isBlank()) {
            return "script-draft";
        }
        return filename.length() > 80 ? filename.substring(0, 80) : filename;
    }
}
