package com.hjan.actone.ai.prompt;

import java.util.Map;

public interface PromptTemplateService {

    String load(PromptTemplateId templateId);

    RenderedPrompt render(PromptTemplateId templateId, Map<String, ?> variables);
}
