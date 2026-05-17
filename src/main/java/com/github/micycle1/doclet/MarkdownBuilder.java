package com.github.micycle1.doclet;

/**
 * Thin wrapper around StringBuilder with convenience methods
 * for generating clean Markdown output.
 */
public class MarkdownBuilder {

    private final StringBuilder sb = new StringBuilder();
    private final boolean minify;

    public MarkdownBuilder() {
        this(false);
    }

    public MarkdownBuilder(boolean minify) {
        this.minify = minify;
    }

    public MarkdownBuilder h1(String text) {
        return line("# " + text);
    }

    public MarkdownBuilder h2(String text) {
        return line("## " + text);
    }

    public MarkdownBuilder h3(String text) {
        return line("### " + text);
    }

    public MarkdownBuilder heading(int level, String text) {
        int normalizedLevel = Math.max(1, Math.min(6, level));
        return line("#".repeat(normalizedLevel) + " " + text);
    }

    /** No-op retained for compatibility with older writer flow. */
    public MarkdownBuilder rule() {
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

    /** Inline code line. */
    public MarkdownBuilder codeBlock(String code, String language) {
        return line("`" + code + "`");
    }

    public String build() {
        String output = sb.toString().trim();
        if (minify) {
            output = output.replaceAll("\n{2,}", "\n");
        }
        return output + "\n";
    }
}
