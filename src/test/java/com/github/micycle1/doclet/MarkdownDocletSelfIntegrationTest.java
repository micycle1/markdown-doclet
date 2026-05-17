package com.github.micycle1.doclet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DocumentationTool;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;

class MarkdownDocletSelfIntegrationTest {

    private static final Path OUTPUT_DIR = Path.of("target", "markdown-doclet-self-test");
    private static final Path FILTER_OUTPUT_DIR = Path.of("target", "markdown-doclet-filter-test");

    @Test
    void appliesDocletToThisProject() throws Exception {
        Path outputDir = OUTPUT_DIR.toAbsolutePath();
        deleteDirectory(outputDir);

        runJavadoc(outputDir, "com.github.micycle1.doclet");

        List<String> expectedFiles = List.of(
                "ClassMarkdownWriter.md",
                "CommentUtils.md",
                "DocletConfig.md",
                "MarkdownBuilder.md",
                "MarkdownDoclet.md");
        for (String file : expectedFiles) {
            assertTrue(Files.isRegularFile(outputDir.resolve(file)), "Expected " + file + " to be generated");
        }

        String docletMarkdown = Files.readString(outputDir.resolve("MarkdownDoclet.md"));
        assertTrue(docletMarkdown.contains("# MarkdownDoclet"));
        assertTrue(docletMarkdown.contains("**Package:** `com.github.micycle1.doclet`"));
        assertTrue(docletMarkdown.contains("## run"));
        assertTrue(docletMarkdown.contains("```java\npublic boolean run(DocletEnvironment env)\n```"));

        String builderMarkdown = Files.readString(outputDir.resolve("MarkdownBuilder.md"));
        assertTrue(builderMarkdown.contains("## codeBlock"));
        assertTrue(builderMarkdown.contains("```java\npublic MarkdownBuilder codeBlock(String code, String language)\n```"));
        assertTrue(builderMarkdown.contains(
                "Thin wrapper around StringBuilder with convenience methods for generating clean Markdown output."));
        assertFalse(builderMarkdown.contains("convenience methods\nfor generating clean Markdown output."));
        assertTrue(builderMarkdown.contains("**Parameters:** `code`"));
    }

    @Test
    void excludesConfiguredPackages() throws Exception {
        Path outputDir = FILTER_OUTPUT_DIR.toAbsolutePath();
        deleteDirectory(outputDir);
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("StaleExcludedClass.md"), "old output");

        runJavadoc(
                outputDir,
                "-excludePackageNames", "com.github.micycle1.doclet",
                "com.github.micycle1.doclet");

        assertTrue(Files.isDirectory(outputDir));
        try (var paths = Files.list(outputDir)) {
            assertFalse(paths.findAny().isPresent(), "Excluded package should not generate Markdown files");
        }
    }

    private static void runJavadoc(Path outputDir, String... extraArgs) {
        DocumentationTool javadoc = ToolProvider.getSystemDocumentationTool();
        assertNotNull(javadoc, "Javadoc tool is required; run tests on a JDK, not a JRE");

        Path classesDir = Path.of("target", "classes").toAbsolutePath();
        Path sourcesDir = Path.of("src", "main", "java").toAbsolutePath();
        assertTrue(Files.isDirectory(classesDir), "Compiled doclet classes should exist before tests run");

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        List<String> args = new java.util.ArrayList<>(List.of(
                "-quiet",
                "-doclet", MarkdownDoclet.class.getName(),
                "-docletpath", classesDir.toString(),
                "-sourcepath", sourcesDir.toString(),
                "-outputDir", outputDir.toString()));
        args.addAll(List.of(extraArgs));

        int exitCode = javadoc.run(null, stdout, stderr, args.toArray(String[]::new));

        assertEquals(0, exitCode, () -> "stdout:\n" + stdout.toString(StandardCharsets.UTF_8)
                + "\nstderr:\n" + stderr.toString(StandardCharsets.UTF_8));
    }

    private static void deleteDirectory(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.delete(path);
            }
        }
    }
}
