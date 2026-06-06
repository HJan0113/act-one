package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.DialogueDraft;
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

public class DeterministicSourceTraceStage implements SourceTraceStage {

    @Override
    public SourceTrace trace(PipelineContext context, List<NovelChapter> chapters) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        if (context.scenePlan() == null || context.scenePlan().scenes().isEmpty()) {
            throw new IllegalArgumentException("scenePlan 不能为空");
        }
        if (context.cinematicTreatment() == null || context.cinematicTreatment().scenes().isEmpty()) {
            throw new IllegalArgumentException("cinematicTreatment 不能为空");
        }
        if (context.dialogueDraft() == null || context.dialogueDraft().scenes().isEmpty()) {
            throw new IllegalArgumentException("dialogueDraft 不能为空");
        }
        Map<String, NovelChapter> chapterById = chaptersById(chapters);
        Map<String, CinematicTreatment.SceneTreatment> treatmentBySceneId = indexTreatments(context.cinematicTreatment());
        Map<String, DialogueDraft.SceneDialogues> dialoguesBySceneId = indexDialogues(context.dialogueDraft());
        Set<String> plannedSceneIds = context.scenePlan().scenes().stream()
                .map(ScenePlan.Scene::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!plannedSceneIds.containsAll(treatmentBySceneId.keySet())
                || !plannedSceneIds.containsAll(dialoguesBySceneId.keySet())) {
            throw new IllegalArgumentException("源文追溯场景引用非法");
        }

        List<SourceTrace.SceneTrace> traces = new ArrayList<>();
        for (ScenePlan.Scene scene : context.scenePlan().scenes()) {
            NovelChapter chapter = chapterById.get(scene.sourceChapterId());
            if (chapter == null) {
                throw new IllegalArgumentException("源文追溯章节引用非法");
            }
            if (chapter.index() != scene.chapterIndex()) {
                throw new IllegalArgumentException("源文追溯章节序号不一致");
            }

            EvidenceCollector evidence = new EvidenceCollector(chapter);
            evidence.add(scene.sourceExcerpt());
            scene.beats().forEach(beat -> evidence.add(beat.sourceExcerpt()));

            CinematicTreatment.SceneTreatment treatment = treatmentBySceneId.get(scene.id());
            if (treatment == null) {
                throw new IllegalArgumentException("源文追溯缺少心理影视化证据");
            }
            validateSceneSource(scene, treatment.sceneId(), treatment.sourceChapterId(), treatment.chapterIndex());
            treatment.transformations().forEach(transformation -> evidence.add(transformation.sourceExcerpt()));

            DialogueDraft.SceneDialogues dialogues = dialoguesBySceneId.get(scene.id());
            if (dialogues == null) {
                throw new IllegalArgumentException("源文追溯缺少对白证据");
            }
            validateSceneSource(scene, dialogues.sceneId(), dialogues.sourceChapterId(), dialogues.chapterIndex());
            dialogues.dialogues().forEach(dialogue -> evidence.add(dialogue.sourceExcerpt()));

            traces.add(new SourceTrace.SceneTrace(
                    scene.id(),
                    scene.chapterIndex(),
                    scene.sourceChapterId(),
                    scene.sourceExcerpt(),
                    evidence.toList()
            ));
        }
        return new SourceTrace(traces);
    }

    private Map<String, NovelChapter> chaptersById(List<NovelChapter> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            throw new IllegalArgumentException("chapters 不能为空");
        }
        return chapters.stream()
                .collect(Collectors.toMap(
                        NovelChapter::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, CinematicTreatment.SceneTreatment> indexTreatments(CinematicTreatment treatment) {
        return treatment.scenes().stream()
                .collect(Collectors.toMap(
                        CinematicTreatment.SceneTreatment::sceneId,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("源文追溯场景证据重复");
                        },
                        LinkedHashMap::new
                ));
    }

    private Map<String, DialogueDraft.SceneDialogues> indexDialogues(DialogueDraft draft) {
        return draft.scenes().stream()
                .collect(Collectors.toMap(
                        DialogueDraft.SceneDialogues::sceneId,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("源文追溯场景证据重复");
                        },
                        LinkedHashMap::new
                ));
    }

    private void validateSceneSource(ScenePlan.Scene scene,
                                     String sceneId,
                                     String sourceChapterId,
                                     Integer chapterIndex) {
        if (!scene.id().equals(sceneId)) {
            throw new IllegalArgumentException("源文追溯场景引用非法");
        }
        if (!scene.sourceChapterId().equals(sourceChapterId) || !scene.chapterIndex().equals(chapterIndex)) {
            throw new IllegalArgumentException("源文追溯场景来源不一致");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private final class EvidenceCollector {

        private final NovelChapter chapter;
        private final String normalizedContent;
        private final Map<String, SourceEvidence> evidenceByExcerpt = new LinkedHashMap<>();

        private EvidenceCollector(NovelChapter chapter) {
            this.chapter = chapter;
            this.normalizedContent = normalize(chapter.content());
        }

        private void add(String excerpt) {
            SourceEvidence evidence = new SourceEvidence(chapter.id(), chapter.index(), excerpt);
            if (!normalizedContent.contains(normalize(evidence.excerpt()))) {
                throw new IllegalArgumentException("源文摘录无法回查原文");
            }
            evidenceByExcerpt.putIfAbsent(evidence.excerpt(), evidence);
        }

        private List<SourceEvidence> toList() {
            return List.copyOf(evidenceByExcerpt.values());
        }
    }
}
