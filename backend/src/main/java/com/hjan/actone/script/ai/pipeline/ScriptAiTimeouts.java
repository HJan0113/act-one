package com.hjan.actone.script.ai.pipeline;

import java.time.Duration;

final class ScriptAiTimeouts {

    private ScriptAiTimeouts() {
    }

    static Duration requirePositive(Duration timeout, Duration fallback, String field) {
        Duration resolved = timeout == null ? fallback : timeout;
        if (resolved == null || resolved.isZero() || resolved.isNegative()) {
            throw new IllegalArgumentException(field + " 必须大于 0");
        }
        return resolved;
    }
}
