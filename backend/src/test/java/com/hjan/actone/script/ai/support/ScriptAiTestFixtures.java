package com.hjan.actone.script.ai.support;

import com.hjan.actone.script.model.ScriptYamlGenerateRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ScriptAiTestFixtures {

    private ScriptAiTestFixtures() {
    }

    public static ScriptYamlGenerateRequest sampleRequest() {
        return new ScriptYamlGenerateRequest(
                "夜雨孤庙",
                "测试作者",
                "杀手林夜在破庙中改变刺杀目标。",
                sampleNovelText()
        );
    }

    public static String sampleNovelText() {
        return resourceText("script-ai/sample-novel.txt");
    }

    public static String validYaml() {
        return resourceText("script-ai/fake-valid-screenplay.yaml");
    }

    public static String invalidYaml() {
        return resourceText("script-ai/fake-invalid-screenplay.yaml");
    }

    private static String resourceText(String path) {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("测试资源不存在：" + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取测试资源失败：" + path, ex);
        }
    }
}
