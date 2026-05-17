package com.github.micycle1.doclet;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A Javadoc doclet that generates concise, LLM-readable Markdown API docs.
 *
 * Invoked via maven-javadoc-plugin's <doclet> configuration.
 * All options are passed as -J flags (see DocletConfig).
 */
public class MarkdownDoclet implements Doclet {

    private static final String MEGAFILE_NAME = "api.md";

    private DocletConfig config;
    private Reporter reporter;

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
        this.config = new DocletConfig();
    }

    @Override
    public String getName() {
        return "MarkdownDoclet";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return config.getSupportedOptions();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }

    @Override
    public boolean run(DocletEnvironment env) {
        // Create output directory
        Path outDir = Path.of(config.getOutputDir());
        try {
            Files.createDirectories(outDir);
            if (config.isCleanOutput()) {
                cleanMarkdownFiles(outDir);
            }
        } catch (IOException e) {
            reporter.print(javax.tools.Diagnostic.Kind.ERROR,
                    "Cannot create output dir: " + e.getMessage());
            return false;
        }

        // One writer per output type. Nested types are inline by default.
        Set<TypeElement> outputTypes = collectOutputTypes(env);
        StringBuilder megafile = shouldWriteMegafile() ? new StringBuilder() : null;
        for (TypeElement classElement : outputTypes) {
            ClassMarkdownWriter writer = new ClassMarkdownWriter(
                    classElement, env.getDocTrees(), config, reporter);
            try {
                String markdown = writer.render();
                if (shouldWriteSplitFiles()) {
                    writeSplitFile(outDir, classElement, markdown);
                }
                if (megafile != null) {
                    appendMegafileSection(megafile, markdown);
                }
            } catch (IOException e) {
                reporter.print(javax.tools.Diagnostic.Kind.ERROR,
                        "Error writing " + classElement.getSimpleName() + ": " + e.getMessage());
            }
        }
        if (megafile != null) {
            try {
                Files.writeString(outDir.resolve(MEGAFILE_NAME), megafile.toString());
            } catch (IOException e) {
                reporter.print(javax.tools.Diagnostic.Kind.ERROR,
                        "Error writing " + MEGAFILE_NAME + ": " + e.getMessage());
            }
        }
        return true;
    }

    private boolean shouldWriteSplitFiles() {
        return config.getOutputMode() == DocletConfig.OutputMode.SPLIT
                || config.getOutputMode() == DocletConfig.OutputMode.BOTH;
    }

    private boolean shouldWriteMegafile() {
        return config.getOutputMode() == DocletConfig.OutputMode.SINGLE
                || config.getOutputMode() == DocletConfig.OutputMode.BOTH;
    }

    private static void writeSplitFile(Path outDir, TypeElement classElement, String markdown) throws IOException {
        Path file = outDir.resolve(classElement.getSimpleName() + ".md");
        Files.writeString(file, markdown);
    }

    private void appendMegafileSection(StringBuilder megafile, String markdown) {
        if (!megafile.isEmpty() && !config.isMinify()) {
            megafile.append('\n');
        }
        megafile.append(markdown);
    }

    private Set<TypeElement> collectOutputTypes(DocletEnvironment env) {
        Set<TypeElement> outputTypes = new LinkedHashSet<>();
        for (TypeElement classElement : ElementFilter.typesIn(env.getIncludedElements())) {
            if (!isIncluded(classElement)) {
                continue;
            }
            if (config.getNestedTypes() == DocletConfig.NestedTypes.SEPARATE) {
                addTypeAndNestedTypes(outputTypes, classElement);
            } else if (isTopLevel(classElement)) {
                outputTypes.add(classElement);
            }
        }
        return outputTypes;
    }

    private void addTypeAndNestedTypes(Set<TypeElement> outputTypes, TypeElement classElement) {
        if (!isIncluded(classElement) || !isVisible(classElement)) {
            return;
        }
        outputTypes.add(classElement);
        for (TypeElement nestedType : ElementFilter.typesIn(classElement.getEnclosedElements())) {
            addTypeAndNestedTypes(outputTypes, nestedType);
        }
    }

    private static void cleanMarkdownFiles(Path outDir) throws IOException {
        try (var paths = Files.list(outDir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".md")).toList()) {
                Files.delete(path);
            }
        }
    }

    private boolean isIncluded(TypeElement classElement) {
        String packageName = envPackageName(classElement);
        if (matchesAnyPackagePrefix(packageName, config.getExcludedPackages())) {
            return false;
        }
        return config.getSubpackages().isEmpty()
                || matchesAnyPackagePrefix(packageName, config.getSubpackages());
    }

    private boolean isVisible(Element e) {
        Set<Modifier> mods = e.getModifiers();
        if (mods.contains(Modifier.PRIVATE)) {
            return config.isIncludePrivate();
        }
        if (!mods.contains(Modifier.PUBLIC) && !mods.contains(Modifier.PROTECTED)) {
            return config.isIncludePackagePrivate();
        }
        return true;
    }

    private static boolean matchesAnyPackagePrefix(String packageName, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String envPackageName(TypeElement classElement) {
        Element element = classElement;
        while (element != null && !(element instanceof PackageElement)) {
            element = element.getEnclosingElement();
        }
        return element == null ? "" : element.toString();
    }

    private static boolean isTopLevel(TypeElement classElement) {
        return classElement.getEnclosingElement() instanceof PackageElement;
    }
}
