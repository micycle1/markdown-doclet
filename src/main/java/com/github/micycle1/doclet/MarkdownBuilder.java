package com.github.micycle1.doclet;

/**
 * Thin wrapper around StringBuilder with convenience methods
 * for generating clean Markdown output.
 */
public class MarkdownBuilder {

    private final StringBuilder sb = new StringBuilder();

    public MarkdownBuilder h1(String text) {
        return line("# " + text);
    }

    public MarkdownBuilder h2(String text) {
        return line("## " + text);
    }

    public MarkdownBuilder h3(String text) {
        return line("### " + text);
    }

    /** Horizontal rule separator between sections */
    public MarkdownBuilder rule() {
        sb.append("\n---\n\n");
        return this;
    }

    /** Append text + newline */
    public MarkdownBuilder line(String text) {
        sb.append(text).append("\n");
        return this;
    }

    /** Append a blank line */
    public MarkdownBuilder line() {
        sb.append("\n");
        return this;
    }

    /** Append text without a trailing newline (for building inline content) */
    public MarkdownBuilder append(String text) {
        sb.append(text);
        return this;
    }

    /**
     * Fenced code block.
     *
     * @param code     the code content
     * @param language fence language hint, e.g. "java"
     */
    public MarkdownBuilder codeBlock(String code, String language) {
        sb.append("```").append(language).append("\n")
          .append(code).append("\n")
          .append("```\n\n");
        return this;
    }

    public String build() {
        return sb.toString();
    }
}
