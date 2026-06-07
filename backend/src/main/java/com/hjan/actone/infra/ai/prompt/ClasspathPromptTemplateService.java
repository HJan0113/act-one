package com.hjan.actone.infra.ai.prompt;

import com.hjan.actone.ai.prompt.PromptTemplateId;
import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.ai.prompt.RenderedPrompt;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClasspathPromptTemplateService implements PromptTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z][a-z0-9_]*)}}");
    private static final Pattern MULTIPLE_BLANK_LINES = Pattern.compile("\\n{3,}");

    private final ResourceLoader resourceLoader;
    private final Map<PromptTemplateId, String> cache = new ConcurrentHashMap<>();

    public ClasspathPromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "ResourceLoader 不能为空");
    }

    @Override
    public String load(PromptTemplateId templateId) {
        Objects.requireNonNull(templateId, "PromptTemplateId 不能为空");
        return cache.computeIfAbsent(templateId, this::readTemplate);
    }

    @Override
    public RenderedPrompt render(PromptTemplateId templateId, Map<String, ?> variables) {
        Objects.requireNonNull(templateId, "PromptTemplateId 不能为空");
        Objects.requireNonNull(variables, "Prompt 变量不能为空");
        validateRequiredVariables(templateId, variables);

        String rendered = replaceVariables(load(templateId), variables);
        Matcher unresolved = PLACEHOLDER.matcher(rendered);
        if (unresolved.find()) {
            throw new IllegalStateException(
                    "Prompt 模板存在未解析变量：" + templateId.id() + " -> " + unresolved.group(1)
            );
        }
        return new RenderedPrompt(
                templateId.id(),
                templateId.version(),
                cleanup(rendered)
        );
    }

    private String readTemplate(PromptTemplateId templateId) {
        String location = "classpath:" + templateId.resourcePath();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt 模板不存在：" + templateId.resourcePath());
        }
        try (InputStream input = resource.getInputStream()) {
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "")
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .strip();
            validateTemplateDefinition(templateId, template);
            return template;
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Prompt 模板失败：" + templateId.resourcePath(), ex);
        }
    }

    private void validateTemplateDefinition(PromptTemplateId templateId, String template) {
        if (template.isBlank()) {
            throw new IllegalStateException("Prompt 模板内容为空：" + templateId.resourcePath());
        }
        Set<String> declaredVariables = extractVariables(template);
        Set<String> expectedVariables = new LinkedHashSet<>(templateId.variables());
        if (!declaredVariables.equals(expectedVariables)) {
            throw new IllegalStateException(
                    "Prompt 模板变量声明不一致：" + templateId.id()
                            + "，期望=" + expectedVariables
                            + "，实际=" + declaredVariables
            );
        }
    }

    private void validateRequiredVariables(PromptTemplateId templateId, Map<String, ?> variables) {
        for (String variable : templateId.variables()) {
            if (!variables.containsKey(variable) || variables.get(variable) == null) {
                throw new IllegalArgumentException(
                        "Prompt 缺少必填变量：" + templateId.id() + " -> " + variable
                );
            }
        }
    }

    private String replaceVariables(String template, Map<String, ?> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder(template.length());
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Set<String> extractVariables(String template) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private String cleanup(String prompt) {
        return MULTIPLE_BLANK_LINES.matcher(prompt).replaceAll("\n\n").strip();
    }
}
