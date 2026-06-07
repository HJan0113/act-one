package com.hjan.actone.script.ai.model;

import java.util.List;

public record DialogueDraft(
        List<SceneDialogues> scenes
) {

    public DialogueDraft {
        scenes = ModelValidation.requireNonEmptyList(scenes, "scenes");
    }

    public record SceneDialogues(
            String sceneId,
            Integer chapterIndex,
            String sourceChapterId,
            List<DialogueLine> dialogues
    ) {

        public SceneDialogues {
            sceneId = ModelValidation.requireText(sceneId, "sceneId");
            chapterIndex = ModelValidation.requirePositive(chapterIndex, "chapterIndex");
            sourceChapterId = ModelValidation.requireText(sourceChapterId, "sourceChapterId");
            dialogues = ModelValidation.requireNonEmptyList(dialogues, "dialogues");
        }
    }

    public record DialogueLine(
            String speaker,
            String speakerCharacterId,
            String line,
            String emotion,
            String sourceExcerpt
    ) {

        public DialogueLine {
            speaker = ModelValidation.requireText(speaker, "speaker");
            speakerCharacterId = ModelValidation.optionalText(speakerCharacterId);
            line = ModelValidation.requireText(line, "line");
            emotion = ModelValidation.optionalText(emotion);
            sourceExcerpt = ModelValidation.requireText(sourceExcerpt, "sourceExcerpt");
        }
    }
}
