package com.hjan.actone.script.model;

import java.util.List;

public record ScriptStoryboardResponse(
        String draftId,
        String sceneId,
        String assessmentMode,
        List<ShotSuggestion> shots,
        List<String> suggestions
) {

    public record ShotSuggestion(
            String shotId,
            String shotType,
            String subject,
            String action,
            String emotion,
            String keyProp,
            String sound,
            String tone,
            String description
    ) {
    }
}
