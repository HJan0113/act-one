package com.hjan.actone.script.ai.model;

import java.util.List;

public record ChapterSummary(
        String chapterId,
        Integer chapterIndex,
        String title,
        String summary,
        List<KeyEvent> keyEvents,
        List<String> characters,
        List<String> locations,
        List<String> timeExpressions,
        List<String> conflicts,
        List<InnerState> innerStates,
        List<SourceEvidence> sourceEvidence
) {

    public ChapterSummary {
        chapterId = ModelValidation.requireText(chapterId, "chapterId");
        chapterIndex = ModelValidation.requirePositive(chapterIndex, "chapterIndex");
        title = ModelValidation.requireText(title, "title");
        summary = ModelValidation.requireText(summary, "summary");
        keyEvents = ModelValidation.copyList(keyEvents, "keyEvents");
        characters = ModelValidation.copyTextList(characters, "characters");
        locations = ModelValidation.copyTextList(locations, "locations");
        timeExpressions = ModelValidation.copyTextList(timeExpressions, "timeExpressions");
        conflicts = ModelValidation.copyTextList(conflicts, "conflicts");
        innerStates = ModelValidation.copyList(innerStates, "innerStates");
        sourceEvidence = ModelValidation.requireNonEmptyList(sourceEvidence, "sourceEvidence");
    }

    public record KeyEvent(
            String eventId,
            String description,
            List<SourceEvidence> evidence
    ) {

        public KeyEvent {
            eventId = ModelValidation.requireText(eventId, "eventId");
            description = ModelValidation.requireText(description, "description");
            evidence = ModelValidation.requireNonEmptyList(evidence, "evidence");
        }
    }

    public record InnerState(
            String character,
            String description,
            SourceEvidence evidence
    ) {

        public InnerState {
            character = ModelValidation.requireText(character, "character");
            description = ModelValidation.requireText(description, "description");
            if (evidence == null) {
                throw new IllegalArgumentException("evidence 不能为空");
            }
        }
    }
}
