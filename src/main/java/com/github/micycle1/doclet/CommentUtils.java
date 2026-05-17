package com.github.micycle1.doclet;

import com.sun.source.doctree.*;
import com.sun.source.util.DocTrees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for extracting text from Javadoc comment trees.
 *
 * The DocTree API gives us structured access to @param, @return, etc.
 * This class handles the messy parts:
 *   - stripping residual HTML tags from old-style Javadoc
 *   - resolving {@code ...} and {@link ...} inline tags
 *   - returning empty strings (never null) for missing tags
 */
public final class CommentUtils {

    private CommentUtils() {}

    /**
     * Returns the main description of an element (everything before the first block tag).
     * HTML is stripped. {@code x} becomes `x`. {@link X} becomes X.
     */
    public static String getMainDescription(DocTrees docTrees, Element element) {
        DocCommentTree tree = docTrees.getDocCommentTree(element);
        if (tree == null) return "";

        StringBuilder sb = new StringBuilder();
        for (DocTree node : tree.getFullBody()) {
            sb.append(renderInline(node));
        }
        return stripHtml(sb.toString()).trim();
    }

    /**
     * Returns the text for a specific block tag (e.g. @return, @deprecated).
     * Returns "" if the tag is absent.
     */
    public static String getTagText(DocTrees docTrees, Element element, DocTree.Kind kind) {
        DocCommentTree tree = docTrees.getDocCommentTree(element);
        if (tree == null) return "";

        for (DocTree tag : tree.getBlockTags()) {
            if (tag.getKind() != kind) continue;
            if (tag instanceof ReturnTree) {
                return renderBody(((ReturnTree) tag).getDescription());
            }
            if (tag instanceof DeprecatedTree) {
                return renderBody(((DeprecatedTree) tag).getBody());
            }
            return tag.toString();
        }
        return "";
    }

    /**
     * Returns a list of formatted param descriptions.
     * Each entry is: "`paramName` — description"
     */
    public static List<String> getParamDocs(DocTrees docTrees, ExecutableElement method) {
        DocCommentTree tree = docTrees.getDocCommentTree(method);
        List<String> result = new ArrayList<>();
        if (tree == null) return result;

        for (DocTree tag : tree.getBlockTags()) {
            if (tag.getKind() != DocTree.Kind.PARAM) continue;
            ParamTree param = (ParamTree) tag;
            String name = param.getName().toString();
            String desc = renderBody(param.getDescription()).trim();
            result.add("`" + name + "` — " + desc);
        }
        return result;
    }

    /**
     * Returns a list of formatted throws descriptions.
     * Each entry is: "`ExceptionType` — description"
     */
    public static List<String> getThrowsDocs(DocTrees docTrees, ExecutableElement method) {
        DocCommentTree tree = docTrees.getDocCommentTree(method);
        List<String> result = new ArrayList<>();
        if (tree == null) return result;

        for (DocTree tag : tree.getBlockTags()) {
            if (tag.getKind() != DocTree.Kind.THROWS &&
                tag.getKind() != DocTree.Kind.EXCEPTION) continue;
            ThrowsTree t = (ThrowsTree) tag;
            String type = t.getExceptionName().toString();
            String desc = renderBody(t.getDescription()).trim();
            result.add("`" + type + "` — " + desc);
        }
        return result;
    }

    // ── Rendering helpers ─────────────────────────────────────────────────────

    private static String renderBody(List<? extends DocTree> nodes) {
        StringBuilder sb = new StringBuilder();
        for (DocTree node : nodes) sb.append(renderInline(node));
        return stripHtml(sb.toString()).trim();
    }

    private static String renderInline(DocTree node) {
        return switch (node.getKind()) {
            case TEXT             -> ((TextTree) node).getBody();
            case CODE, LITERAL    -> "`" + ((LiteralTree) node).getBody().toString() + "`";
            case LINK, LINK_PLAIN -> {
                LinkTree link = (LinkTree) node;
                String label = link.getLabel().isEmpty()
                        ? link.getReference().getSignature()
                        : renderBody(link.getLabel());
                yield "`" + label + "`";
            }
            // Silently skip tags we don't render (@see, @since, @author, etc.)
            default -> "";
        };
    }

    /** Remove residual HTML tags from old-style Javadoc comments */
    private static String stripHtml(String text) {
        // Remove tags but keep content (e.g. <p>, <br>, <em>, <strong>)
        String stripped = text.replaceAll("<[^>]+>", " ");
        // Collapse multiple spaces/newlines
        stripped = stripped.replaceAll("[ \t]+", " ");
        stripped = stripped.replaceAll("\n{3,}", "\n\n");
        return stripped;
    }
}
