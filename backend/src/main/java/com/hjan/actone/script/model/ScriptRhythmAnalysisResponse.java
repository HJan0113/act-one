package com.hjan.actone.script.model;

import java.util.List;

public record ScriptRhythmAnalysisResponse(
        String draftId,
        String assessmentMode,
        ScriptRhythmOverallMetrics overall,
        List<ScriptSceneRhythmMetric> scenes,
        List<String> suggestions
) {
}
