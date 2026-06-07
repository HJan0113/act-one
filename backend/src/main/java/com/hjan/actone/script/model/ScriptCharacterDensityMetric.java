package com.hjan.actone.script.model;

import java.util.List;

public record ScriptCharacterDensityMetric(
        String characterName,
        String characterId,
        int sceneCount,
        int dialogueCount,
        List<String> sceneIds
) {
}
