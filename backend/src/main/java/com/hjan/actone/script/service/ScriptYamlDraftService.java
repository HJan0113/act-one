package com.hjan.actone.script.service;

import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.model.ScriptYamlGenerateResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ScriptYamlDraftService {

    public static final String SCHEMA_VERSION = "1.0";
    private static final int ACTION_MAX_CHARS = 360;
    private static final int EXCERPT_MAX_CHARS = 600;

    private final NovelChapterParser chapterParser;

    public ScriptYamlDraftService(NovelChapterParser chapterParser) {
        this.chapterParser = chapterParser;
    }

    public ScriptYamlGenerateResponse generateFromText(ScriptYamlGenerateRequest request) {
        List<NovelChapter> chapters = chapterParser.parse(request.text());
        if (chapters.size() < NovelChapterParser.MIN_CHAPTER_COUNT) {
            throw new ClientException("至少需要 3 个章节，当前识别到 " + chapters.size() + " 个。");
        }

        String title = resolveTitle(request.title(), chapters);
        String yaml = buildYaml(title, request.author(), request.logline(), chapters);
        return new ScriptYamlGenerateResponse(SCHEMA_VERSION, title, chapters.size(), yaml);
    }

    private String buildYaml(String title, String author, String logline, List<NovelChapter> chapters) {
        StringBuilder yaml = new StringBuilder();
        appendQuoted(yaml, "schema_version", SCHEMA_VERSION, 0);
        appendQuoted(yaml, "kind", "screenplay_draft", 0);
        yaml.append("metadata:\n");
        appendQuoted(yaml, "title", title, 2);
        appendQuoted(yaml, "author", defaultText(author, "未填写"), 2);
        appendQuoted(yaml, "logline", defaultText(logline, "待补充"), 2);
        yaml.append("source:\n");
        appendQuoted(yaml, "type", "novel", 2);
        appendNumber(yaml, "chapter_count", chapters.size(), 2);
        appendQuoted(yaml, "generation_mode", "rule_based_scaffold", 2);
        yaml.append("characters: []\n");
        appendActs(yaml, chapters.size());
        appendScenes(yaml, chapters);
        yaml.append("notes:\n");
        yaml.append("  - \"人物、地点、对白可在后续精修阶段继续补全。\"\n");
        return yaml.toString();
    }

    private void appendActs(StringBuilder yaml, int chapterCount) {
        yaml.append("acts:\n");
        ActRange first = new ActRange("act_1", "第一幕", "建立人物、目标与初始冲突",
                1, Math.max(1, chapterCount / 3));
        ActRange second = new ActRange("act_2", "第二幕", "推进阻碍、选择与关系变化",
                first.end() + 1, Math.max(first.end() + 1, chapterCount * 2 / 3));
        ActRange third = new ActRange("act_3", "第三幕", "完成高潮、抉择与结局",
                second.end() + 1, chapterCount);

        appendAct(yaml, first);
        appendAct(yaml, second);
        appendAct(yaml, third);
    }

    private void appendAct(StringBuilder yaml, ActRange act) {
        yaml.append("  - ");
        appendQuoted(yaml, "id", act.id(), 0);
        appendQuoted(yaml, "title", act.title(), 4);
        yaml.append("    chapter_range:\n");
        appendNumber(yaml, "start", act.start(), 6);
        appendNumber(yaml, "end", act.end(), 6);
        appendQuoted(yaml, "dramatic_function", act.dramaticFunction(), 4);
    }

    private void appendScenes(StringBuilder yaml, List<NovelChapter> chapters) {
        yaml.append("scenes:\n");
        for (NovelChapter chapter : chapters) {
            yaml.append("  - ");
            appendQuoted(yaml, "id", formatSceneId(chapter.index()), 0);
            appendQuoted(yaml, "act_id", resolveActId(chapter.index(), chapters.size()), 4);
            appendNumber(yaml, "chapter_index", chapter.index(), 4);
            appendQuoted(yaml, "source_chapter_id", chapter.id(), 4);
            appendQuoted(yaml, "title", chapter.title(), 4);
            appendQuoted(yaml, "location", "待补充", 4);
            appendQuoted(yaml, "time", "待补充", 4);
            appendQuoted(yaml, "scene_purpose", "承接原章节情节，形成可继续打磨的场景初稿", 4);
            yaml.append("    action: |-\n");
            appendBlock(yaml, summarize(chapter.content(), ACTION_MAX_CHARS), 6);
            yaml.append("    dialogues: []\n");
            yaml.append("    source_excerpt: |-\n");
            appendBlock(yaml, summarize(chapter.content(), EXCERPT_MAX_CHARS), 6);
        }
    }

    private String resolveTitle(String title, List<NovelChapter> chapters) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        if (!chapters.isEmpty() && StringUtils.hasText(chapters.get(0).title())) {
            return chapters.get(0).title();
        }
        return "未命名剧本";
    }

    private String resolveActId(int chapterIndex, int chapterCount) {
        int firstEnd = Math.max(1, chapterCount / 3);
        int secondEnd = Math.max(firstEnd + 1, chapterCount * 2 / 3);
        if (chapterIndex <= firstEnd) {
            return "act_1";
        }
        if (chapterIndex <= secondEnd) {
            return "act_2";
        }
        return "act_3";
    }

    private String formatSceneId(int index) {
        return "scene_%03d".formatted(index);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String summarize(String content, int maxChars) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars).stripTrailing() + "...";
    }

    private void appendQuoted(StringBuilder yaml, String key, String value, int indent) {
        appendIndent(yaml, indent);
        yaml.append(key).append(": \"").append(escapeQuoted(value)).append("\"\n");
    }

    private void appendNumber(StringBuilder yaml, String key, int value, int indent) {
        appendIndent(yaml, indent);
        yaml.append(key).append(": ").append(value).append('\n');
    }

    private void appendBlock(StringBuilder yaml, String value, int indent) {
        String normalized = StringUtils.hasText(value) ? value.replace("\r\n", "\n").replace('\r', '\n').strip() : "待补充";
        String[] lines = normalized.split("\n", -1);
        for (String line : lines) {
            appendIndent(yaml, indent);
            yaml.append(line).append('\n');
        }
    }

    private String escapeQuoted(String value) {
        return defaultText(value, "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void appendIndent(StringBuilder yaml, int indent) {
        yaml.append(" ".repeat(Math.max(0, indent)));
    }

    private record ActRange(String id, String title, String dramaticFunction, int start, int end) {
    }
}
