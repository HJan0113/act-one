package com.hjan.actone.script.ai.model;

import java.util.List;
import java.util.stream.Collectors;

public record PipelineConsistencyReport(
        List<Issue> errors,
        List<Issue> warnings
) {

    public PipelineConsistencyReport {
        errors = ModelValidation.copyList(errors, "errors");
        warnings = ModelValidation.copyList(warnings, "warnings");
    }

    public static PipelineConsistencyReport empty() {
        return new PipelineConsistencyReport(List.of(), List.of());
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public String summary() {
        if (errors.isEmpty()) {
            return "一致性校验通过";
        }
        return errors.stream()
                .map(Issue::message)
                .limit(5)
                .collect(Collectors.joining("；"));
    }

    public record Issue(
            String code,
            String message,
            List<String> refs
    ) {

        public Issue {
            code = ModelValidation.requireText(code, "code");
            message = ModelValidation.requireText(message, "message");
            refs = ModelValidation.copyTextList(refs, "refs");
        }
    }
}
