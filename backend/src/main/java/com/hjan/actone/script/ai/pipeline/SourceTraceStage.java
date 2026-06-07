package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.SourceTrace;
import com.hjan.actone.script.model.NovelChapter;

import java.util.List;

public interface SourceTraceStage {

    SourceTrace trace(PipelineContext context, List<NovelChapter> chapters);
}
