package com.hjan.actone.script.ai.model;

import java.util.List;

public record SourceTrace(
        List<SceneTrace> scenes
) {

    public SourceTrace {
        scenes = ModelValidation.requireNonEmptyList(scenes, "scenes");
    }

    public record SceneTrace(
            String sceneId,
            Integer chapterIndex,
            String sourceChapterId,
            String sourceExcerpt,
            List<SourceEvidence> evidence
    ) {

        public SceneTrace {
            sceneId = ModelValidation.requireText(sceneId, "sceneId");
            chapterIndex = ModelValidation.requirePositive(chapterIndex, "chapterIndex");
            sourceChapterId = ModelValidation.requireText(sourceChapterId, "sourceChapterId");
            sourceExcerpt = ModelValidation.requireText(sourceExcerpt, "sourceExcerpt");
            evidence = ModelValidation.requireNonEmptyList(evidence, "evidence");
        }
    }
}
