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
    private static final Path NESTED_OMIT_OUTPUT_DIR = Path.of("target", "markdown-doclet-nested-omit-test");
    private static final Path NESTED_SEPARATE_OUTPUT_DIR = Path.of("target", "markdown-doclet-nested-separate-test");
    private static final Path MINIFY_OUTPUT_DIR = Path.of("target", "markdown-doclet-minify-test");

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
        assertFalse(Files.exists(outputDir.resolve("NestedTypes.md")), "Nested types should be inline by default");

        String docletMarkdown = Files.readString(outputDir.resolve("MarkdownDoclet.md"));
        assertTrue(docletMarkdown.contains("# MarkdownDoclet"));
        assertTrue(docletMarkdown.contains("package: `com.github.micycle1.doclet`"));
        assertTrue(docletMarkdown.contains("`public boolean run(DocletEnvironment env)`"));
        assertFalse(docletMarkdown.contains("`run`\n"));
        assertFalse(docletMarkdown.contains("```"));
        assertFalse(docletMarkdown.contains("---"));
        assertTrue(docletMarkdown.contains("\n\n"));

        String builderMarkdown = Files.readString(outputDir.resolve("MarkdownBuilder.md"));
        assertTrue(builderMarkdown.contains("`public MarkdownBuilder codeBlock(String code, String language)`"));
        assertFalse(builderMarkdown.contains("`codeBlock`\n"));
        assertTrue(builderMarkdown.contains(
                "Thin wrapper around StringBuilder with convenience methods for generating clean Markdown output."));
        assertFalse(builderMarkdown.contains("convenience methods\nfor generating clean Markdown output."));
        assertTrue(builderMarkdown.contains("\n\n"));

        String configMarkdown = Files.readString(outputDir.resolve("DocletConfig.md"));
        assertTrue(configMarkdown.contains("## NestedTypes"));
        assertTrue(configMarkdown.contains("`public static DocletConfig.NestedTypes valueOf(String name)`"));

        String commentUtilsMarkdown = Files.readString(outputDir.resolve("CommentUtils.md"));
        assertTrue(commentUtilsMarkdown.contains(
                "`public static String getTagText(DocTrees docTrees, Element element, DocTree.Kind kind)`\n\n"
                        + "Returns the text for a specific block tag"));
        assertFalse(commentUtilsMarkdown.contains("`getTagText`\n"));
    }

    @Test
    void minifiesBlankLinesWhenConfigured() throws Exception {
        Path outputDir = MINIFY_OUTPUT_DIR.toAbsolutePath();
        deleteDirectory(outputDir);

        runJavadoc(
                outputDir,
                "-minify", "true",
                "com.github.micycle1.doclet");

        String docletMarkdown = Files.readString(outputDir.resolve("MarkdownDoclet.md"));
        assertTrue(docletMarkdown.contains("`public boolean run(DocletEnvironment env)`"));
        assertFalse(docletMarkdown.contains("`run`\n"));
        assertFalse(docletMarkdown.contains("\n\n"));
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

    @Test
    void omitsNestedTypesWhenConfigured() throws Exception {
        Path outputDir = NESTED_OMIT_OUTPUT_DIR.toAbsolutePath();
        deleteDirectory(outputDir);

        runJavadoc(
                outputDir,
                "-nestedTypes", "omit",
                "com.github.micycle1.doclet");

        String configMarkdown = Files.readString(outputDir.resolve("DocletConfig.md"));
        assertFalse(configMarkdown.contains("## NestedTypes"));
        assertFalse(Files.exists(outputDir.resolve("NestedTypes.md")));
    }

    @Test
    void writesNestedTypesAsSeparateFilesWhenConfigured() throws Exception {
        Path outputDir = NESTED_SEPARATE_OUTPUT_DIR.toAbsolutePath();
        deleteDirectory(outputDir);

        runJavadoc(
                outputDir,
                "-nestedTypes", "separate",
                "com.github.micycle1.doclet");

        String configMarkdown = Files.readString(outputDir.resolve("DocletConfig.md"));
        assertFalse(configMarkdown.contains("## NestedTypes"));
        assertTrue(Files.isRegularFile(outputDir.resolve("NestedTypes.md")));

        String nestedMarkdown = Files.readString(outputDir.resolve("NestedTypes.md"));
        assertTrue(nestedMarkdown.contains("# NestedTypes"));
        assertTrue(nestedMarkdown.contains("`public static DocletConfig.NestedTypes valueOf(String name)`"));
        assertFalse(nestedMarkdown.contains("`valueOf`\n"));
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
