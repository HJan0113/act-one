package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.ScenePlan;

import java.util.List;

public record ScriptAiPipelineResumeState(
        List<ChapterSummary> chapterSummaries,
        ScenePlan scenePlan
) {

    public static ScriptAiPipelineResumeState empty() {
        return new ScriptAiPipelineResumeState(null, null);
    }

    public boolean hasChapterSummaries() {
        return chapterSummaries != null && !chapterSummaries.isEmpty();
    }

    public boolean hasScenePlan() {
        return hasChapterSummaries() && scenePlan != null;
    }
}
