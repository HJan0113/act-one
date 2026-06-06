package com.hjan.actone.script.service;

import com.hjan.actone.script.model.NovelChapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

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

    @ParameterizedTest
    @MethodSource("novelNoiseSamples")
    void cleanupNormalizesCommonNovelNoise(String input, String expected) {
        assertThat(parser.cleanup(input)).isEqualTo(expected);
    }

    @Test
    void parseHandlesFullWidthHeadingsAndPageBoilerplate() {
        String text = """
                \uFEFF　　第１章　开端
                www.example.com
                林夏推开旧剧场的门。
                第 1 页

                第２章　转折
                -- 2 --
                她找到后台暗门。
                请点击下一页继续阅读

                第３章　选择
                她决定公开真相。
                """;

        List<NovelChapter> chapters = parser.parse(text);

        assertThat(chapters).hasSize(3);
        assertThat(chapters.get(0).title()).isEqualTo("第1章 开端");
        assertThat(chapters.get(0).content())
                .contains("林夏推开旧剧场的门")
                .doesNotContain("www.example.com", "第 1 页");
        assertThat(chapters.get(1).content()).doesNotContain("-- 2 --", "请点击下一页");
    }

    @Test
    void parseSkipsRepeatedHeadingAndEmptyChapters() {
        String text = """
                第1章 开端
                第1章 开端
                林夏进入旧剧场。

                第2章 空白

                第3章 转折
                她看见朋友留下的信。

                第4章 结局
                她做出选择。
                """;

        List<NovelChapter> chapters = parser.parse(text);

        assertThat(chapters).hasSize(3);
        assertThat(chapters).extracting(NovelChapter::index).containsExactly(1, 2, 3);
        assertThat(chapters).extracting(NovelChapter::id).containsExactly("chapter_1", "chapter_2", "chapter_3");
        assertThat(chapters).extracting(NovelChapter::title)
                .containsExactly("第1章 开端", "第3章 转折", "第4章 结局");
        assertThat(chapters.get(0).content()).doesNotContain("第1章 开端");
    }

    private static Stream<Arguments> novelNoiseSamples() {
        return Stream.of(
                Arguments.of("\uFEFF\u3000第１章　开端\r\n正文\r\n\r\n\r\n", "第1章 开端\n正文"),
                Arguments.of("第1章 开端\nwww.example.com\n正文\n-- 12 --\n请点击下一页继续阅读", "第1章 开端\n正文"),
                Arguments.of("第1章 开端\n第1章 开端\n正文", "第1章 开端\n正文"),
                Arguments.of("第1章 开端\n1\n正文", "第1章 开端\n1\n正文"),
                Arguments.of("第１章 开端\n他停住，回头。", "第1章 开端\n他停住，回头。")
        );
    }
}
