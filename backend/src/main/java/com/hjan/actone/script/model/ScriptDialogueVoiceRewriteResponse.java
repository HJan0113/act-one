package com.hjan.actone.script.model;

public record ScriptDialogueVoiceRewriteResponse(
        String draftId,
        String speaker,
        ScriptDialogueVoicePatch voicePatch,
        String patchedYaml,
        String validationStatus,
        String validationError
) {
}
