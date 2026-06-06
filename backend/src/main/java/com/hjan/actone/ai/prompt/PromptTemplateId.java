package com.hjan.actone.ai.prompt;

import java.util.List;

public enum PromptTemplateId {

    CHAPTER_SUMMARY(
            "chapter-summary",
            "1.0.0",
            "prompt/v1/chapter-summary.st",
            List.of("chapter_id", "chapter_index", "chapter_title", "chapter_content")
    ),
    GLOBAL_STORY_EXTRACTION(
            "global-story-extraction",
            "1.0.0",
            "prompt/v1/global-story-extraction.st",
            List.of("chapter_summaries", "existing_global_story_state")
    ),
    SCENE_PLANNING(
            "scene-planning",
            "1.0.0",
            "prompt/v1/scene-planning.st",
            List.of("chapter_summaries", "global_story_state")
    ),
    CINEMATIC_TREATMENT(
            "cinematic-treatment",
            "1.0.0",
            "prompt/v1/cinematic-treatment.st",
            List.of("chapter_summaries", "global_story_state", "scene_plan")
    ),
    DIALOGUE_GENERATION(
            "dialogue-generation",
            "1.0.0",
            "prompt/v1/dialogue-generation.st",
            List.of("global_story_state", "scene_plan", "cinematic_treatment")
    ),
    SCRIPT_YAML_GENERATION(
            "script-yaml-generation",
            "1.0.0",
            "prompt/v1/script-yaml-generation.st",
            List.of(
                    "project_title",
                    "author",
                    "logline",
                    "chapter_count",
                    "global_story_state",
                    "scene_plans",
                    "cinematic_treatment",
                    "dialogue_draft",
                    "source_trace",
                    "schema_summary"
            )
    ),
    SCENE_REFINEMENT(
            "scene-refinement",
            "1.0.0",
            "prompt/v1/scene-refinement.st",
            List.of("scene_id", "instruction", "scene_yaml", "characters_yaml", "schema_summary")
    ),
    DIALOGUE_VOICE_REWRITE(
            "dialogue-voice-rewrite",
            "1.0.0",
            "prompt/v1/dialogue-voice-rewrite.st",
            List.of("speaker", "instruction", "scenes_yaml", "characters_yaml", "relationships_yaml", "schema_summary")
    ),
    RHYTHM_ANALYSIS(
            "rhythm-analysis",
            "1.0.0",
            "prompt/v1/rhythm-analysis.st",
            List.of("scenes_metrics_json", "schema_summary")
    ),
    YAML_REPAIR(
            "yaml-repair",
            "1.0.0",
            "prompt/v1/yaml-repair.st",
            List.of("invalid_yaml", "validation_errors", "schema_summary", "chapter_count")
    );

    private final String id;
    private final String version;
    private final String resourcePath;
    private final List<String> variables;

    PromptTemplateId(String id,
                     String version,
                     String resourcePath,
                     List<String> variables) {
        this.id = id;
        this.version = version;
        this.resourcePath = resourcePath;
        this.variables = List.copyOf(variables);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public String resourcePath() {
        return resourcePath;
    }

    public List<String> variables() {
        return variables;
    }
}
