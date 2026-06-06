package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.PipelineContext;

public interface CinematicTreatmentStage {

    CinematicTreatment visualize(PipelineContext context);
}
