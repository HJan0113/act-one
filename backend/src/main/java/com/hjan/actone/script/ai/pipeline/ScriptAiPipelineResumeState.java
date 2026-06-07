package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.ScenePlan;

import java.util.List;

public record ScriptAiPipelineResumeState(
        List<ChapterSummary> chapterSummaries,
        GlobalStoryState globalStoryState,
        ScenePlan scenePlan
) {

    public static ScriptAiPipelineResumeState empty() {
        return new ScriptAiPipelineResumeState(null, null, null);
    }

    public boolean hasChapterSummaries() {
        return chapterSummaries != null && !chapterSummaries.isEmpty();
    }

    public boolean hasGlobalStoryState() {
        return hasChapterSummaries() && globalStoryState != null;
    }

    public boolean hasScenePlan() {
        return hasGlobalStoryState() && scenePlan != null;
    }
}
