package com.hjan.actone.script.ai.model;

public record SourceEvidence(
        String sourceChapterId,
        Integer chapterIndex,
        String excerpt
) {

    public SourceEvidence {
        sourceChapterId = ModelValidation.requireText(sourceChapterId, "sourceChapterId");
        chapterIndex = ModelValidation.requirePositive(chapterIndex, "chapterIndex");
        excerpt = ModelValidation.requireText(excerpt, "excerpt");
    }
}
