package com.hjan.actone.script.model;

import java.util.List;

public record ScriptProjectCreateResponse(
        String projectId,
        String title,
        int chapterCount,
        List<NovelChapter> chapters
) {
}

