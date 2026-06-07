package com.hjan.actone.infra.ai.support;

import java.util.regex.Pattern;

public class LLMResponseCleaner {

    private static final Pattern LEADING_CODE_FENCE =
            Pattern.compile("^```[\\w.+-]*[\\t ]*\\n?");
    private static final Pattern TRAILING_CODE_FENCE =
            Pattern.compile("\\n?```[\\t ]*$");

    public String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        cleaned = LEADING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        cleaned = TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        return cleaned.strip();
    }
}
