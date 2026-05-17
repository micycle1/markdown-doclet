package com.github.micycle1.doclet;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A Javadoc doclet that generates one Markdown file per class,
 * designed to be concise and LLM-readable.
 *
 * Invoked via maven-javadoc-plugin's <doclet> configuration.
 * All options are passed as -J flags (see DocletConfig).
 */
public class MarkdownDoclet implements Doclet {

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
        } catch (IOException e) {
            reporter.print(javax.tools.Diagnostic.Kind.ERROR,
                    "Cannot create output dir: " + e.getMessage());
            return false;
        }

        // One writer per class
        for (TypeElement classElement : ElementFilter.typesIn(env.getIncludedElements())) {
            if (!isIncluded(classElement)) {
                continue;
            }
            ClassMarkdownWriter writer = new ClassMarkdownWriter(
                    classElement, env.getDocTrees(), config, reporter);
            try {
                writer.write(outDir);
            } catch (IOException e) {
                reporter.print(javax.tools.Diagnostic.Kind.ERROR,
                        "Error writing " + classElement.getSimpleName() + ": " + e.getMessage());
            }
        }
        return true;
    }

    private boolean isIncluded(TypeElement classElement) {
        String packageName = envPackageName(classElement);
        if (matchesAnyPackagePrefix(packageName, config.getExcludedPackages())) {
            return false;
        }
        return config.getSubpackages().isEmpty()
                || matchesAnyPackagePrefix(packageName, config.getSubpackages());
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
}
