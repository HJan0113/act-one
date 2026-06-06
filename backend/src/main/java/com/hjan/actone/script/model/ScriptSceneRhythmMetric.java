package com.hjan.actone.script.model;

import java.util.List;

public record ScriptSceneRhythmMetric(
        String sceneId,
        String title,
        int actionCharCount,
        int dialogueCharCount,
        int dialogueCount,
        int actionRatio,
        int dialogueRatio,
        int conflictScore,
        int turningScore,
        int purposeScore,
        String paceLevel,
        String summary,
        List<String> warnings
) {
}
