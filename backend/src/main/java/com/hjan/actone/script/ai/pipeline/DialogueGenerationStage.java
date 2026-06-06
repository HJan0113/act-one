package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.DialogueDraft;
import com.hjan.actone.script.ai.model.PipelineContext;

public interface DialogueGenerationStage {

    DialogueDraft draft(PipelineContext context);
}
