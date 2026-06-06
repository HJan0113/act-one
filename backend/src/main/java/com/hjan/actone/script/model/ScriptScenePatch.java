package com.hjan.actone.script.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScriptScenePatch(
        String sceneId,
        String title,
        String location,
        String time,
        String scenePurpose,
        String action,
        List<ScriptDialoguePatch> dialogues,
        Boolean aiExpansion,
        String summary,
        List<String> riskWarnings
) {

    public ScriptScenePatch {
        if (dialogues != null) {
            dialogues = List.copyOf(dialogues);
        }
        if (riskWarnings != null) {
            riskWarnings = List.copyOf(riskWarnings);
        }
    }
}
