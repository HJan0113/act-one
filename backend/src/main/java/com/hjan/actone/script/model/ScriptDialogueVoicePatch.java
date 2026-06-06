package com.hjan.actone.script.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScriptDialogueVoicePatch(
        String speaker,
        String voiceSample,
        List<ScriptDialogueScenePatch> scenePatches,
        String summary,
        List<String> riskWarnings
) {

    public ScriptDialogueVoicePatch {
        if (scenePatches != null) {
            scenePatches = List.copyOf(scenePatches);
        }
        if (riskWarnings != null) {
            riskWarnings = List.copyOf(riskWarnings);
        }
    }
}
