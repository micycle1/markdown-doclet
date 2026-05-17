package com.github.micycle1.doclet;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Generates one Markdown file for a given TypeElement (class/interface/enum).
 *
 * Output path: {outputDir}/{SimpleName}.md
 *
 * Structure:
 *   # ClassName
 *   package + class-level javadoc
 *   ---
 *   ## Fields (if enabled)
 *   ## Constructors (if enabled)
 *   ## methodName   (one section per method)
 *   ```java signature```
 *   description
 *   **Parameters:** ...
 *   **Returns:** ...
 */
public class ClassMarkdownWriter {

    private final TypeElement       classElement;
    private final com.sun.source.util.DocTrees docTrees;
    private final DocletConfig      config;
    private final Reporter          reporter;

    public ClassMarkdownWriter(TypeElement classElement,
                               com.sun.source.util.DocTrees docTrees,
                               DocletConfig config,
                               Reporter reporter) {
        this.classElement = classElement;
        this.docTrees     = docTrees;
        this.config       = config;
        this.reporter     = reporter;
    }

    public void write(Path outputDir) throws IOException {
        MarkdownBuilder md = new MarkdownBuilder();

        // ── Class header ──────────────────────────────────────────────────────
        String simpleName = classElement.getSimpleName().toString();
        md.h1(simpleName);
        md.line();
        md.line("**Package:** `" + classElement.getEnclosingElement().toString() + "`");
        md.line();

        // Class-level doc comment
        String classDoc = CommentUtils.getMainDescription(docTrees, classElement);
        if (!classDoc.isBlank()) {
            md.line(classDoc);
            md.line();
        }

        md.rule();

        // ── Fields ────────────────────────────────────────────────────────────
        if (config.isIncludeFields()) {
            List<VariableElement> fields = ElementFilter.fieldsIn(classElement.getEnclosedElements());
            List<VariableElement> visible = fields.stream()
                    .filter(f -> isVisible(f))
                    .toList();
            if (!visible.isEmpty()) {
                md.h2("Fields");
                for (VariableElement field : visible) {
                    writeField(md, field);
                }
                md.rule();
            }
        }

        // ── Constructors ──────────────────────────────────────────────────────
        if (config.isIncludeConstructors()) {
            List<ExecutableElement> ctors = ElementFilter.constructorsIn(classElement.getEnclosedElements());
            List<ExecutableElement> visible = ctors.stream()
                    .filter(c -> isVisible(c))
                    .toList();
            if (!visible.isEmpty()) {
                md.h2("Constructors");
                for (ExecutableElement ctor : visible) {
                    writeExecutable(md, ctor, true);
                }
                md.rule();
            }
        }

        // ── Methods ───────────────────────────────────────────────────────────
        List<ExecutableElement> methods = ElementFilter.methodsIn(classElement.getEnclosedElements());
        for (ExecutableElement method : methods) {
            if (!isVisible(method)) continue;
            writeExecutable(md, method, false);
            md.rule();
        }

        // ── Write file ────────────────────────────────────────────────────────
        Path file = outputDir.resolve(simpleName + ".md");
        Files.writeString(file, md.build());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void writeField(MarkdownBuilder md, VariableElement field) {
        String sig = buildFieldSignature(field);
        md.codeBlock(sig, "java");

        String doc = CommentUtils.getMainDescription(docTrees, field);
        if (!doc.isBlank()) {
            md.line(doc);
        }
        md.line();
    }

    private void writeExecutable(MarkdownBuilder md, ExecutableElement exec, boolean isCtor) {
        String name = isCtor
                ? classElement.getSimpleName().toString()
                : exec.getSimpleName().toString();

        md.h2(name);

        // Signature
        String sig = buildSignature(exec, isCtor);
        md.codeBlock(sig, "java");

        // Deprecated notice
        if (config.isIncludeDeprecated()) {
            String dep = CommentUtils.getTagText(docTrees, exec, DocTree.Kind.DEPRECATED);
            if (!dep.isBlank()) {
                md.line("> ⚠️ **Deprecated:** " + dep);
                md.line();
            }
        }

        // Main description
        String desc = CommentUtils.getMainDescription(docTrees, exec);
        if (!desc.isBlank()) {
            md.line(desc);
            md.line();
        }

        // Params
        if (config.isIncludeParams() && !exec.getParameters().isEmpty()) {
            List<String> paramDocs = CommentUtils.getParamDocs(docTrees, exec);
            if (!paramDocs.isEmpty()) {
                if (config.isCompactParams()) {
                    // Inline: **Parameters:** `a` — desc  `b` — desc
                    md.append("**Parameters:** ");
                    md.append(String.join("  ", paramDocs));
                    md.line();
                } else {
                    // One bullet per param
                    md.line("**Parameters:**");
                    paramDocs.forEach(p -> md.line("- " + p));
                }
                md.line();
            }
        }

        // Return
        if (config.isIncludeReturn() && !isCtor) {
            String ret = CommentUtils.getTagText(docTrees, exec, DocTree.Kind.RETURN);
            if (!ret.isBlank()) {
                md.line("**Returns:** " + ret);
                md.line();
            }
        }

        // Throws
        if (config.isIncludeThrows()) {
            List<String> throwsDocs = CommentUtils.getThrowsDocs(docTrees, exec);
            if (!throwsDocs.isEmpty()) {
                md.line("**Throws:**");
                throwsDocs.forEach(t -> md.line("- " + t));
                md.line();
            }
        }
    }

    private boolean isVisible(Element e) {
        Set<Modifier> mods = e.getModifiers();
        if (mods.contains(Modifier.PRIVATE))   return config.isIncludePrivate();
        if (!mods.contains(Modifier.PUBLIC) &&
            !mods.contains(Modifier.PROTECTED)) return config.isIncludePackagePrivate();
        return true;
    }

    private String buildSignature(ExecutableElement exec, boolean isCtor) {
        StringBuilder sb = new StringBuilder();

        // Modifiers (skip abstract/default noise for LLM purposes)
        Set<Modifier> mods = exec.getModifiers();
        if (mods.contains(Modifier.PUBLIC))    sb.append("public ");
        if (mods.contains(Modifier.PROTECTED)) sb.append("protected ");
        if (mods.contains(Modifier.STATIC))    sb.append("static ");

        // Return type (methods only)
        if (!isCtor) {
            sb.append(simplify(exec.getReturnType().toString())).append(" ");
        }

        sb.append(isCtor ? classElement.getSimpleName() : exec.getSimpleName());
        sb.append("(");

        // Parameters
        List<? extends VariableElement> params = exec.getParameters();
        for (int i = 0; i < params.size(); i++) {
            VariableElement p = params.get(i);
            sb.append(simplify(p.asType().toString()))
              .append(" ")
              .append(p.getSimpleName());
            if (i < params.size() - 1) sb.append(", ");
        }
        sb.append(")");

        // Throws (in signature, brief)
        List<? extends TypeMirror> thrown = exec.getThrownTypes();
        if (!thrown.isEmpty()) {
            sb.append(" throws ");
            for (int i = 0; i < thrown.size(); i++) {
                sb.append(simplify(thrown.get(i).toString()));
                if (i < thrown.size() - 1) sb.append(", ");
            }
        }
        return sb.toString();
    }

    private String buildFieldSignature(VariableElement field) {
        StringBuilder sb = new StringBuilder();
        Set<Modifier> mods = field.getModifiers();
        if (mods.contains(Modifier.PUBLIC))    sb.append("public ");
        if (mods.contains(Modifier.STATIC))    sb.append("static ");
        if (mods.contains(Modifier.FINAL))     sb.append("final ");
        sb.append(simplify(field.asType().toString()))
          .append(" ")
          .append(field.getSimpleName());
        return sb.toString();
    }

    /** Strip package prefixes if configured. e.g. processing.core.PShape → PShape */
    private String simplify(String typeName) {
        if (!config.isStripPackageNames()) return typeName;
        // Handle generics like java.util.List<processing.core.PShape>
        return typeName.replaceAll("\\b[a-z][a-z0-9_.]*\\.([A-Z])", "$1");
    }
}
