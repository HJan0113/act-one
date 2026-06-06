package com.hjan.actone.script.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScriptDialoguePatch(
        String speaker,
        String line,
        String emotion
) {
}
