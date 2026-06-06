package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.DialogueDraft;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineConsistencyReport;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.ai.model.SourceTrace;
import com.hjan.actone.script.model.NovelChapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PipelineConsistencyChecker {

    public PipelineConsistencyReport check(PipelineContext context, List<NovelChapter> chapters) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        List<PipelineConsistencyReport.Issue> errors = new ArrayList<>();
        Map<String, NovelChapter> chapterById = chaptersById(chapters, errors);
        Map<Integer, NovelChapter> chapterByIndex = chaptersByIndex(chapterById.values(), errors);
        Set<String> characterIds = checkGlobalStoryState(context.globalStoryState(), chapterById.keySet(), errors);
        Map<String, ScenePlan.Scene> sceneById = checkScenePlan(
                context.scenePlan(),
                chapterById,
                chapterByIndex,
                characterIds,
                errors
        );
        checkCinematicTreatment(context.cinematicTreatment(), sceneById, characterIds, errors);
        checkDialogueDraft(context.dialogueDraft(), sceneById, characterIds, errors);
        checkSourceTrace(context.sourceTrace(), sceneById, chapterById, errors);
        return new PipelineConsistencyReport(errors, List.of());
    }

    public void validate(PipelineContext context, List<NovelChapter> chapters) {
        PipelineConsistencyReport report = check(context, chapters);
        if (!report.valid()) {
            throw new IllegalStateException("AI Pipeline 一致性校验失败：" + report.summary());
        }
    }

    private Map<String, NovelChapter> chaptersById(List<NovelChapter> chapters,
                                                   List<PipelineConsistencyReport.Issue> errors) {
        if (chapters == null || chapters.isEmpty()) {
            add(errors, "chapters.empty", "章节列表不能为空", List.of());
            return Map.of();
        }
        Map<String, NovelChapter> result = new LinkedHashMap<>();
        for (NovelChapter chapter : chapters) {
            if (result.putIfAbsent(chapter.id(), chapter) != null) {
                add(errors, "chapter.id.duplicate", "章节 ID 重复：" + chapter.id(), List.of(chapter.id()));
            }
        }
        return result;
    }

    private Map<Integer, NovelChapter> chaptersByIndex(Iterable<NovelChapter> chapters,
                                                       List<PipelineConsistencyReport.Issue> errors) {
        Map<Integer, NovelChapter> result = new LinkedHashMap<>();
        for (NovelChapter chapter : chapters) {
            if (result.putIfAbsent(chapter.index(), chapter) != null) {
                add(errors, "chapter.index.duplicate", "章节序号重复：" + chapter.index(), List.of(chapter.id()));
            }
        }
        return result;
    }

    private Set<String> checkGlobalStoryState(GlobalStoryState state,
                                              Set<String> knownChapterIds,
                                              List<PipelineConsistencyReport.Issue> errors) {
        if (state == null || state.characters().isEmpty()) {
            add(errors, "global.characters.empty", "全局故事状态缺少人物", List.of());
            return Set.of();
        }
        Set<String> characterIds = new LinkedHashSet<>();
        state.characters().forEach(character -> {
            addUnique(characterIds, character.id(), "character.id.duplicate", "人物 ID 重复", errors);
            requireKnownChapter(character.firstAppearanceChapterId(), knownChapterIds, "character.firstAppearance.unknown", errors);
            requireKnownChapters(character.sourceChapterIds(), knownChapterIds, "character.source.unknown", errors);
        });

        Set<String> locationIds = new LinkedHashSet<>();
        state.locations().forEach(location -> {
            addUnique(locationIds, location.id(), "location.id.duplicate", "地点 ID 重复", errors);
            requireKnownChapters(location.sourceChapterIds(), knownChapterIds, "location.source.unknown", errors);
        });

        Set<String> eventIds = new LinkedHashSet<>();
        state.timeline().forEach(event -> {
            addUnique(eventIds, event.eventId(), "timeline.id.duplicate", "时间线事件 ID 重复", errors);
            requireKnownChapter(event.chapterId(), knownChapterIds, "timeline.chapter.unknown", errors);
        });

        state.relationships().forEach(relationship -> {
            requireKnownCharacter(relationship.sourceCharacterId(), characterIds, "relationship.character.unknown", errors);
            requireKnownCharacter(relationship.targetCharacterId(), characterIds, "relationship.character.unknown", errors);
            requireKnownChapters(relationship.sourceChapterIds(), knownChapterIds, "relationship.source.unknown", errors);
        });
        state.props().forEach(prop -> requireKnownChapters(prop.sourceChapterIds(), knownChapterIds, "prop.source.unknown", errors));
        state.foreshadowing().forEach(item ->
                requireKnownChapters(item.sourceChapterIds(), knownChapterIds, "foreshadowing.source.unknown", errors)
        );
        state.conflicts().forEach(conflict -> {
            conflict.candidateIds()
                    .forEach(candidateId -> requireKnownCharacter(candidateId, characterIds, "conflict.character.unknown", errors));
            requireKnownChapters(conflict.sourceChapterIds(), knownChapterIds, "conflict.source.unknown", errors);
        });
        return characterIds;
    }

    private Map<String, ScenePlan.Scene> checkScenePlan(ScenePlan scenePlan,
                                                        Map<String, NovelChapter> chapterById,
                                                        Map<Integer, NovelChapter> chapterByIndex,
                                                        Set<String> characterIds,
                                                        List<PipelineConsistencyReport.Issue> errors) {
        if (scenePlan == null || scenePlan.scenes().isEmpty()) {
            add(errors, "scenePlan.empty", "场景规划不能为空", List.of());
            return Map.of();
        }
        Map<String, ScenePlan.Scene> sceneById = new LinkedHashMap<>();
        for (int i = 0; i < scenePlan.scenes().size(); i++) {
            ScenePlan.Scene scene = scenePlan.scenes().get(i);
            addUnique(sceneById, scene.id(), scene, "scene.id.duplicate", "场景 ID 重复", errors);
            String expectedSceneId = "scene_%03d".formatted(i + 1);
            if (!expectedSceneId.equals(scene.id())) {
                add(errors, "scene.id.unstable", "场景 ID 未按顺序稳定编号：" + scene.id(), List.of(scene.id()));
            }
            if (!scene.actId().matches("act_\\d+")) {
                add(errors, "scene.act.invalid", "场景 act_id 格式非法：" + scene.actId(), List.of(scene.id(), scene.actId()));
            }
            NovelChapter byId = chapterById.get(scene.sourceChapterId());
            NovelChapter byIndex = chapterByIndex.get(scene.chapterIndex());
            if (byId == null || byIndex == null || !Objects.equals(byId, byIndex)) {
                add(errors, "scene.source.mismatch", "场景来源章节与序号不一致：" + scene.id(),
                        List.of(scene.id(), scene.sourceChapterId()));
            }
            if (scene.characters().isEmpty()) {
                add(errors, "scene.characters.empty", "场景缺少出场人物：" + scene.id(), List.of(scene.id()));
            }
            scene.characters().forEach(characterId ->
                    requireKnownCharacter(characterId, characterIds, "scene.character.unknown", errors)
            );
            scene.beats().forEach(beat -> beat.characterIds().forEach(characterId -> {
                requireKnownCharacter(characterId, characterIds, "scene.beat.character.unknown", errors);
                requireSceneCharacter(scene, characterId, "scene.beat.character.absent", errors);
            }));
        }
        return sceneById;
    }

    private void checkCinematicTreatment(CinematicTreatment treatment,
                                         Map<String, ScenePlan.Scene> sceneById,
                                         Set<String> characterIds,
                                         List<PipelineConsistencyReport.Issue> errors) {
        if (treatment == null) {
            add(errors, "cinematicTreatment.empty", "心理影视化结果不能为空", List.of());
            return;
        }
        checkSceneCoverage(
                "cinematic.scene",
                sceneById,
                treatment.scenes().stream().map(CinematicTreatment.SceneTreatment::sceneId).toList(),
                errors
        );
        treatment.scenes().forEach(sceneTreatment -> {
            ScenePlan.Scene scene = sceneById.get(sceneTreatment.sceneId());
            if (scene == null) {
                return;
            }
            requireSameSceneSource(
                    scene,
                    sceneTreatment.sceneId(),
                    sceneTreatment.sourceChapterId(),
                    sceneTreatment.chapterIndex(),
                    "cinematic.scene.source.mismatch",
                    errors
            );
            sceneTreatment.transformations().forEach(transformation ->
                    transformation.characterIds().forEach(characterId -> {
                        requireKnownCharacter(characterId, characterIds, "cinematic.character.unknown", errors);
                        requireSceneCharacter(scene, characterId, "cinematic.character.absent", errors);
                    })
            );
        });
    }

    private void checkDialogueDraft(DialogueDraft draft,
                                    Map<String, ScenePlan.Scene> sceneById,
                                    Set<String> characterIds,
                                    List<PipelineConsistencyReport.Issue> errors) {
        if (draft == null) {
            add(errors, "dialogueDraft.empty", "对白草稿不能为空", List.of());
            return;
        }
        checkSceneCoverage(
                "dialogue.scene",
                sceneById,
                draft.scenes().stream().map(DialogueDraft.SceneDialogues::sceneId).toList(),
                errors
        );
        draft.scenes().forEach(sceneDialogues -> {
            ScenePlan.Scene scene = sceneById.get(sceneDialogues.sceneId());
            if (scene == null) {
                return;
            }
            requireSameSceneSource(
                    scene,
                    sceneDialogues.sceneId(),
                    sceneDialogues.sourceChapterId(),
                    sceneDialogues.chapterIndex(),
                    "dialogue.scene.source.mismatch",
                    errors
            );
            sceneDialogues.dialogues().forEach(dialogue -> {
                if (dialogue.speakerCharacterId() == null) {
                    return;
                }
                requireKnownCharacter(dialogue.speakerCharacterId(), characterIds, "dialogue.character.unknown", errors);
                requireSceneCharacter(scene, dialogue.speakerCharacterId(), "dialogue.character.absent", errors);
            });
        });
    }

    private void checkSourceTrace(SourceTrace sourceTrace,
                                  Map<String, ScenePlan.Scene> sceneById,
                                  Map<String, NovelChapter> chapterById,
                                  List<PipelineConsistencyReport.Issue> errors) {
        if (sourceTrace == null) {
            add(errors, "sourceTrace.empty", "源文追溯结果不能为空", List.of());
            return;
        }
        checkSceneCoverage(
                "sourceTrace.scene",
                sceneById,
                sourceTrace.scenes().stream().map(SourceTrace.SceneTrace::sceneId).toList(),
                errors
        );
        sourceTrace.scenes().forEach(trace -> {
            ScenePlan.Scene scene = sceneById.get(trace.sceneId());
            if (scene == null) {
                return;
            }
            requireSameSceneSource(
                    scene,
                    trace.sceneId(),
                    trace.sourceChapterId(),
                    trace.chapterIndex(),
                    "sourceTrace.scene.source.mismatch",
                    errors
            );
            NovelChapter chapter = chapterById.get(trace.sourceChapterId());
            trace.evidence().forEach(evidence -> validateEvidence(trace, chapter, evidence, errors));
        });
    }

    private void validateEvidence(SourceTrace.SceneTrace trace,
                                  NovelChapter chapter,
                                  SourceEvidence evidence,
                                  List<PipelineConsistencyReport.Issue> errors) {
        if (chapter == null) {
            add(errors, "sourceTrace.chapter.unknown", "源文追溯章节不存在：" + trace.sourceChapterId(),
                    List.of(trace.sceneId(), trace.sourceChapterId()));
            return;
        }
        if (!trace.sourceChapterId().equals(evidence.sourceChapterId()) || !trace.chapterIndex().equals(evidence.chapterIndex())) {
            add(errors, "sourceTrace.evidence.source.mismatch", "源文证据与场景来源不一致：" + trace.sceneId(),
                    List.of(trace.sceneId(), evidence.sourceChapterId()));
        }
        if (!normalize(chapter.content()).contains(normalize(evidence.excerpt()))) {
            add(errors, "sourceTrace.evidence.missing", "源文证据无法回查原文：" + trace.sceneId(),
                    List.of(trace.sceneId(), evidence.sourceChapterId()));
        }
    }

    private void checkSceneCoverage(String codePrefix,
                                    Map<String, ScenePlan.Scene> plannedScenes,
                                    List<String> actualSceneIds,
                                    List<PipelineConsistencyReport.Issue> errors) {
        Map<String, Long> countBySceneId = actualSceneIds.stream()
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        countBySceneId.forEach((sceneId, count) -> {
            if (count > 1) {
                add(errors, codePrefix + ".duplicate", "场景引用重复：" + sceneId, List.of(sceneId));
            }
            if (!plannedScenes.containsKey(sceneId)) {
                add(errors, codePrefix + ".unknown", "引用了不存在的场景：" + sceneId, List.of(sceneId));
            }
        });
        plannedScenes.keySet().stream()
                .filter(sceneId -> !countBySceneId.containsKey(sceneId))
                .forEach(sceneId -> add(errors, codePrefix + ".missing", "缺少场景结果：" + sceneId, List.of(sceneId)));
    }

    private void requireSameSceneSource(ScenePlan.Scene scene,
                                        String sceneId,
                                        String sourceChapterId,
                                        Integer chapterIndex,
                                        String code,
                                        List<PipelineConsistencyReport.Issue> errors) {
        if (!scene.id().equals(sceneId)
                || !scene.sourceChapterId().equals(sourceChapterId)
                || !scene.chapterIndex().equals(chapterIndex)) {
            add(errors, code, "场景来源引用不一致：" + scene.id(), List.of(scene.id(), sourceChapterId));
        }
    }

    private void requireSceneCharacter(ScenePlan.Scene scene,
                                       String characterId,
                                       String code,
                                       List<PipelineConsistencyReport.Issue> errors) {
        if (!scene.characters().contains(characterId)) {
            add(errors, code, "人物未列入场景出场列表：" + characterId, List.of(scene.id(), characterId));
        }
    }

    private void requireKnownCharacter(String characterId,
                                       Set<String> knownCharacterIds,
                                       String code,
                                       List<PipelineConsistencyReport.Issue> errors) {
        if (!knownCharacterIds.contains(characterId)) {
            add(errors, code, "人物引用不存在：" + characterId, List.of(characterId));
        }
    }

    private void requireKnownChapter(String chapterId,
                                     Set<String> knownChapterIds,
                                     String code,
                                     List<PipelineConsistencyReport.Issue> errors) {
        if (!knownChapterIds.contains(chapterId)) {
            add(errors, code, "章节引用不存在：" + chapterId, List.of(chapterId));
        }
    }

    private void requireKnownChapters(List<String> chapterIds,
                                      Set<String> knownChapterIds,
                                      String code,
                                      List<PipelineConsistencyReport.Issue> errors) {
        chapterIds.forEach(chapterId -> requireKnownChapter(chapterId, knownChapterIds, code, errors));
    }

    private void addUnique(Set<String> ids,
                           String id,
                           String code,
                           String messagePrefix,
                           List<PipelineConsistencyReport.Issue> errors) {
        if (!ids.add(id)) {
            add(errors, code, messagePrefix + "：" + id, List.of(id));
        }
    }

    private <T> void addUnique(Map<String, T> values,
                               String id,
                               T value,
                               String code,
                               String messagePrefix,
                               List<PipelineConsistencyReport.Issue> errors) {
        if (values.putIfAbsent(id, value) != null) {
            add(errors, code, messagePrefix + "：" + id, List.of(id));
        }
    }

    private void add(List<PipelineConsistencyReport.Issue> errors,
                     String code,
                     String message,
                     List<String> refs) {
        errors.add(new PipelineConsistencyReport.Issue(code, message, refs));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
