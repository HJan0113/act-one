package com.hjan.actone.script.model;

import java.util.List;

public record NovelPreviewResponse(
        boolean valid,
        int chapterCount,
        String message,
        List<NovelChapter> chapters
) {
}

