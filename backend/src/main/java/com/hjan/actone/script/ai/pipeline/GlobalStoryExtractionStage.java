package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;

public interface GlobalStoryExtractionStage {

    GlobalStoryState extract(PipelineContext context);
}
