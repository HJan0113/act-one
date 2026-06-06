package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.GlobalStoryState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlobalStoryStateMerger {

    private static final Pattern CHAPTER_NUMBER = Pattern.compile("(\\d+)");

    public GlobalStoryState merge(GlobalStoryState base, GlobalStoryState incoming) {
        if (base == null) {
            return normalize(incoming);
        }
        if (incoming == null) {
            return normalize(base);
        }
        return normalize(new GlobalStoryState(
                concat(base.characters(), incoming.characters()),
                concat(base.locations(), incoming.locations()),
                concat(base.timeline(), incoming.timeline()),
                concat(base.relationships(), incoming.relationships()),
                concat(base.props(), incoming.props()),
                concat(base.foreshadowing(), incoming.foreshadowing()),
                concat(base.conflicts(), incoming.conflicts())
        ));
    }

    public GlobalStoryState normalize(GlobalStoryState state) {
        Objects.requireNonNull(state, "GlobalStoryState 不能为空");
        ConflictCollector conflicts = new ConflictCollector(state.conflicts());
        CharacterMergeResult characterResult = mergeCharacters(state.characters(), conflicts);
        List<GlobalStoryState.Relationship> relationships = mergeRelationships(
                state.relationships(),
                characterResult.idMapping(),
                conflicts
        );
        return new GlobalStoryState(
                characterResult.characters(),
                mergeLocations(state.locations()),
                mergeTimeline(state.timeline()),
                relationships,
                mergeProps(state.props()),
                mergeForeshadowing(state.foreshadowing()),
                mergeConflicts(conflicts.all(), characterResult.idMapping())
        );
    }

    private CharacterMergeResult mergeCharacters(List<GlobalStoryState.CharacterProfile> characters,
                                                 ConflictCollector conflicts) {
        List<CharacterAccumulator> groups = new ArrayList<>();
        Map<String, String> idMapping = new LinkedHashMap<>();
        for (GlobalStoryState.CharacterProfile character : characters) {
            CharacterAccumulator matched = groups.stream()
                    .filter(group -> group.matches(character))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                matched = new CharacterAccumulator(character);
                groups.add(matched);
            } else {
                matched.merge(character, conflicts);
            }
            idMapping.put(character.id(), matched.canonicalId());
        }
        groups.sort(Comparator.comparing(CharacterAccumulator::firstAppearanceRank)
                .thenComparing(CharacterAccumulator::canonicalId));
        for (CharacterAccumulator group : groups) {
            group.originalIds().forEach(originalId -> idMapping.put(originalId, group.canonicalId()));
        }
        return new CharacterMergeResult(
                groups.stream().map(CharacterAccumulator::toProfile).toList(),
                idMapping
        );
    }

    private List<GlobalStoryState.LocationProfile> mergeLocations(List<GlobalStoryState.LocationProfile> locations) {
        Map<String, LocationAccumulator> merged = new LinkedHashMap<>();
        for (GlobalStoryState.LocationProfile location : locations) {
            merged.computeIfAbsent(normalizeKey(location.name()), key -> new LocationAccumulator(location))
                    .merge(location);
        }
        return merged.values().stream().map(LocationAccumulator::toProfile).toList();
    }

    private List<GlobalStoryState.TimelineEvent> mergeTimeline(List<GlobalStoryState.TimelineEvent> timeline) {
        Map<String, GlobalStoryState.TimelineEvent> merged = new LinkedHashMap<>();
        for (GlobalStoryState.TimelineEvent event : timeline) {
            String key = hasText(event.eventId())
                    ? event.eventId()
                    : event.chapterId() + "|" + event.order() + "|" + normalizeKey(event.description());
            merged.putIfAbsent(key, event);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(GlobalStoryState.TimelineEvent::order)
                        .thenComparing(GlobalStoryState.TimelineEvent::chapterId)
                        .thenComparing(GlobalStoryState.TimelineEvent::eventId))
                .toList();
    }

    private List<GlobalStoryState.Relationship> mergeRelationships(List<GlobalStoryState.Relationship> relationships,
                                                                   Map<String, String> characterIdMapping,
                                                                   ConflictCollector conflicts) {
        Map<String, RelationshipAccumulator> merged = new LinkedHashMap<>();
        Map<String, Set<String>> typesByPair = new LinkedHashMap<>();
        Map<String, List<String>> sourcesByPair = new LinkedHashMap<>();
        for (GlobalStoryState.Relationship relationship : relationships) {
            String sourceId = remap(characterIdMapping, relationship.sourceCharacterId());
            String targetId = remap(characterIdMapping, relationship.targetCharacterId());
            String pairKey = sourceId + "->" + targetId;
            typesByPair.computeIfAbsent(pairKey, key -> new LinkedHashSet<>()).add(relationship.type());
            sourcesByPair.computeIfAbsent(pairKey, key -> new ArrayList<>()).addAll(relationship.sourceChapterIds());

            String key = pairKey + "|" + normalizeKey(relationship.type());
            merged.computeIfAbsent(key, ignored -> new RelationshipAccumulator(relationship, sourceId, targetId))
                    .merge(relationship);
        }
        typesByPair.forEach((pairKey, types) -> {
            if (types.size() > 1) {
                String[] ids = pairKey.split("->", 2);
                conflicts.add("关系类型存在变化：" + String.join(" / ", types),
                        List.of(ids[0], ids[1]),
                        sourcesByPair.get(pairKey));
            }
        });
        return merged.values().stream().map(RelationshipAccumulator::toRelationship).toList();
    }

    private List<GlobalStoryState.StoryProp> mergeProps(List<GlobalStoryState.StoryProp> props) {
        Map<String, PropAccumulator> merged = new LinkedHashMap<>();
        for (GlobalStoryState.StoryProp prop : props) {
            merged.computeIfAbsent(normalizeKey(prop.name()), key -> new PropAccumulator(prop))
                    .merge(prop);
        }
        return merged.values().stream().map(PropAccumulator::toProp).toList();
    }

    private List<GlobalStoryState.Foreshadowing> mergeForeshadowing(List<GlobalStoryState.Foreshadowing> items) {
        Map<String, ForeshadowingAccumulator> merged = new LinkedHashMap<>();
        for (GlobalStoryState.Foreshadowing item : items) {
            String key = normalizeKey(item.description()) + "|" + normalizeKey(item.payoffHint());
            merged.computeIfAbsent(key, ignored -> new ForeshadowingAccumulator(item))
                    .merge(item);
        }
        return merged.values().stream().map(ForeshadowingAccumulator::toForeshadowing).toList();
    }

    private List<GlobalStoryState.ConflictNote> mergeConflicts(List<GlobalStoryState.ConflictNote> conflicts,
                                                               Map<String, String> characterIdMapping) {
        Map<String, ConflictAccumulator> merged = new LinkedHashMap<>();
        for (GlobalStoryState.ConflictNote conflict : conflicts) {
            List<String> candidateIds = conflict.candidateIds().stream()
                    .map(candidateId -> remap(characterIdMapping, candidateId))
                    .distinct()
                    .toList();
            String key = normalizeKey(conflict.description()) + "|" + String.join(",", candidateIds);
            merged.computeIfAbsent(key, ignored -> new ConflictAccumulator(conflict, candidateIds))
                    .merge(conflict, candidateIds);
        }
        return merged.values().stream().map(ConflictAccumulator::toConflict).toList();
    }

    private String remap(Map<String, String> mapping, String id) {
        return mapping.getOrDefault(id, id);
    }

    private <T> List<T> concat(List<T> left, List<T> right) {
        List<T> result = new ArrayList<>(left.size() + right.size());
        result.addAll(left);
        result.addAll(right);
        return result;
    }

    private List<String> unionText(Collection<String> left, Collection<String> right) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addText(values, left);
        addText(values, right);
        return List.copyOf(values);
    }

    private void addText(Set<String> values, Collection<String> candidates) {
        if (candidates == null) {
            return;
        }
        candidates.stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(values::add);
    }

    private String combineText(String left, String right) {
        if (!hasText(left)) {
            return optionalText(right);
        }
        if (!hasText(right) || left.trim().equals(right.trim())) {
            return left.trim();
        }
        return left.trim() + "；" + right.trim();
    }

    private String optionalText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeKey(String value) {
        return hasText(value) ? value.trim().toLowerCase() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int chapterRank(String chapterId) {
        if (!hasText(chapterId)) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = CHAPTER_NUMBER.matcher(chapterId);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private final class CharacterAccumulator {

        private String canonicalId;
        private String name;
        private final LinkedHashSet<String> aliases = new LinkedHashSet<>();
        private final LinkedHashSet<String> matchKeys = new LinkedHashSet<>();
        private String role;
        private String description;
        private String firstAppearanceChapterId;
        private final LinkedHashSet<String> sourceChapterIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> originalIds = new LinkedHashSet<>();

        private CharacterAccumulator(GlobalStoryState.CharacterProfile character) {
            canonicalId = character.id();
            name = character.name();
            role = optionalText(character.role());
            description = optionalText(character.description());
            firstAppearanceChapterId = character.firstAppearanceChapterId();
            mergeKeys(character);
            sourceChapterIds.addAll(character.sourceChapterIds());
            originalIds.add(character.id());
        }

        private boolean matches(GlobalStoryState.CharacterProfile character) {
            return keys(character).stream().anyMatch(matchKeys::contains);
        }

        private void merge(GlobalStoryState.CharacterProfile character, ConflictCollector conflicts) {
            originalIds.add(character.id());
            if (chapterRank(character.firstAppearanceChapterId()) < chapterRank(firstAppearanceChapterId)) {
                aliases.add(name);
                canonicalId = character.id();
                name = character.name();
                firstAppearanceChapterId = character.firstAppearanceChapterId();
            }
            mergeKeys(character);
            sourceChapterIds.addAll(character.sourceChapterIds());
            description = combineText(description, character.description());
            if (!hasText(role)) {
                role = optionalText(character.role());
            } else if (hasText(character.role()) && !role.equals(character.role().trim())) {
                conflicts.add(
                        "人物“" + name + "”存在角色冲突：" + role + " / " + character.role().trim(),
                        List.of(canonicalId, character.id()),
                        unionText(sourceChapterIds, character.sourceChapterIds())
                );
            }
        }

        private void mergeKeys(GlobalStoryState.CharacterProfile character) {
            List<String> keys = keys(character);
            matchKeys.addAll(keys);
            keys.stream()
                    .filter(alias -> !normalizeKey(alias).equals(normalizeKey(name)))
                    .forEach(aliases::add);
        }

        private List<String> keys(GlobalStoryState.CharacterProfile character) {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            keys.add(character.name().trim());
            keys.addAll(character.aliases());
            return keys.stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        }

        private int firstAppearanceRank() {
            return chapterRank(firstAppearanceChapterId);
        }

        private String canonicalId() {
            return canonicalId;
        }

        private Set<String> originalIds() {
            return originalIds;
        }

        private GlobalStoryState.CharacterProfile toProfile() {
            aliases.removeIf(alias -> normalizeKey(alias).equals(normalizeKey(name)));
            return new GlobalStoryState.CharacterProfile(
                    canonicalId,
                    name,
                    List.copyOf(aliases),
                    role,
                    description,
                    firstAppearanceChapterId,
                    List.copyOf(sourceChapterIds)
            );
        }
    }

    private record CharacterMergeResult(
            List<GlobalStoryState.CharacterProfile> characters,
            Map<String, String> idMapping
    ) {
    }

    private final class LocationAccumulator {

        private final String id;
        private final String name;
        private String description;
        private final LinkedHashSet<String> sourceChapterIds = new LinkedHashSet<>();

        private LocationAccumulator(GlobalStoryState.LocationProfile location) {
            id = location.id();
            name = location.name();
            description = optionalText(location.description());
            sourceChapterIds.addAll(location.sourceChapterIds());
        }

        private void merge(GlobalStoryState.LocationProfile location) {
            description = combineText(description, location.description());
            sourceChapterIds.addAll(location.sourceChapterIds());
        }

        private GlobalStoryState.LocationProfile toProfile() {
            return new GlobalStoryState.LocationProfile(id, name, description, List.copyOf(sourceChapterIds));
        }
    }

    private final class RelationshipAccumulator {

        private final String id;
        private final String sourceCharacterId;
        private final String targetCharacterId;
        private final String type;
        private String description;
        private final LinkedHashSet<String> sourceChapterIds = new LinkedHashSet<>();

        private RelationshipAccumulator(GlobalStoryState.Relationship relationship,
                                        String sourceCharacterId,
                                        String targetCharacterId) {
            id = relationship.id();
            this.sourceCharacterId = sourceCharacterId;
            this.targetCharacterId = targetCharacterId;
            type = relationship.type();
            description = optionalText(relationship.description());
            sourceChapterIds.addAll(relationship.sourceChapterIds());
        }

        private void merge(GlobalStoryState.Relationship relationship) {
            description = combineText(description, relationship.description());
            sourceChapterIds.addAll(relationship.sourceChapterIds());
        }

        private GlobalStoryState.Relationship toRelationship() {
            return new GlobalStoryState.Relationship(
                    id,
                    sourceCharacterId,
                    targetCharacterId,
                    type,
                    description,
                    List.copyOf(sourceChapterIds)
            );
        }
    }

    private final class PropAccumulator {

        private final String id;
        private final String name;
        private String description;
        private final LinkedHashSet<String> sourceChapterIds = new LinkedHashSet<>();

        private PropAccumulator(GlobalStoryState.StoryProp prop) {
            id = prop.id();
            name = prop.name();
            description = optionalText(prop.description());
            sourceChapterIds.addAll(prop.sourceChapterIds());
        }

        private void merge(GlobalStoryState.StoryProp prop) {
            description = combineText(description, prop.description());
            sourceChapterIds.addAll(prop.sourceChapterIds());
        }

        private GlobalStoryState.StoryProp toProp() {
            return new GlobalStoryState.StoryProp(id, name, description, List.copyOf(sourceChapterIds));
        }
    }

    private final class ForeshadowingAccumulator {

        private final String id;
        private String description;
        private String payoffHint;
        private final LinkedHashSet<String> sourceChapterIds = new LinkedHashSet<>();

        private ForeshadowingAccumulator(GlobalStoryState.Foreshadowing item) {
            id = item.id();
            description = item.description();
            payoffHint = optionalText(item.payoffHint());
            sourceChapterIds.addAll(item.sourceChapterIds());
        }

        private void merge(GlobalStoryState.Foreshadowing item) {
            description = combineText(description, item.description());
            payoffHint = combineText(payoffHint, item.payoffHint());
            sourceChapterIds.addAll(item.sourceChapterIds());
        }

        private GlobalStoryState.Foreshadowing toForeshadowing() {
            return new GlobalStoryState.Foreshadowing(id, description, payoffHint, List.copyOf(sourceChapterIds));
        }
    }

    private final class ConflictAccumulator {

        private final String id;
        private String description;
        private final LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> sourceChapterIds = new LinkedHashSet<>();

        private ConflictAccumulator(GlobalStoryState.ConflictNote conflict, List<String> remappedCandidateIds) {
            id = conflict.id();
            description = conflict.description();
            candidateIds.addAll(remappedCandidateIds);
            sourceChapterIds.addAll(conflict.sourceChapterIds());
        }

        private void merge(GlobalStoryState.ConflictNote conflict, List<String> remappedCandidateIds) {
            description = combineText(description, conflict.description());
            candidateIds.addAll(remappedCandidateIds);
            sourceChapterIds.addAll(conflict.sourceChapterIds());
        }

        private GlobalStoryState.ConflictNote toConflict() {
            return new GlobalStoryState.ConflictNote(
                    id,
                    description,
                    List.copyOf(candidateIds),
                    List.copyOf(sourceChapterIds)
            );
        }
    }

    private final class ConflictCollector {

        private final List<GlobalStoryState.ConflictNote> conflicts = new ArrayList<>();
        private int generatedConflictIndex = 1;

        private ConflictCollector(List<GlobalStoryState.ConflictNote> existing) {
            conflicts.addAll(existing);
        }

        private void add(String description, List<String> candidateIds, Collection<String> sourceChapterIds) {
            conflicts.add(new GlobalStoryState.ConflictNote(
                    "conflict_merge_%03d".formatted(generatedConflictIndex++),
                    description,
                    candidateIds,
                    unionText(List.of(), sourceChapterIds)
            ));
        }

        private List<GlobalStoryState.ConflictNote> all() {
            return List.copyOf(conflicts);
        }
    }
}
