package com.hjan.actone.script.service;

import com.hjan.actone.script.model.NovelChapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NovelChapterParserTest {

    private final NovelChapterParser parser = new NovelChapterParser();

    @Test
    void parseRecognizesChineseChapterHeadings() {
        String text = """
                第 1 章 初遇

                第一章内容。

                第 2 章 转折

                第二章内容。

                第 3 章 抉择

                第三章内容。
                """;

        List<NovelChapter> chapters = parser.parse(text);

        assertThat(chapters).hasSize(3);
        assertThat(chapters.get(0).title()).isEqualTo("第 1 章 初遇");
        assertThat(chapters.get(2).content()).contains("第三章内容");
    }
}

