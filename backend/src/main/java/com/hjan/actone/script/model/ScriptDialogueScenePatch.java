package com.hjan.actone.script.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScriptDialogueScenePatch(
        String sceneId,
        List<ScriptDialoguePatch> dialogues,
        String summary,
        List<String> riskWarnings
) {

    public ScriptDialogueScenePatch {
        if (dialogues != null) {
            dialogues = List.copyOf(dialogues);
        }
        if (riskWarnings != null) {
            riskWarnings = List.copyOf(riskWarnings);
        }
    }
}
