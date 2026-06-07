package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;

public interface ScenePlanningStage {

    ScenePlan plan(PipelineContext context);
}
