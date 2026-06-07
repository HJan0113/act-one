package com.hjan.actone.script.ai.model;

import java.util.List;

public record CinematicTreatment(
        List<SceneTreatment> scenes
) {

    public CinematicTreatment {
        scenes = ModelValidation.requireNonEmptyList(scenes, "scenes");
    }

    public record SceneTreatment(
            String sceneId,
            Integer chapterIndex,
            String sourceChapterId,
            String action,
            List<Transformation> transformations
    ) {

        public SceneTreatment {
            sceneId = ModelValidation.requireText(sceneId, "sceneId");
            chapterIndex = ModelValidation.requirePositive(chapterIndex, "chapterIndex");
            sourceChapterId = ModelValidation.requireText(sourceChapterId, "sourceChapterId");
            action = ModelValidation.requireText(action, "action");
            transformations = ModelValidation.copyList(transformations, "transformations");
        }
    }

    public record Transformation(
            String originalText,
            String visualizedText,
            String technique,
            String sourceExcerpt,
            List<String> characterIds
    ) {

        public Transformation {
            originalText = ModelValidation.requireText(originalText, "originalText");
            visualizedText = ModelValidation.requireText(visualizedText, "visualizedText");
            technique = ModelValidation.requireText(technique, "technique");
            sourceExcerpt = ModelValidation.requireText(sourceExcerpt, "sourceExcerpt");
            characterIds = ModelValidation.copyTextList(characterIds, "characterIds");
        }
    }
}
