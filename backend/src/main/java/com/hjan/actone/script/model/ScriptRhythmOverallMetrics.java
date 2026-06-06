package com.hjan.actone.script.model;

public record ScriptRhythmOverallMetrics(
        int sceneCount,
        int dialogueCount,
        int actionCharCount,
        int dialogueCharCount,
        int actionRatio,
        int dialogueRatio,
        int averageConflictScore,
        int averageTurningScore,
        int purposeCoverage,
        int imbalanceSceneCount
) {
}
