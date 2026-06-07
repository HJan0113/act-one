package com.hjan.actone.script.service;

import com.hjan.actone.script.ai.pipeline.ScriptAiGenerationMode;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineObserver;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineResult;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineResumeState;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineService;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.model.ScriptYamlGenerateResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ScriptYamlGenerationService {

    private final ScriptYamlDraftService ruleBasedDraftService;
    private final ScriptYamlValidator yamlValidator;
    private final ObjectProvider<ScriptAiPipelineService> aiPipelineProvider;

    public ScriptYamlGenerationService(ScriptYamlDraftService ruleBasedDraftService,
                                       ScriptYamlValidator yamlValidator,
                                       ObjectProvider<ScriptAiPipelineService> aiPipelineProvider) {
        this.ruleBasedDraftService = Objects.requireNonNull(
                ruleBasedDraftService,
                "ScriptYamlDraftService 不能为空"
        );
        this.yamlValidator = Objects.requireNonNull(yamlValidator, "ScriptYamlValidator 不能为空");
        this.aiPipelineProvider = Objects.requireNonNull(
                aiPipelineProvider,
                "ScriptAiPipelineService ObjectProvider 不能为空"
        );
    }

    public ScriptYamlGenerateResponse generateFromText(ScriptYamlGenerateRequest request) {
        return generateFromText(request, ScriptAiPipelineObserver.NOOP);
    }

    public ScriptYamlGenerateResponse generateFromText(ScriptYamlGenerateRequest request,
                                                       ScriptAiPipelineObserver observer) {
        return generateFromText(request, observer, ScriptAiPipelineResumeState.empty());
    }

    public ScriptYamlGenerateResponse generateFromText(ScriptYamlGenerateRequest request,
                                                       ScriptAiPipelineObserver observer,
                                                       ScriptAiPipelineResumeState resumeState) {
        ScriptAiPipelineService aiPipelineService = aiPipelineProvider.getIfAvailable();
        if (aiPipelineService != null) {
            return fromAiPipeline(aiPipelineService.generate(request, observer, resumeState));
        }
        return ruleBasedScaffold(request);
    }

    private ScriptYamlGenerateResponse fromAiPipeline(ScriptAiPipelineResult result) {
        ScriptYamlValidationResult validation = result.validationResult();
        return new ScriptYamlGenerateResponse(
                result.schemaVersion(),
                result.title(),
                result.chapterCount(),
                result.yaml(),
                result.generationMode().yamlValue(),
                result.fallback(),
                result.fallbackReason(),
                validation == null ? null : validation.status(),
                validation == null ? null : validation.errorMessage(),
                result.usageSummary()
        );
    }

    private ScriptYamlGenerateResponse ruleBasedScaffold(ScriptYamlGenerateRequest request) {
        ScriptYamlGenerateResponse scaffold = ruleBasedDraftService.generateFromText(request);
        ScriptYamlValidationResult validation = yamlValidator.validate(scaffold.yaml());
        return new ScriptYamlGenerateResponse(
                scaffold.schemaVersion(),
                scaffold.title(),
                scaffold.chapterCount(),
                validation.cleanedYaml(),
                ScriptAiGenerationMode.RULE_BASED_SCAFFOLD.yamlValue(),
                false,
                null,
                validation.status(),
                validation.errorMessage(),
                null
        );
    }
}
