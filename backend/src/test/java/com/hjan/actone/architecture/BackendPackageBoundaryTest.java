package com.hjan.actone.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BackendPackageBoundaryTest {

    private static final Path MAIN_SOURCE = Path.of("src/main/java");

    @Test
    void stableAiBoundaryDoesNotDependOnInfraOrBusinessPackages() throws IOException {
        assertNoForbiddenImports(
                MAIN_SOURCE.resolve("com/hjan/actone/ai"),
                Set.of(
                        "com.hjan.actone.infra.",
                        "com.hjan.actone.script.",
                        "com.hjan.actone.user.",
                        "com.hjan.actone.database.",
                        "com.hjan.actone.config.",
                        "com.baomidou.",
                        "org.springframework."
                )
        );
    }

    @Test
    void infraAiDoesNotDependOnScriptOrUserPackages() throws IOException {
        assertNoForbiddenImports(
                MAIN_SOURCE.resolve("com/hjan/actone/infra/ai"),
                Set.of(
                        "com.hjan.actone.script.",
                        "com.hjan.actone.user.",
                        "com.hjan.actone.database.",
                        "com.baomidou."
                )
        );
    }

    @Test
    void scriptAiBusinessDoesNotDependOnConcreteModelInfrastructure() throws IOException {
        assertNoForbiddenImports(
                MAIN_SOURCE.resolve("com/hjan/actone/script/ai"),
                Set.of(
                        "com.hjan.actone.infra.ai.",
                        "com.hjan.actone.user.",
                        "com.hjan.actone.script.dao.",
                        "com.baomidou.",
                        "org.springframework.ai.",
                        "org.springframework.web.client."
                )
        );
    }

    @Test
    void scriptAiBusinessStaysIndependentFromSpringFramework() throws IOException {
        assertNoForbiddenImports(
                MAIN_SOURCE.resolve("com/hjan/actone/script/ai"),
                Set.of("org.springframework.")
        );
    }

    private void assertNoForbiddenImports(Path packageRoot, Set<String> forbiddenPrefixes) throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(packageRoot)) {
            List<Path> files = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            for (Path file : files) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.startsWith("import ")) {
                        continue;
                    }
                    for (String prefix : forbiddenPrefixes) {
                        if (line.startsWith("import " + prefix)) {
                            violations.add(MAIN_SOURCE.relativize(file) + ":" + (i + 1) + " " + line);
                        }
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }
}
