package com.hjan.actone.script.ai.model;

import java.util.List;

public record ScenePlan(
        List<Scene> scenes
) {

    public ScenePlan {
        scenes = ModelValidation.requireNonEmptyList(scenes, "scenes");
    }

    public record Scene(
            String id,
            String actId,
            Integer chapterIndex,
            String sourceChapterId,
            String title,
            String location,
            String time,
            List<String> characters,
            String scenePurpose,
            String conflict,
            List<Beat> beats,
            String sourceExcerpt
    ) {

        public Scene {
            id = ModelValidation.requireText(id, "id");
            actId = ModelValidation.requireText(actId, "actId");
            chapterIndex = ModelValidation.requirePositive(chapterIndex, "chapterIndex");
            sourceChapterId = ModelValidation.requireText(sourceChapterId, "sourceChapterId");
            title = ModelValidation.requireText(title, "title");
            location = ModelValidation.requireText(location, "location");
            time = ModelValidation.requireText(time, "time");
            characters = ModelValidation.copyTextList(characters, "characters");
            scenePurpose = ModelValidation.requireText(scenePurpose, "scenePurpose");
            conflict = ModelValidation.optionalText(conflict);
            beats = ModelValidation.requireNonEmptyList(beats, "beats");
            sourceExcerpt = ModelValidation.requireText(sourceExcerpt, "sourceExcerpt");
        }
    }

    public record Beat(
            String description,
            String sourceExcerpt,
            List<String> characterIds
    ) {

        public Beat {
            description = ModelValidation.requireText(description, "description");
            sourceExcerpt = ModelValidation.requireText(sourceExcerpt, "sourceExcerpt");
            characterIds = ModelValidation.copyTextList(characterIds, "characterIds");
        }
    }
}
