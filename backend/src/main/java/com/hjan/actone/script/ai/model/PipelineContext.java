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
        GlobalStoryState globalStoryState,
        ScenePlan scenePlan,
        CinematicTreatment cinematicTreatment,
        String generatedYaml,
        String repairedYaml,
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
        globalStoryState = globalStoryState == null ? GlobalStoryState.empty() : globalStoryState;
        generatedYaml = ModelValidation.optionalText(generatedYaml);
        repairedYaml = ModelValidation.optionalText(repairedYaml);
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
                GlobalStoryState.empty(),
                null,
                null,
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
                globalStoryState,
                scenePlan,
                cinematicTreatment,
                generatedYaml,
                repairedYaml,
                validationStatus,
                validationError
        );
    }

    public PipelineContext withGlobalStoryState(GlobalStoryState state) {
        return new PipelineContext(
                taskId,
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.GLOBAL_STORY_EXTRACTION,
                chapterSummaries,
                state,
                scenePlan,
                cinematicTreatment,
                generatedYaml,
                repairedYaml,
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
                globalStoryState,
                plan,
                cinematicTreatment,
                generatedYaml,
                repairedYaml,
                validationStatus,
                validationError
        );
    }

    public PipelineContext withCinematicTreatment(CinematicTreatment treatment) {
        return new PipelineContext(
                taskId,
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.PSYCHOLOGICAL_VISUALIZATION,
                chapterSummaries,
                globalStoryState,
                scenePlan,
                treatment,
                generatedYaml,
                repairedYaml,
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
                globalStoryState,
                scenePlan,
                cinematicTreatment,
                yaml,
                repairedYaml,
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
                globalStoryState,
                scenePlan,
                cinematicTreatment,
                generatedYaml,
                repairedYaml,
                status,
                error
        );
    }

    public PipelineContext withRepairedYaml(String yaml) {
        return new PipelineContext(
                taskId,
                projectId,
                projectTitle,
                author,
                logline,
                chapterCount,
                PipelineStage.YAML_REPAIR,
                chapterSummaries,
                globalStoryState,
                scenePlan,
                cinematicTreatment,
                generatedYaml,
                yaml,
                validationStatus,
                validationError
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
                globalStoryState,
                scenePlan,
                cinematicTreatment,
                generatedYaml,
                repairedYaml,
                validationStatus,
                validationError
        );
    }

    private static String newTaskId() {
        return "task_" + UUID.randomUUID().toString().replace("-", "");
    }
}
