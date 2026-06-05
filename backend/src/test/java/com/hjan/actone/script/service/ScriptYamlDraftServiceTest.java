package com.hjan.actone.script.service;

import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptYamlDraftServiceTest {

    private final ScriptYamlDraftService service = new ScriptYamlDraftService(new NovelChapterParser());

    @Test
    void generateFromTextBuildsEditableYamlDraft() {
        var response = service.generateFromText(new ScriptYamlGenerateRequest(
                "测试剧本",
                "作者A",
                "主角在三次选择中完成转变。",
                validNovelText()
        ));

        assertThat(response.schemaVersion()).isEqualTo("1.0");
        assertThat(response.title()).isEqualTo("测试剧本");
        assertThat(response.chapterCount()).isEqualTo(3);
        assertThat(response.yaml())
                .contains("schema_version: \"1.0\"")
                .contains("kind: \"screenplay_draft\"")
                .contains("metadata:")
                .contains("generation_mode: \"rule_based_scaffold\"")
                .contains("characters: []")
                .contains("acts:")
                .contains("scenes:")
                .contains("id: \"scene_001\"")
                .contains("title: \"第 2 章 发展\"")
                .contains("dialogues: []");
    }

    @Test
    void generateFromTextRejectsLessThanThreeChapters() {
        assertThatThrownBy(() -> service.generateFromText(new ScriptYamlGenerateRequest(null, null, null, """
                第 1 章
                内容

                第 2 章
                内容
                """)))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("至少需要 3 个章节");
    }

    private String validNovelText() {
        return """
                第 1 章 开端
                林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。

                第 2 章 发展
                她按照信里的线索找到后台暗门，却被多年未见的朋友拦住。

                第 3 章 选择
                演出开始前，林夏必须决定公开真相，还是保护朋友的秘密。
                """;
    }
}
