package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceTrace;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.service.ScriptYamlValidationResult;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScriptYamlFactConstraintChecker {

    public static final String ERROR_PREFIX = "事实约束失败：";

    public ScriptYamlValidationResult validate(PipelineContext context,
                                               String cleanedYaml,
                                               List<NovelChapter> chapters) {
        Objects.requireNonNull(context, "PipelineContext 不能为空");
        String yaml = cleanedYaml == null ? "" : cleanedYaml.strip();
        if (!hasText(yaml)) {
            return invalid(yaml, "YAML 内容不能为空");
        }

        Object parsed;
        try {
            parsed = newYamlParser().load(yaml);
        } catch (YAMLException ex) {
            return invalid(yaml, "YAML 无法解析，需先通过 Schema 校验");
        }
        if (!(parsed instanceof Map<?, ?> document)) {
            return invalid(yaml, "YAML 顶层结构必须是对象");
        }

        FactIndex index = FactIndex.from(context, chapters);
        List<String> errors = new ArrayList<>();
        validateCharacters(asList(document.get("characters")), index, errors);
        validateScenes(asList(document.get("scenes")), index, errors);

        if (errors.isEmpty()) {
            return new ScriptYamlValidationResult(yaml, ScriptYamlValidator.VALIDATION_STATUS_VALID, null);
        }
        return invalid(yaml, ERROR_PREFIX + String.join("；", errors));
    }

    private void validateCharacters(List<?> characters, FactIndex index, List<String> errors) {
        if (characters == null) {
            return;
        }
        for (int i = 0; i < characters.size(); i++) {
            if (!(characters.get(i) instanceof Map<?, ?> character)) {
                continue;
            }
            String id = optionalText(character, "id");
            String name = optionalText(character, "name");
            if (hasText(id) && !index.characterById().containsKey(id)) {
                errors.add("characters[" + i + "] 使用了无来源人物 ID：" + id);
            }
            if (hasText(name) && !index.characterIdByAlias().containsKey(normalizeName(name))) {
                errors.add("characters[" + i + "] 使用了无来源人物：" + name);
            }
        }
    }

    private void validateScenes(List<?> scenes, FactIndex index, List<String> errors) {
        if (scenes == null) {
            return;
        }
        for (int i = 0; i < scenes.size(); i++) {
            if (!(scenes.get(i) instanceof Map<?, ?> scene)) {
                continue;
            }
            String sceneId = optionalText(scene, "id");
            SourceTrace.SceneTrace trace = index.traceBySceneId().get(sceneId);
            ScenePlan.Scene plannedScene = index.planBySceneId().get(sceneId);
            if (trace == null || plannedScene == null) {
                errors.add("scene[" + i + "] 引用了无来源场景：" + nullToUnknown(sceneId));
                continue;
            }

            validateSceneSource(scene, trace, plannedScene, index, errors);
            validateSceneAiExpansion(scene, trace, plannedScene, index, errors);
            validateSceneCharacters(scene, plannedScene, index, errors);
        }
    }

    private void validateSceneSource(Map<?, ?> scene,
                                     SourceTrace.SceneTrace trace,
                                     ScenePlan.Scene plannedScene,
                                     FactIndex index,
                                     List<String> errors) {
        String sceneId = trace.sceneId();
        String sourceChapterId = optionalText(scene, "source_chapter_id");
        Integer chapterIndex = optionalInteger(scene, "chapter_index");
        if (!Objects.equals(trace.sourceChapterId(), sourceChapterId)
                || !Objects.equals(trace.chapterIndex(), chapterIndex)
                || !Objects.equals(plannedScene.sourceChapterId(), sourceChapterId)
                || !Objects.equals(plannedScene.chapterIndex(), chapterIndex)) {
            errors.add("场景 " + sceneId + " 来源章节与 source_trace 不一致");
        }

        String sourceExcerpt = optionalText(scene, "source_excerpt");
        if (!hasText(sourceExcerpt)) {
            errors.add("场景 " + sceneId + " 缺少 source_excerpt");
            return;
        }
        if (!isSourceBacked(sourceExcerpt, trace, plannedScene, index)) {
            errors.add("场景 " + sceneId + " 的 source_excerpt 无法回查源文");
        }
    }

    private void validateSceneAiExpansion(Map<?, ?> scene,
                                          SourceTrace.SceneTrace trace,
                                          ScenePlan.Scene plannedScene,
                                          FactIndex index,
                                          List<String> errors) {
        String sceneId = trace.sceneId();
        Boolean aiExpansion = optionalBoolean(scene, "ai_expansion");
        if (aiExpansion == null) {
            errors.add("场景 " + sceneId + " 必须标记 ai_expansion");
            return;
        }
        if (aiExpansion) {
            return;
        }

        String action = optionalText(scene, "action");
        if (hasText(action) && !isSourceBacked(action, trace, plannedScene, index)) {
            errors.add("场景 " + sceneId + " 的 action 超出来源证据，需标记 ai_expansion=true");
        }
        List<?> dialogues = asList(scene.get("dialogues"));
        if (dialogues == null) {
            return;
        }
        for (int i = 0; i < dialogues.size(); i++) {
            if (!(dialogues.get(i) instanceof Map<?, ?> dialogue)) {
                continue;
            }
            String line = optionalText(dialogue, "line");
            if (hasText(line) && !isSourceBacked(line, trace, plannedScene, index)) {
                errors.add("场景 " + sceneId + " 的 dialogue[" + i + "] 超出来源证据，需标记 ai_expansion=true");
            }
        }
    }

    private void validateSceneCharacters(Map<?, ?> scene,
                                         ScenePlan.Scene plannedScene,
                                         FactIndex index,
                                         List<String> errors) {
        String sceneId = plannedScene.id();
        Set<String> plannedCharacterIds = new LinkedHashSet<>(plannedScene.characters());
        validateKnownMentions(sceneId, optionalText(scene, "title"), plannedCharacterIds, index, errors);
        validateKnownMentions(sceneId, optionalText(scene, "scene_purpose"), plannedCharacterIds, index, errors);
        validateKnownMentions(sceneId, optionalText(scene, "action"), plannedCharacterIds, index, errors);

        List<?> dialogues = asList(scene.get("dialogues"));
        if (dialogues == null) {
            return;
        }
        for (int i = 0; i < dialogues.size(); i++) {
            if (!(dialogues.get(i) instanceof Map<?, ?> dialogue)) {
                continue;
            }
            String speaker = optionalText(dialogue, "speaker");
            String speakerCharacterId = optionalText(dialogue, "speaker_character_id");
            if (isNarrator(speaker)) {
                validateKnownMentions(sceneId, optionalText(dialogue, "line"), plannedCharacterIds, index, errors);
                continue;
            }
            String resolvedCharacterId = resolveSpeakerCharacterId(speaker, speakerCharacterId, index);
            if (!hasText(resolvedCharacterId)) {
                errors.add("场景 " + sceneId + " 的 dialogue[" + i + "] 使用了无来源说话人：" + nullToUnknown(speaker));
                continue;
            }
            if (!plannedCharacterIds.contains(resolvedCharacterId)) {
                errors.add("场景 " + sceneId + " 的 dialogue[" + i + "] 说话人未在场景出场列表中："
                        + nullToUnknown(speaker));
            }
            validateKnownMentions(sceneId, optionalText(dialogue, "line"), plannedCharacterIds, index, errors);
        }
    }

    private String resolveSpeakerCharacterId(String speaker, String speakerCharacterId, FactIndex index) {
        if (hasText(speakerCharacterId)) {
            if (!index.characterById().containsKey(speakerCharacterId)) {
                return null;
            }
            return speakerCharacterId;
        }
        if (!hasText(speaker)) {
            return null;
        }
        return index.characterIdByAlias().get(normalizeName(speaker));
    }

    private void validateKnownMentions(String sceneId,
                                       String text,
                                       Set<String> plannedCharacterIds,
                                       FactIndex index,
                                       List<String> errors) {
        if (!hasText(text)) {
            return;
        }
        String normalizedText = normalizeForSource(text);
        Set<String> reported = new LinkedHashSet<>();
        index.characterAliases().forEach(alias -> {
            if (alias.normalizedAlias().length() < 2 || !normalizedText.contains(alias.normalizedAlias())) {
                return;
            }
            if (!plannedCharacterIds.contains(alias.characterId()) && reported.add(alias.characterId())) {
                errors.add("场景 " + sceneId + " 出现未列入出场人物的角色：" + alias.displayName());
            }
        });
    }

    private boolean isSourceBacked(String text,
                                   SourceTrace.SceneTrace trace,
                                   ScenePlan.Scene plannedScene,
                                   FactIndex index) {
        String normalizedText = normalizeForSource(text);
        if (!hasText(normalizedText)) {
            return true;
        }
        List<String> sources = new ArrayList<>();
        sources.add(trace.sourceExcerpt());
        sources.add(plannedScene.sourceExcerpt());
        trace.evidence().forEach(evidence -> sources.add(evidence.excerpt()));
        plannedScene.beats().forEach(beat -> sources.add(beat.sourceExcerpt()));
        NovelChapter chapter = index.chapterById().get(trace.sourceChapterId());
        if (chapter != null) {
            sources.add(chapter.content());
        }
        return sources.stream().anyMatch(source -> {
            String normalizedSource = normalizeForSource(source);
            return normalizedSource.contains(normalizedText);
        });
    }

    private Boolean optionalBoolean(Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(text.trim())) {
                return false;
            }
        }
        return null;
    }

    private Integer optionalInteger(Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private String optionalText(Map<?, ?> source, String key) {
        Object value = source.get(key);
        return value instanceof String text ? text.trim() : null;
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : null;
    }

    private ScriptYamlValidationResult invalid(String cleanedYaml, String message) {
        return new ScriptYamlValidationResult(cleanedYaml, ScriptYamlValidator.VALIDATION_STATUS_INVALID, message);
    }

    private Yaml newYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(loaderOptions));
    }

    private boolean isNarrator(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = normalizeName(value);
        return Set.of("旁白", "叙述者", "画外音", "narrator", "vo", "voiceover").contains(normalized);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private String normalizeForSource(String value) {
        return value == null
                ? ""
                : value.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）《》【】…—·]+", "").toLowerCase();
    }

    private String nullToUnknown(String value) {
        return hasText(value) ? value : "未知";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CharacterAlias(String characterId, String normalizedAlias, String displayName) {
    }

    private record FactIndex(
            Map<String, GlobalStoryState.CharacterProfile> characterById,
            Map<String, String> characterIdByAlias,
            List<CharacterAlias> characterAliases,
            Map<String, ScenePlan.Scene> planBySceneId,
            Map<String, SourceTrace.SceneTrace> traceBySceneId,
            Map<String, NovelChapter> chapterById
    ) {

        private static FactIndex from(PipelineContext context, List<NovelChapter> chapters) {
            Map<String, GlobalStoryState.CharacterProfile> characterById = context.globalStoryState()
                    .characters()
                    .stream()
                    .collect(Collectors.toMap(
                            GlobalStoryState.CharacterProfile::id,
                            Function.identity(),
                            (first, ignored) -> first,
                            LinkedHashMap::new
                    ));
            Map<String, String> characterIdByAlias = new LinkedHashMap<>();
            List<CharacterAlias> characterAliases = new ArrayList<>();
            characterById.values().forEach(character -> {
                addAlias(characterIdByAlias, characterAliases, character.id(), character.name());
                character.aliases().forEach(alias ->
                        addAlias(characterIdByAlias, characterAliases, character.id(), alias)
                );
            });
            Map<String, ScenePlan.Scene> planBySceneId = context.scenePlan() == null
                    ? Map.of()
                    : context.scenePlan().scenes().stream().collect(Collectors.toMap(
                            ScenePlan.Scene::id,
                            Function.identity(),
                            (first, ignored) -> first,
                            LinkedHashMap::new
                    ));
            Map<String, SourceTrace.SceneTrace> traceBySceneId = context.sourceTrace() == null
                    ? Map.of()
                    : context.sourceTrace().scenes().stream().collect(Collectors.toMap(
                            SourceTrace.SceneTrace::sceneId,
                            Function.identity(),
                            (first, ignored) -> first,
                            LinkedHashMap::new
                    ));
            Map<String, NovelChapter> chapterById = chapters == null
                    ? Map.of()
                    : chapters.stream().collect(Collectors.toMap(
                            NovelChapter::id,
                            Function.identity(),
                            (first, ignored) -> first,
                            LinkedHashMap::new
                    ));
            return new FactIndex(
                    characterById,
                    characterIdByAlias,
                    characterAliases,
                    planBySceneId,
                    traceBySceneId,
                    chapterById
            );
        }

        private static void addAlias(Map<String, String> characterIdByAlias,
                                     List<CharacterAlias> characterAliases,
                                     String characterId,
                                     String alias) {
            if (alias == null || alias.isBlank()) {
                return;
            }
            String normalized = alias.replaceAll("\\s+", "").toLowerCase();
            characterIdByAlias.putIfAbsent(normalized, characterId);
            characterAliases.add(new CharacterAlias(characterId, normalized, alias));
        }
    }
}
