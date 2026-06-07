package com.hjan.actone.script.ai.model;

import java.util.List;

public record GlobalStoryState(
        List<CharacterProfile> characters,
        List<LocationProfile> locations,
        List<TimelineEvent> timeline,
        List<Relationship> relationships,
        List<StoryProp> props,
        List<Foreshadowing> foreshadowing,
        List<ConflictNote> conflicts
) {

    public GlobalStoryState {
        characters = ModelValidation.copyList(characters, "characters");
        locations = ModelValidation.copyList(locations, "locations");
        timeline = ModelValidation.copyList(timeline, "timeline");
        relationships = ModelValidation.copyList(relationships, "relationships");
        props = ModelValidation.copyList(props, "props");
        foreshadowing = ModelValidation.copyList(foreshadowing, "foreshadowing");
        conflicts = ModelValidation.copyList(conflicts, "conflicts");
    }

    public static GlobalStoryState empty() {
        return new GlobalStoryState(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public record CharacterProfile(
            String id,
            String name,
            List<String> aliases,
            String role,
            String description,
            String firstAppearanceChapterId,
            List<String> sourceChapterIds
    ) {

        public CharacterProfile {
            id = ModelValidation.requireText(id, "id");
            name = ModelValidation.requireText(name, "name");
            aliases = ModelValidation.copyTextList(aliases, "aliases");
            role = ModelValidation.optionalText(role);
            description = ModelValidation.optionalText(description);
            firstAppearanceChapterId = ModelValidation.requireText(
                    firstAppearanceChapterId,
                    "firstAppearanceChapterId"
            );
            sourceChapterIds = ModelValidation.copyTextList(sourceChapterIds, "sourceChapterIds");
        }
    }

    public record LocationProfile(
            String id,
            String name,
            String description,
            List<String> sourceChapterIds
    ) {

        public LocationProfile {
            id = ModelValidation.requireText(id, "id");
            name = ModelValidation.requireText(name, "name");
            description = ModelValidation.optionalText(description);
            sourceChapterIds = ModelValidation.copyTextList(sourceChapterIds, "sourceChapterIds");
        }
    }

    public record TimelineEvent(
            String eventId,
            Integer order,
            String chapterId,
            String timeExpression,
            String description
    ) {

        public TimelineEvent {
            eventId = ModelValidation.requireText(eventId, "eventId");
            order = ModelValidation.requirePositive(order, "order");
            chapterId = ModelValidation.requireText(chapterId, "chapterId");
            timeExpression = ModelValidation.optionalText(timeExpression);
            description = ModelValidation.requireText(description, "description");
        }
    }

    public record Relationship(
            String id,
            String sourceCharacterId,
            String targetCharacterId,
            String type,
            String description,
            List<String> sourceChapterIds
    ) {

        public Relationship {
            id = ModelValidation.requireText(id, "id");
            sourceCharacterId = ModelValidation.requireText(sourceCharacterId, "sourceCharacterId");
            targetCharacterId = ModelValidation.requireText(targetCharacterId, "targetCharacterId");
            type = ModelValidation.requireText(type, "type");
            description = ModelValidation.optionalText(description);
            sourceChapterIds = ModelValidation.copyTextList(sourceChapterIds, "sourceChapterIds");
        }
    }

    public record StoryProp(
            String id,
            String name,
            String description,
            List<String> sourceChapterIds
    ) {

        public StoryProp {
            id = ModelValidation.requireText(id, "id");
            name = ModelValidation.requireText(name, "name");
            description = ModelValidation.optionalText(description);
            sourceChapterIds = ModelValidation.copyTextList(sourceChapterIds, "sourceChapterIds");
        }
    }

    public record Foreshadowing(
            String id,
            String description,
            String payoffHint,
            List<String> sourceChapterIds
    ) {

        public Foreshadowing {
            id = ModelValidation.requireText(id, "id");
            description = ModelValidation.requireText(description, "description");
            payoffHint = ModelValidation.optionalText(payoffHint);
            sourceChapterIds = ModelValidation.copyTextList(sourceChapterIds, "sourceChapterIds");
        }
    }

    public record ConflictNote(
            String id,
            String description,
            List<String> candidateIds,
            List<String> sourceChapterIds
    ) {

        public ConflictNote {
            id = ModelValidation.requireText(id, "id");
            description = ModelValidation.requireText(description, "description");
            candidateIds = ModelValidation.copyTextList(candidateIds, "candidateIds");
            sourceChapterIds = ModelValidation.copyTextList(sourceChapterIds, "sourceChapterIds");
        }
    }
}
