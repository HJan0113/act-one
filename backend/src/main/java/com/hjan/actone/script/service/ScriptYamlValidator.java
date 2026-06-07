package com.hjan.actone.script.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ScriptYamlValidator {

    public static final String VALIDATION_STATUS_VALID = "valid";
    public static final String VALIDATION_STATUS_INVALID = "invalid";

    public ScriptYamlValidationResult validate(String rawYaml) {
        String cleanedYaml = clean(rawYaml);
        if (!StringUtils.hasText(cleanedYaml)) {
            return invalid(cleanedYaml, "YAML 内容不能为空");
        }

        Object parsed;
        try {
            parsed = newYamlParser().load(cleanedYaml);
        } catch (MarkedYAMLException ex) {
            return invalid(cleanedYaml, "YAML 语法错误：" + formatMarkedError(ex));
        } catch (YAMLException ex) {
            return invalid(cleanedYaml, "YAML 语法错误：" + safeMessage(ex));
        }

        if (!(parsed instanceof Map<?, ?> document)) {
            return invalid(cleanedYaml, "YAML 顶层结构必须是对象");
        }

        List<String> errors = new ArrayList<>();
        validateTopLevel(document, errors);
        if (errors.isEmpty()) {
            return new ScriptYamlValidationResult(cleanedYaml, VALIDATION_STATUS_VALID, null);
        }
        return invalid(cleanedYaml, String.join("；", errors));
    }

    public String clean(String rawYaml) {
        if (rawYaml == null) {
            return "";
        }
        String value = rawYaml.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        if (value.startsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                value = value.substring(firstLineEnd + 1, lastFence).strip();
            }
        }
        return value;
    }

    private void validateTopLevel(Map<?, ?> document, List<String> errors) {
        requireStringEquals(document, "schema_version", "1.0", errors);
        requireStringEquals(document, "kind", "screenplay_draft", errors);

        Map<?, ?> metadata = requireMap(document, "metadata", errors);
        if (metadata != null) {
            requireText(metadata, "title", errors);
        }

        Map<?, ?> source = requireMap(document, "source", errors);
        Integer chapterCount = null;
        if (source != null) {
            requireStringEquals(source, "type", "novel", errors);
            chapterCount = requirePositiveInteger(source, "chapter_count", errors);
            requireText(source, "generation_mode", errors);
        }

        List<?> characters = requireList(document, "characters", errors);
        validateCharacters(characters, errors);

        List<?> acts = requireList(document, "acts", errors);
        Set<String> actIds = validateActs(acts, errors);

        List<?> scenes = requireList(document, "scenes", errors);
        validateScenes(scenes, actIds, chapterCount, errors);
    }

    private void validateCharacters(List<?> characters, List<String> errors) {
        if (characters == null) {
            return;
        }
        Set<String> characterIds = new HashSet<>();
        for (int i = 0; i < characters.size(); i++) {
            Object value = characters.get(i);
            if (!(value instanceof Map<?, ?> character)) {
                errors.add("characters[" + i + "] 必须是对象");
                continue;
            }
            String id = optionalText(character, "id");
            if (StringUtils.hasText(id) && !characterIds.add(id)) {
                errors.add("人物 ID 重复：" + id);
            }
        }
    }

    private Set<String> validateActs(List<?> acts, List<String> errors) {
        Set<String> actIds = new HashSet<>();
        if (acts == null || acts.isEmpty()) {
            errors.add("acts 至少需要 1 个幕");
            return actIds;
        }
        for (int i = 0; i < acts.size(); i++) {
            Object value = acts.get(i);
            if (!(value instanceof Map<?, ?> act)) {
                errors.add("acts[" + i + "] 必须是对象");
                continue;
            }
            String id = requireText(act, "id", errors);
            requireText(act, "title", errors);
            Map<?, ?> chapterRange = requireMap(act, "chapter_range", errors);
            if (chapterRange != null) {
                Integer start = requirePositiveInteger(chapterRange, "start", errors);
                Integer end = requirePositiveInteger(chapterRange, "end", errors);
                if (start != null && end != null && start > end) {
                    errors.add("chapter_range.start 不能大于 end");
                }
            }
            if (StringUtils.hasText(id) && !actIds.add(id)) {
                errors.add("幕 ID 重复：" + id);
            }
        }
        return actIds;
    }

    private void validateScenes(List<?> scenes, Set<String> actIds, Integer chapterCount, List<String> errors) {
        if (scenes == null || scenes.isEmpty()) {
            errors.add("scenes 至少需要 1 个场景");
            return;
        }
        Set<String> sceneIds = new HashSet<>();
        for (int i = 0; i < scenes.size(); i++) {
            Object value = scenes.get(i);
            if (!(value instanceof Map<?, ?> scene)) {
                errors.add("scenes[" + i + "] 必须是对象");
                continue;
            }
            String sceneId = requireText(scene, "id", errors);
            if (StringUtils.hasText(sceneId) && !sceneIds.add(sceneId)) {
                errors.add("场景 ID 重复：" + sceneId);
            }
            String actId = requireText(scene, "act_id", errors);
            if (StringUtils.hasText(actId) && !actIds.contains(actId)) {
                errors.add("场景 " + sceneId + " 引用了不存在的 act_id：" + actId);
            }
            Integer chapterIndex = requirePositiveInteger(scene, "chapter_index", errors);
            if (chapterIndex != null && chapterCount != null && chapterIndex > chapterCount) {
                errors.add("场景 " + sceneId + " 的 chapter_index 超出 source.chapter_count");
            }
            requireText(scene, "source_chapter_id", errors);
            requireText(scene, "title", errors);
            requireText(scene, "location", errors);
            requireText(scene, "time", errors);
            requireText(scene, "scene_purpose", errors);
            requireText(scene, "action", errors);
            requireText(scene, "source_excerpt", errors);
            requireList(scene, "dialogues", errors);
        }
    }

    private Map<?, ?> requireMap(Map<?, ?> source, String key, List<String> errors) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        errors.add(key + " 必须是对象");
        return null;
    }

    private List<?> requireList(Map<?, ?> source, String key, List<String> errors) {
        Object value = source.get(key);
        if (value instanceof List<?> list) {
            return list;
        }
        errors.add(key + " 必须是数组");
        return null;
    }

    private String requireText(Map<?, ?> source, String key, List<String> errors) {
        String value = optionalText(source, key);
        if (!StringUtils.hasText(value)) {
            errors.add(key + " 不能为空");
        }
        return value;
    }

    private void requireStringEquals(Map<?, ?> source, String key, String expected, List<String> errors) {
        String value = requireText(source, key, errors);
        if (StringUtils.hasText(value) && !expected.equals(value)) {
            errors.add(key + " 必须为 " + expected);
        }
    }

    private Integer requirePositiveInteger(Map<?, ?> source, String key, List<String> errors) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            int parsed = number.intValue();
            if (parsed > 0) {
                return parsed;
            }
        }
        errors.add(key + " 必须是正整数");
        return null;
    }

    private String optionalText(Map<?, ?> source, String key) {
        Object value = source.get(key);
        return value instanceof String text ? text.trim() : null;
    }

    private ScriptYamlValidationResult invalid(String cleanedYaml, String message) {
        return new ScriptYamlValidationResult(cleanedYaml, VALIDATION_STATUS_INVALID, message);
    }

    private Yaml newYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(loaderOptions));
    }

    private String formatMarkedError(MarkedYAMLException ex) {
        Mark mark = ex.getProblemMark();
        String message = safeMessage(ex);
        if (mark == null) {
            return message;
        }
        return message + "，第 " + (mark.getLine() + 1) + " 行，第 " + (mark.getColumn() + 1) + " 列";
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
