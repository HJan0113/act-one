package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.model.NovelChapter;

public interface ChapterSummaryStage {

    ChapterSummary summarize(NovelChapter chapter);
}
