package com.hjan.actone.script.ai.model;

import java.util.List;

final class ModelValidation {

    private ModelValidation() {
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static Integer requirePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " 必须大于 0");
        }
        return value;
    }

    static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " 必须大于 0");
        }
        return value;
    }

    static <T> List<T> copyList(List<T> values, String field) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(field + " 不能包含空元素");
        }
        return List.copyOf(values);
    }

    static <T> List<T> requireNonEmptyList(List<T> values, String field) {
        List<T> copied = copyList(values, field);
        if (copied.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return copied;
    }

    static List<String> copyTextList(List<String> values, String field) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> requireText(value, field + " 元素"))
                .toList();
    }
}
