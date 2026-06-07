package com.hjan.actone.script.service;

import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.model.NovelPreviewResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NovelChapterParser {

    public static final int MIN_CHAPTER_COUNT = 3;
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "(?m)^\\s*(第\\s*[一二三四五六七八九十百千万零〇两0-9]+\\s*[章节回幕卷].*)$"
    );

    public NovelPreviewResponse preview(String rawText) {
        String text = cleanup(rawText);
        if (text.isBlank()) {
            return new NovelPreviewResponse(false, 0, "请先输入小说正文。", List.of());
        }

        List<NovelChapter> chapters = parse(text);
        boolean valid = chapters.size() >= MIN_CHAPTER_COUNT;
        String message = valid
                ? "已识别 " + chapters.size() + " 个章节，可以开始生成剧本 YAML。"
                : "至少需要 3 个章节，当前识别到 " + chapters.size() + " 个。";
        return new NovelPreviewResponse(valid, chapters.size(), message, chapters);
    }

    public List<NovelChapter> parse(String rawText) {
        String text = cleanup(rawText);
        if (text.isBlank()) {
            return List.of();
        }
        Matcher matcher = CHAPTER_HEADING.matcher(text);
        List<Heading> headings = new ArrayList<>();
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            if (looksLikeNarrativeSentence(title)) {
                continue;
            }
            headings.add(new Heading(matcher.start(), matcher.end(), title));
        }
        if (headings.isEmpty()) {
            return List.of(new NovelChapter("chapter_1", 1, "未分章正文", text, text.length()));
        }

        List<NovelChapter> chapters = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            Heading current = headings.get(i);
            int contentStart = current.end();
            int contentEnd = i + 1 < headings.size() ? headings.get(i + 1).start() : text.length();
            String content = text.substring(contentStart, contentEnd).trim();
            chapters.add(new NovelChapter(
                    "chapter_" + (i + 1),
                    i + 1,
                    current.title(),
                    content,
                    content.length()
            ));
        }
        return chapters;
    }

    public String cleanup(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean looksLikeNarrativeSentence(String title) {
        return title.endsWith("。") || title.endsWith("！") || title.endsWith("？")
                || title.endsWith(".") || title.endsWith("!") || title.endsWith("?");
    }

    private record Heading(int start, int end, String title) {
    }
}
