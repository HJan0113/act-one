package com.hjan.actone.script.controller;

import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.web.Results;
import com.hjan.actone.script.model.NovelPreviewRequest;
import com.hjan.actone.script.model.NovelPreviewResponse;
import com.hjan.actone.script.service.NovelChapterParser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/novels")
public class NovelPreviewController {

    private final NovelChapterParser chapterParser;

    public NovelPreviewController(NovelChapterParser chapterParser) {
        this.chapterParser = chapterParser;
    }

    @PostMapping("/chapters/preview")
    public Result<NovelPreviewResponse> preview(@RequestBody NovelPreviewRequest request) {
        return Results.success(chapterParser.preview(request == null ? null : request.text()));
    }
}
