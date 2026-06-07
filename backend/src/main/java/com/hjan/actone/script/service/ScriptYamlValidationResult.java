package com.hjan.actone.script.service;

public record ScriptYamlValidationResult(
        String cleanedYaml,
        String status,
        String errorMessage
) {

    public boolean valid() {
        return "valid".equals(status);
    }
}
