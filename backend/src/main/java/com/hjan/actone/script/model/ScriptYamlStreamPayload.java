package com.hjan.actone.script.model;

public record ScriptYamlStreamPayload(
        String taskId,
        String stage,
        Integer progress,
        String message,
        String validationStatus,
        String validationError,
        Boolean valid,
        ScriptYamlGenerateResponse result
) {

    public static ScriptYamlStreamPayload of(String taskId, String stage, int progress, String message) {
        return new ScriptYamlStreamPayload(taskId, stage, progress, message, null, null, null, null);
    }

    public static ScriptYamlStreamPayload validation(String taskId,
                                                     String stage,
                                                     int progress,
                                                     String validationStatus,
                                                     String validationError,
                                                     boolean valid) {
        return new ScriptYamlStreamPayload(
                taskId,
                stage,
                progress,
                null,
                validationStatus,
                validationError,
                valid,
                null
        );
    }

    public static ScriptYamlStreamPayload finish(String taskId, ScriptYamlGenerateResponse result) {
        return new ScriptYamlStreamPayload(
                taskId,
                "COMPLETED",
                100,
                "转换完成",
                result.validationStatus(),
                result.validationError(),
                "valid".equalsIgnoreCase(result.validationStatus()),
                result
        );
    }
}
