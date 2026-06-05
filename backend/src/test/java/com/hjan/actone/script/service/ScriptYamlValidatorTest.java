package com.hjan.actone.script.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptYamlValidatorTest {

    private final ScriptYamlValidator validator = new ScriptYamlValidator();

    @Test
    void validateAcceptsValidYamlAndCleansMarkdownFence() {
        ScriptYamlValidationResult result = validator.validate("""
                ```yaml
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "测试剧本"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "rule_based_scaffold"
                characters: []
                acts:
                  - id: "act_1"
                    title: "第一幕"
                    chapter_range:
                      start: 1
                      end: 3
                scenes:
                  - id: "scene_001"
                    act_id: "act_1"
                    chapter_index: 1
                    source_chapter_id: "chapter_1"
                    title: "第 1 章"
                    location: "旧剧场"
                    time: "夜晚"
                    scene_purpose: "建立冲突"
                    action: "林夏推门进入。"
                    dialogues: []
                    source_excerpt: "林夏推开旧剧场的门。"
                ```
                """);

        assertThat(result.valid()).isTrue();
        assertThat(result.cleanedYaml()).doesNotContain("```");
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void validateRejectsYamlSyntaxErrorWithPosition() {
        ScriptYamlValidationResult result = validator.validate("schema_version: [");

        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("invalid");
        assertThat(result.errorMessage()).contains("YAML 语法错误").contains("第 1 行");
    }

    @Test
    void validateRejectsBrokenSceneReferences() {
        ScriptYamlValidationResult result = validator.validate("""
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "测试剧本"
                source:
                  type: "novel"
                  chapter_count: 1
                  generation_mode: "rule_based_scaffold"
                characters: []
                acts:
                  - id: "act_1"
                    title: "第一幕"
                    chapter_range:
                      start: 1
                      end: 1
                scenes:
                  - id: "scene_001"
                    act_id: "missing_act"
                    chapter_index: 2
                    source_chapter_id: "chapter_2"
                    title: "第 2 章"
                    location: "旧剧场"
                    time: "夜晚"
                    scene_purpose: "建立冲突"
                    action: "林夏推门进入。"
                    dialogues: []
                    source_excerpt: "林夏推开旧剧场的门。"
                """);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage())
                .contains("不存在的 act_id")
                .contains("chapter_index 超出 source.chapter_count");
    }

    @Test
    void validateRejectsEmptyScenes() {
        ScriptYamlValidationResult result = validator.validate("""
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "测试剧本"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "rule_based_scaffold"
                characters: []
                acts:
                  - id: "act_1"
                    title: "第一幕"
                    chapter_range:
                      start: 1
                      end: 3
                scenes: []
                """);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("scenes 至少需要 1 个场景");
    }
}
