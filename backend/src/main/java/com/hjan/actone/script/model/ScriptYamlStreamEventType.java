package com.hjan.actone.script.model;

public enum ScriptYamlStreamEventType {

    META("meta"),
    PROGRESS("progress"),
    MESSAGE("message"),
    VALIDATION("validation"),
    FINISH("finish"),
    ERROR("error"),
    DONE("done");

    private final String value;

    ScriptYamlStreamEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
