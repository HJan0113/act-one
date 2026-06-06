package com.hjan.actone.script.model;

public record ScriptSceneRefineResponse(
        String draftId,
        String sceneId,
        ScriptScenePatch scenePatch,
        String patchedYaml,
        String validationStatus,
        String validationError
) {
}
