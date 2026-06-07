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
    public static final int DEFAULT_MAX_CHAPTER_CHARS = 6_000;
    public static final int DEFAULT_TARGET_CHUNK_CHARS = 4_500;
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "(?m)^\\s*(第\\s*[一二三四五六七八九十百千万零〇两0-9]+\\s*[章节回幕卷].*)$"
    );
    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile(
            "^(?:(?:第)?\\d{1,4}页(?:/共?\\d{1,4}页?)?|\\d{1,4}/\\d{1,4}页?)$"
    );
    private static final Pattern DECORATIVE_PAGE_NUMBER = Pattern.compile("^[-_=—]{1,}\\s*\\d{1,4}\\s*[-_=—]{1,}$");
    private static final Pattern DECORATIVE_SEPARATOR = Pattern.compile("^[-_=—]{3,}$");
    private static final Pattern WEB_SOURCE_LINE = Pattern.compile("(?i).*(https?://|www\\.|\\.com|\\.net|\\.org).*");
    private static final Pattern SENTENCE = Pattern.compile("[^。！？!?；;]+[。！？!?；;]?");
    private static final Pattern PREFERRED_BOUNDARY = Pattern.compile(
            "^(翌日|次日|第二天|当夜|夜里|清晨|黎明|黄昏|傍晚|深夜|子时|与此同时|此时|这时|随后|片刻后|不久后|来到|回到|走进|门外|屋内|院中|街上|山下|林中|雨夜).*"
    );

    private final int maxChapterChars;
    private final int targetChunkChars;

    public NovelChapterParser() {
        this(DEFAULT_MAX_CHAPTER_CHARS, DEFAULT_TARGET_CHUNK_CHARS);
    }

    NovelChapterParser(int maxChapterChars, int targetChunkChars) {
        this.maxChapterChars = Math.max(1, maxChapterChars);
        this.targetChunkChars = Math.max(1, targetChunkChars);
    }

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
            return splitLongChapters(List.of(new NovelChapter("chapter_1", 1, "未分章正文", text, text.length())));
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
        return splitLongChapters(chapters);
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

    private List<NovelChapter> splitLongChapters(List<NovelChapter> chapters) {
        List<NovelChapter> result = new ArrayList<>();
        for (NovelChapter chapter : chapters) {
            List<String> chunks = splitContent(chapter.content());
            for (int i = 0; i < chunks.size(); i++) {
                int chapterIndex = result.size() + 1;
                boolean chunked = chunks.size() > 1;
                String id = chunked
                        ? "chapter_%d_chunk_%d".formatted(chapter.index(), i + 1)
                        : "chapter_" + chapterIndex;
                String title = chunked
                        ? chapter.title() + "（片段 " + (i + 1) + "）"
                        : chapter.title();
                String content = chunks.get(i).trim();
                if (!content.isBlank()) {
                    result.add(new NovelChapter(id, chapterIndex, title, content, content.length()));
                }
            }
        }
        return result;
    }

    private List<String> splitContent(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalized = content.trim();
        if (normalized.length() <= maxChapterChars) {
            return List.of(normalized);
        }

        List<TextSegment> segments = segments(normalized);
        List<String> chunks = new ArrayList<>();
        List<String> currentSegments = new ArrayList<>();
        int currentLength = 0;
        int preferredMinimum = Math.max(1, targetChunkChars * 2 / 3);
        for (TextSegment segment : segments) {
            if (segment.text().isBlank()) {
                continue;
            }
            int separatorLength = currentSegments.isEmpty() ? 0 : 2;
            boolean exceedsBudget = !currentSegments.isEmpty()
                    && currentLength + separatorLength + segment.text().length() > targetChunkChars;
            boolean naturalBoundary = !currentSegments.isEmpty()
                    && currentLength >= preferredMinimum
                    && segment.preferredBreakBefore();
            if (exceedsBudget || naturalBoundary) {
                chunks.add(String.join("\n\n", currentSegments).trim());
                currentSegments.clear();
                currentLength = 0;
                separatorLength = 0;
            }
            currentSegments.add(segment.text());
            currentLength += separatorLength + segment.text().length();
        }
        if (!currentSegments.isEmpty()) {
            chunks.add(String.join("\n\n", currentSegments).trim());
        }
        return chunks.isEmpty() ? List.of(normalized) : chunks;
    }

    private List<TextSegment> segments(String content) {
        String[] paragraphs = content.split("\\n\\s*\\n");
        List<TextSegment> result = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String text = paragraph.trim();
            if (text.isBlank()) {
                continue;
            }
            if (text.length() <= targetChunkChars) {
                result.add(new TextSegment(text, isPreferredBoundary(text)));
                continue;
            }
            Matcher matcher = SENTENCE.matcher(text);
            boolean matched = false;
            while (matcher.find()) {
                String sentence = matcher.group().trim();
                if (!sentence.isBlank()) {
                    result.add(new TextSegment(sentence, isPreferredBoundary(sentence)));
                    matched = true;
                }
            }
            if (!matched) {
                result.add(new TextSegment(text, isPreferredBoundary(text)));
            }
        }
        return result;
    }

    private boolean isPreferredBoundary(String text) {
        return text != null && PREFERRED_BOUNDARY.matcher(text.strip()).matches();
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

    private record TextSegment(String text, boolean preferredBreakBefore) {
    }
}
