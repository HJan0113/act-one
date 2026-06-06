package com.hjan.actone.script.ai.pipeline;

public enum ScriptAiGenerationMode {
    AI_PIPELINE("ai_pipeline"),
    RULE_BASED_FALLBACK("rule_based_fallback");

    private final String yamlValue;

    ScriptAiGenerationMode(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    public String yamlValue() {
        return yamlValue;
    }
}
