[![](https://jitpack.io/v/micycle1/markdown-doclet.svg)](https://jitpack.io/#micycle1/markdown-doclet)

# markdown-doclet

A Javadoc doclet that writes one Markdown file per class. It is intended for compact API reference output that can be read directly or used as LLM/RAG context.

## Output

```text
docs/
  MyClass.md
  AnotherClass.md
```

Each file looks roughly like:

````markdown
# MyClass

**Package:** `com.example`

Class description.

---

## myMethod

```java
public String myMethod(int value)
```

Method description.

**Parameters:** `value` - description

**Returns:** description
````

## Maven Usage

Add JitPack and configure `maven-javadoc-plugin` in the project whose docs you want to generate:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-javadoc-plugin</artifactId>
      <version>3.6.0</version>
      <configuration>
        <doclet>com.github.micycle1.doclet.MarkdownDoclet</doclet>
        <docletArtifact>
          <groupId>com.github.micycle1</groupId>
          <artifactId>markdown-doclet</artifactId>
          <version>v1.0.0</version>
        </docletArtifact>
        <useStandardDocletOptions>false</useStandardDocletOptions>
        <additionalOptions>
          <additionalOption>-outputDir</additionalOption>
          <additionalOption>${project.basedir}/docs</additionalOption>
          <additionalOption>-includeThrows</additionalOption>
          <additionalOption>true</additionalOption>
        </additionalOptions>
      </configuration>
      <executions>
        <execution>
          <id>generate-markdown-docs</id>
          <phase>package</phase>
          <goals>
            <goal>javadoc</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Run:

```bash
mvn javadoc:javadoc
```

or, if bound to `package` as above:

```bash
mvn package
```

## Options

When using Maven, pass doclet options through `<additionalOptions>`. Each option that
takes a value must be written as a pair of `<additionalOption>` entries: first the
option name, then its value.

```xml
<additionalOptions>
  <additionalOption>-outputDir</additionalOption>
  <additionalOption>${project.build.directory}/markdown-doclet</additionalOption>
  <additionalOption>-subpackages</additionalOption>
  <additionalOption>com.example.api</additionalOption>
  <additionalOption>-excludePackageNames</additionalOption>
  <additionalOption>com.example.api.internal,com.example.api.generated</additionalOption>
</additionalOptions>
```

| Option | Default | Description |
|---|---:|---|
| `-outputDir` | `references` | Output directory |
| `-subpackages` |  | Include only these package prefixes |
| `-excludePackageNames` |  | Exclude these package prefixes |
| `-exclude` |  | Alias for `-excludePackageNames` |
| `-includeParams` | `true` | Include `@param` descriptions |
| `-includeReturn` | `true` | Include `@return` descriptions |
| `-includeThrows` | `false` | Include `@throws` / `@exception` descriptions |
| `-includeDeprecated` | `true` | Include `@deprecated` notices |
| `-includeFields` | `true` | Include visible fields |
| `-includeConstructors` | `true` | Include visible constructors |
| `-includePrivate` | `false` | Include private members |
| `-includePackagePrivate` | `false` | Include package-private members |
| `-stripPackageNames` | `true` | Shorten fully qualified type names in signatures |
| `-compactParams` | `true` | Render parameters inline instead of one per bullet |
