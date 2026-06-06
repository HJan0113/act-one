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
    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile(
            "^(?:(?:第)?\\d{1,4}页(?:/共?\\d{1,4}页?)?|\\d{1,4}/\\d{1,4}页?)$"
    );
    private static final Pattern DECORATIVE_PAGE_NUMBER = Pattern.compile("^[-_=—]{1,}\\s*\\d{1,4}\\s*[-_=—]{1,}$");
    private static final Pattern DECORATIVE_SEPARATOR = Pattern.compile("^[-_=—]{3,}$");
    private static final Pattern WEB_SOURCE_LINE = Pattern.compile("(?i).*(https?://|www\\.|\\.com|\\.net|\\.org).*");

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
            if (!headings.isEmpty()) {
                Heading previous = headings.get(headings.size() - 1);
                String between = text.substring(previous.end(), matcher.start()).trim();
                if (sameHeading(previous.title(), title) && between.isBlank()) {
                    headings.set(headings.size() - 1, new Heading(matcher.start(), matcher.end(), title));
                    continue;
                }
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
            if (content.isBlank()) {
                continue;
            }
            int chapterIndex = chapters.size() + 1;
            chapters.add(new NovelChapter(
                    "chapter_" + chapterIndex,
                    chapterIndex,
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
        String normalized = normalizeFullWidthAscii(text)
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace('\u2028', '\n')
                .replace('\u2029', '\n')
                .replace('\u3000', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ');
        String[] lines = normalized.split("\\n", -1);
        List<String> cleaned = new ArrayList<>(lines.length);
        String previousKept = null;
        for (String rawLine : lines) {
            String line = rawLine.strip().replaceAll(" {2,}", " ");
            if (isBoilerplateLine(line)) {
                continue;
            }
            if (previousKept != null && sameHeading(previousKept, line)) {
                continue;
            }
            cleaned.add(line);
            if (!line.isBlank()) {
                previousKept = line;
            }
        }
        return collapseBlankLines(cleaned);
    }

    private boolean looksLikeNarrativeSentence(String title) {
        return title.endsWith("。") || title.endsWith("！") || title.endsWith("？")
                || title.endsWith(".") || title.endsWith("!") || title.endsWith("?");
    }

    private boolean isBoilerplateLine(String line) {
        if (line.isBlank()) {
            return false;
        }
        String compact = line.replace(" ", "");
        return PAGE_NUMBER_LINE.matcher(compact).matches()
                || DECORATIVE_PAGE_NUMBER.matcher(compact).matches()
                || DECORATIVE_SEPARATOR.matcher(compact).matches()
                || WEB_SOURCE_LINE.matcher(compact).matches()
                || compact.contains("本章未完")
                || compact.contains("请点击下一页")
                || compact.contains("返回目录")
                || compact.contains("加入书签")
                || compact.contains("手机用户请浏览")
                || compact.contains("最新网址");
    }

    private boolean sameHeading(String first, String second) {
        return isChapterHeading(first)
                && isChapterHeading(second)
                && normalizeHeading(first).equals(normalizeHeading(second));
    }

    private boolean isChapterHeading(String value) {
        return value != null && CHAPTER_HEADING.matcher(value).matches() && !looksLikeNarrativeSentence(value.trim());
    }

    private String normalizeHeading(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private String normalizeFullWidthAscii(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\uFF10' && c <= '\uFF19') {
                builder.append((char) ('0' + c - '\uFF10'));
            } else if (c >= '\uFF21' && c <= '\uFF3A') {
                builder.append((char) ('A' + c - '\uFF21'));
            } else if (c >= '\uFF41' && c <= '\uFF5A') {
                builder.append((char) ('a' + c - '\uFF41'));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private String collapseBlankLines(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        boolean previousBlank = true;
        for (String line : lines) {
            if (line.isBlank()) {
                if (!previousBlank) {
                    result.add("");
                }
                previousBlank = true;
                continue;
            }
            result.add(line);
            previousBlank = false;
        }
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        return String.join("\n", result).trim();
    }

    private record Heading(int start, int end, String title) {
    }
}
