package com.hjan.actone.ai.prompt;

import java.util.Objects;

public record RenderedPrompt(
        String templateId,
        String version,
        String content
) {

    public RenderedPrompt {
        templateId = requireText(templateId, "templateId");
        version = requireText(version, "version");
        content = requireText(content, "content");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
