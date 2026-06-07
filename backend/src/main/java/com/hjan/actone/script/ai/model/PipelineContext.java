package com.hjan.actone.script.ai.model;

import java.util.List;
import java.util.UUID;

public record PipelineContext(
        String taskId,
        String projectId,
        String projectTitle,
        String author,
        String logline,
        Integer chapterCount,
        PipelineStage currentStage,
        List<ChapterSummary> chapterSummaries,
        ScenePlan scenePlan,
        String generatedYaml,
        String validationStatus,
        String validationError
) {

    public PipelineContext {
        taskId = ModelValidation.optionalText(taskId);
        taskId = taskId == null ? newTaskId() : taskId;
        projectId = ModelValidation.optionalText(projectId);
        projectTitle = ModelValidation.requireText(projectTitle, "projectTitle");
        author = ModelValidation.optionalText(author);
        logline = ModelValidation.optionalText(logline);
        chapterCount = ModelValidation.requirePositive(chapterCount, "chapterCount");
        currentStage = currentStage == null ? PipelineStage.INIT : currentStage;
        chapterSummaries = ModelValidation.copyList(chapterSummaries, "chapterSummaries");
        generatedYaml = ModelValidation.optionalText(generatedYaml);
        validationStatus = ModelValidation.optionalText(validationStatus);
        validationError = ModelValidation.optionalText(validationError);
    }

    public static PipelineContext create(String projectId,
                                         String projectTitle,
                                         String author,
                                         String logline,
                                         Integer chapterCount) {
        return new PipelineContext(
                newTaskId(),
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.INIT,
                List.of(),
                null,
                null,
                null,
                null
        );
    }

    public PipelineContext withChapterSummaries(List<ChapterSummary> summaries) {
        return new PipelineContext(
                taskId,
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.CHAPTER_SUMMARY,
                summaries,
                scenePlan,
                generatedYaml,
                validationStatus,
                validationError
        );
    }

    public PipelineContext withScenePlan(ScenePlan plan) {
        return new PipelineContext(
                taskId,
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.SCENE_PLANNING,
                chapterSummaries,
                plan,
                generatedYaml,
                validationStatus,
                validationError
        );
    }

    public PipelineContext withGeneratedYaml(String yaml) {
        return new PipelineContext(
                taskId,
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.SCRIPT_YAML_GENERATION,
                chapterSummaries,
                scenePlan,
                yaml,
                validationStatus,
                validationError
        );
    }

    public PipelineContext withValidation(String status, String error) {
        return new PipelineContext(
                taskId,
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.YAML_VALIDATION,
                chapterSummaries,
                scenePlan,
                generatedYaml,
                status,
                error
        );
    }

    public PipelineContext completed() {
        return new PipelineContext(
                taskId,
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.COMPLETED,
                chapterSummaries,
                scenePlan,
                generatedYaml,
                validationStatus,
                validationError
        );
    }

    private static String newTaskId() {
        return "task_" + UUID.randomUUID().toString().replace("-", "");
    }
}
