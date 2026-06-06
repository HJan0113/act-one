package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.GlobalStoryState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalStoryStateMergerTest {

    private final GlobalStoryStateMerger merger = new GlobalStoryStateMerger();

    @Test
    void normalizeMergesCharactersByAliasAndKeepsEarliestAppearance() {
        GlobalStoryState state = state(
                List.of(
                        character("character_child", "孩子", List.of(), "被保护者", "chapter_1", List.of("chapter_1")),
                        character("character_002", "阿夜", List.of("林夜"), "刺客", "chapter_2", List.of("chapter_2")),
                        character("character_001", "林夜", List.of("夜哥"), "刺客", "chapter_1", List.of("chapter_1"))
                ),
                List.of(relationship(
                        "relationship_001",
                        "character_002",
                        "character_child",
                        "保护",
                        List.of("chapter_2")
                )),
                List.of()
        );

        GlobalStoryState normalized = merger.normalize(state);

        assertThat(normalized.characters()).hasSize(2);
        GlobalStoryState.CharacterProfile linYe = normalized.characters().stream()
                .filter(character -> character.id().equals("character_001"))
                .findFirst()
                .orElseThrow();
        assertThat(linYe.name()).isEqualTo("林夜");
        assertThat(linYe.aliases()).containsExactly("阿夜", "夜哥");
        assertThat(linYe.firstAppearanceChapterId()).isEqualTo("chapter_1");
        assertThat(linYe.sourceChapterIds()).containsExactly("chapter_2", "chapter_1");
        assertThat(linYe.description()).contains("阿夜 描述", "林夜 描述");
        assertThat(normalized.relationships()).singleElement()
                .satisfies(relationship -> {
                    assertThat(relationship.sourceCharacterId()).isEqualTo("character_001");
                    assertThat(relationship.targetCharacterId()).isEqualTo("character_child");
                    assertThat(relationship.sourceChapterIds()).containsExactly("chapter_2");
                });
    }

    @Test
    void normalizePreservesRoleConflictWhenDuplicateCharacterRolesDiffer() {
        GlobalStoryState state = state(
                List.of(
                        character("character_001", "林夜", List.of(), "刺客", "chapter_1", List.of("chapter_1")),
                        character("character_002", "阿夜", List.of("林夜"), "护卫", "chapter_2", List.of("chapter_2"))
                ),
                List.of(),
                List.of()
        );

        GlobalStoryState normalized = merger.normalize(state);

        assertThat(normalized.characters()).hasSize(1);
        assertThat(normalized.characters().get(0).role()).isEqualTo("刺客");
        assertThat(normalized.conflicts()).singleElement()
                .satisfies(conflict -> {
                    assertThat(conflict.description()).contains("角色冲突", "刺客", "护卫");
                    assertThat(conflict.candidateIds()).containsExactly("character_001");
                    assertThat(conflict.sourceChapterIds()).containsExactly("chapter_1", "chapter_2");
                });
    }

    @Test
    void normalizeKeepsRelationshipTypeChangesAsConflicts() {
        GlobalStoryState state = state(
                List.of(
                        character("character_001", "林夜", List.of(), "主角", "chapter_1", List.of("chapter_1")),
                        character("character_002", "孩子", List.of(), "被保护者", "chapter_1", List.of("chapter_1"))
                ),
                List.of(
                        relationship("relationship_001", "character_001", "character_002", "敌对", List.of("chapter_1")),
                        relationship("relationship_002", "character_001", "character_002", "保护", List.of("chapter_3"))
                ),
                List.of()
        );

        GlobalStoryState normalized = merger.normalize(state);

        assertThat(normalized.relationships()).extracting(GlobalStoryState.Relationship::type)
                .containsExactly("敌对", "保护");
        assertThat(normalized.conflicts()).singleElement()
                .satisfies(conflict -> {
                    assertThat(conflict.description()).contains("关系类型存在变化", "敌对", "保护");
                    assertThat(conflict.candidateIds()).containsExactly("character_001", "character_002");
                    assertThat(conflict.sourceChapterIds()).containsExactly("chapter_1", "chapter_3");
                });
    }

    @Test
    void mergeCombinesLocationsPropsForeshadowingAndSortsTimeline() {
        GlobalStoryState base = new GlobalStoryState(
                List.of(character("character_001", "林夜", List.of(), "主角", "chapter_1", List.of("chapter_1"))),
                List.of(location("location_001", "破庙", "荒废", List.of("chapter_1"))),
                List.of(timeline("timeline_002", 2, "chapter_2", "黑衣人追入破庙。")),
                List.of(),
                List.of(prop("prop_001", "短刀", "林夜的武器", List.of("chapter_1"))),
                List.of(foreshadowing("foreshadowing_001", "孩子身份可疑", "后续揭示", List.of("chapter_1"))),
                List.of()
        );
        GlobalStoryState incoming = new GlobalStoryState(
                List.of(character("character_001", "林夜", List.of(), "主角", "chapter_1", List.of("chapter_2"))),
                List.of(location("location_002", "破庙", "屋顶漏雨", List.of("chapter_2"))),
                List.of(timeline("timeline_001", 1, "chapter_1", "林夜听见孩子哭声。")),
                List.of(),
                List.of(prop("prop_002", "短刀", "刀刃被雨水打湿", List.of("chapter_2"))),
                List.of(foreshadowing("foreshadowing_002", "孩子身份可疑", "后续揭示", List.of("chapter_2"))),
                List.of()
        );

        GlobalStoryState merged = merger.merge(base, incoming);

        assertThat(merged.locations()).singleElement()
                .satisfies(location -> {
                    assertThat(location.id()).isEqualTo("location_001");
                    assertThat(location.description()).contains("荒废", "屋顶漏雨");
                    assertThat(location.sourceChapterIds()).containsExactly("chapter_1", "chapter_2");
                });
        assertThat(merged.timeline()).extracting(GlobalStoryState.TimelineEvent::eventId)
                .containsExactly("timeline_001", "timeline_002");
        assertThat(merged.props()).singleElement()
                .satisfies(prop -> {
                    assertThat(prop.description()).contains("林夜的武器", "刀刃被雨水打湿");
                    assertThat(prop.sourceChapterIds()).containsExactly("chapter_1", "chapter_2");
                });
        assertThat(merged.foreshadowing()).singleElement()
                .satisfies(item -> assertThat(item.sourceChapterIds()).containsExactly("chapter_1", "chapter_2"));
    }

    @Test
    void normalizeRemapsExistingConflictCandidateIds() {
        GlobalStoryState state = state(
                List.of(
                        character("character_001", "林夜", List.of(), "刺客", "chapter_1", List.of("chapter_1")),
                        character("character_002", "阿夜", List.of("林夜"), "刺客", "chapter_2", List.of("chapter_2"))
                ),
                List.of(),
                List.of(new GlobalStoryState.ConflictNote(
                        "conflict_001",
                        "阿夜身份是否等同林夜待确认。",
                        List.of("character_002"),
                        List.of("chapter_2")
                ))
        );

        GlobalStoryState normalized = merger.normalize(state);

        assertThat(normalized.characters()).hasSize(1);
        assertThat(normalized.conflicts()).singleElement()
                .satisfies(conflict -> assertThat(conflict.candidateIds()).containsExactly("character_001"));
    }

    private GlobalStoryState state(List<GlobalStoryState.CharacterProfile> characters,
                                   List<GlobalStoryState.Relationship> relationships,
                                   List<GlobalStoryState.ConflictNote> conflicts) {
        return new GlobalStoryState(
                characters,
                List.of(),
                List.of(),
                relationships,
                List.of(),
                List.of(),
                conflicts
        );
    }

    private GlobalStoryState.CharacterProfile character(String id,
                                                        String name,
                                                        List<String> aliases,
                                                        String role,
                                                        String firstAppearanceChapterId,
                                                        List<String> sourceChapterIds) {
        return new GlobalStoryState.CharacterProfile(
                id,
                name,
                aliases,
                role,
                name + " 描述",
                firstAppearanceChapterId,
                sourceChapterIds
        );
    }

    private GlobalStoryState.LocationProfile location(String id,
                                                      String name,
                                                      String description,
                                                      List<String> sourceChapterIds) {
        return new GlobalStoryState.LocationProfile(id, name, description, sourceChapterIds);
    }

    private GlobalStoryState.TimelineEvent timeline(String eventId,
                                                    int order,
                                                    String chapterId,
                                                    String description) {
        return new GlobalStoryState.TimelineEvent(eventId, order, chapterId, null, description);
    }

    private GlobalStoryState.Relationship relationship(String id,
                                                       String sourceCharacterId,
                                                       String targetCharacterId,
                                                       String type,
                                                       List<String> sourceChapterIds) {
        return new GlobalStoryState.Relationship(
                id,
                sourceCharacterId,
                targetCharacterId,
                type,
                type + " 描述",
                sourceChapterIds
        );
    }

    private GlobalStoryState.StoryProp prop(String id,
                                            String name,
                                            String description,
                                            List<String> sourceChapterIds) {
        return new GlobalStoryState.StoryProp(id, name, description, sourceChapterIds);
    }

    private GlobalStoryState.Foreshadowing foreshadowing(String id,
                                                         String description,
                                                         String payoffHint,
                                                         List<String> sourceChapterIds) {
        return new GlobalStoryState.Foreshadowing(id, description, payoffHint, sourceChapterIds);
    }
}
