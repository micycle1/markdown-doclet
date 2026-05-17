package com.github.micycle1.doclet;

import jdk.javadoc.doclet.Doclet;

import java.util.*;

/**
 * Holds all configuration for the doclet.
 * Each field maps to a command-line option, e.g. -outputDir ./references
 *
 * In Maven, pass via:
 *   <additionalOptions>
 *     <additionalOption>-outputDir</additionalOption>
 *     <additionalOption>${project.build.directory}/references</additionalOption>
 *     <additionalOption>-includeThrows</additionalOption>
 *     <additionalOption>false</additionalOption>
 *   </additionalOptions>
 */
public class DocletConfig {

    public enum NestedTypes {
        OMIT,
        INLINE,
        SEPARATE
    }

    // ── Output ────────────────────────────────────────────────────────────────
    private String outputDir = "references";
    private List<String> subpackages = List.of();
    private List<String> excludedPackages = List.of();
    private boolean cleanOutput = true;
    private NestedTypes nestedTypes = NestedTypes.INLINE;
    private boolean minify = false;

    // ── Per-element inclusion toggles ─────────────────────────────────────────
    private boolean includeParams       = true;   // @param descriptions
    private boolean includeReturn       = true;   // @return description
    private boolean includeThrows       = false;  // @throws / @exception
    private boolean includeDeprecated   = true;   // @deprecated notice
    private boolean includeFields       = true;   // public fields
    private boolean includeConstructors = true;   // constructors
    private boolean includePrivate      = false;  // private members
    private boolean includePackagePrivate = false;

    // ── Formatting ────────────────────────────────────────────────────────────
    /**
     * When true, strip package prefixes from type names in signatures.
     * e.g. processing.core.PShape → PShape
     * Greatly reduces token count for LLM consumption.
     */
    private boolean stripPackageNames = true;

    /**
     * When true, params are rendered inline on one line:
     *   **Parameters:** `shape` — …  `threshold` — …
     * When false, each param gets its own bullet line.
     */
    private boolean compactParams = true;

    // ── Getters ───────────────────────────────────────────────────────────────

    public String  getOutputDir()          { return outputDir; }
    public List<String> getSubpackages()   { return subpackages; }
    public List<String> getExcludedPackages() { return excludedPackages; }
    public boolean isCleanOutput()         { return cleanOutput; }
    public NestedTypes getNestedTypes()     { return nestedTypes; }
    public boolean isMinify()              { return minify; }
    public boolean isIncludeParams()       { return includeParams; }
    public boolean isIncludeReturn()       { return includeReturn; }
    public boolean isIncludeThrows()       { return includeThrows; }
    public boolean isIncludeDeprecated()   { return includeDeprecated; }
    public boolean isIncludeFields()       { return includeFields; }
    public boolean isIncludeConstructors() { return includeConstructors; }
    public boolean isIncludePrivate()      { return includePrivate; }
    public boolean isIncludePackagePrivate(){ return includePackagePrivate; }
    public boolean isStripPackageNames()   { return stripPackageNames; }
    public boolean isCompactParams()       { return compactParams; }

    // ── Doclet option descriptors ─────────────────────────────────────────────

    public Set<Doclet.Option> getSupportedOptions() {
        List<Doclet.Option> opts = new ArrayList<>();

        opts.add(strOption("-outputDir", "Output directory for .md files", v -> outputDir = v));
        opts.add(strOption("-subpackages", "Included package prefixes", v -> subpackages = splitPackages(v)));
        opts.add(strOption("-exclude", "Excluded package prefixes", v -> excludedPackages = splitPackages(v)));
        opts.add(strOption("-excludePackageNames", "Excluded package prefixes", v -> excludedPackages = splitPackages(v)));
        opts.add(boolOption("-cleanOutput",       v -> cleanOutput = v));
        opts.add(strOption("-nestedTypes", "Nested type handling: omit, inline, or separate", v -> nestedTypes = parseNestedTypes(v)));
        opts.add(boolOption("-minify",            v -> minify = v));

        opts.add(boolOption("-includeParams",        v -> includeParams = v));
        opts.add(boolOption("-includeReturn",        v -> includeReturn = v));
        opts.add(boolOption("-includeThrows",        v -> includeThrows = v));
        opts.add(boolOption("-includeDeprecated",    v -> includeDeprecated = v));
        opts.add(boolOption("-includeFields",        v -> includeFields = v));
        opts.add(boolOption("-includeConstructors",  v -> includeConstructors = v));
        opts.add(boolOption("-includePrivate",       v -> includePrivate = v));
        opts.add(boolOption("-includePackagePrivate",v -> includePackagePrivate = v));
        opts.add(boolOption("-stripPackageNames",    v -> stripPackageNames = v));
        opts.add(boolOption("-compactParams",        v -> compactParams = v));

        return new HashSet<>(opts);
    }

    // ── Option builder helpers ─────────────────────────────────────────────────

    private Doclet.Option strOption(String name, String desc, java.util.function.Consumer<String> setter) {
        return new Doclet.Option() {
            public int          getArgumentCount() { return 1; }
            public String       getDescription()   { return desc; }
            public Kind         getKind()          { return Kind.STANDARD; }
            public List<String> getNames()         { return List.of(name); }
            public String       getParameters()    { return "<value>"; }
            public boolean      process(String opt, List<String> args) {
                setter.accept(args.get(0)); return true;
            }
        };
    }

    private Doclet.Option boolOption(String name, java.util.function.Consumer<Boolean> setter) {
        return new Doclet.Option() {
            public int          getArgumentCount() { return 1; }
            public String       getDescription()   { return "true/false (default varies)"; }
            public Kind         getKind()          { return Kind.STANDARD; }
            public List<String> getNames()         { return List.of(name); }
            public String       getParameters()    { return "<true|false>"; }
            public boolean      process(String opt, List<String> args) {
                setter.accept(Boolean.parseBoolean(args.get(0))); return true;
            }
        };
    }

    private static List<String> splitPackages(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;:\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static NestedTypes parseNestedTypes(String value) {
        if (value == null || value.isBlank()) {
            return NestedTypes.INLINE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "omit", "skip" -> NestedTypes.OMIT;
            case "inline", "nest" -> NestedTypes.INLINE;
            case "separate", "first-level", "firstlevel", "first_level" -> NestedTypes.SEPARATE;
            default -> throw new IllegalArgumentException(
                    "-nestedTypes must be one of: omit, inline, separate");
        };
    }
}
