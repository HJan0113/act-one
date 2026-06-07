package com.hjan.actone.infra.ai.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LLMResponseCleanerTest {

    private final LLMResponseCleaner cleaner = new LLMResponseCleaner();

    @Test
    void cleansMarkdownFenceBomAndLineEndings() {
        String raw = "\uFEFF  ```yaml\r\nschema_version: \"1.0\"\r\nkind: screenplay_draft\r\n```  ";

        String cleaned = cleaner.clean(raw);

        assertThat(cleaned).isEqualTo("""
                schema_version: "1.0"
                kind: screenplay_draft""");
    }

    @Test
    void keepsUnfencedContentAndTrimsOuterWhitespace() {
        assertThat(cleaner.clean("  第一场\n角色进入房间。  "))
                .isEqualTo("第一场\n角色进入房间。");
    }

    @Test
    void toleratesMissingClosingFenceForModelRecovery() {
        assertThat(cleaner.clean("```json\n{\"summary\":\"完成\"}"))
                .isEqualTo("{\"summary\":\"完成\"}");
    }

    @Test
    void returnsNullForNullResponse() {
        assertThat(cleaner.clean(null)).isNull();
    }
}
