# crap-java

CRAP metrics for Java.

It combines method cyclomatic complexity with JaCoCo method coverage and reports CRAP scores.
The toolkit resolves Maven and Gradle modules natively, including standard multi-module layouts, and publishes a standalone CLI plus dedicated Gradle and Maven plugins.

## Modules

- `core`: analysis engine, build-tool-neutral CLI orchestration, and Maven/Gradle coverage runner
- `cli`: executable entrypoint that bundles the core as a runnable jar
- `gradle-plugin`: self-contained Gradle plugin build exposing `media.barney.crap-java`
- `maven-plugin`: native Maven plugin exposing the `check` goal

## Formula

`CRAP = CC^2 * (1 - coverage)^3 + CC`

- `CC` is cyclomatic complexity.
- `coverage` is the lower available method coverage fraction from JaCoCo
  `INSTRUCTION` and `BRANCH` counters. JaCoCo omits `BRANCH` counters for
  branchless methods, so those methods use instruction coverage.

## Cyclomatic Complexity Model

Java method complexity starts at `1` and increments for each AST decision node:
loops, `if`, conditional expressions, `catch`, short-circuit boolean operators,
and switch case clauses. Switch handling follows the javac AST: each
`case`/`default` clause contributes `1`, regardless of how many constants appear
in that clause. For example, `case A, B, C ->` contributes `1`, not `3`, and
`default` also contributes `1`.

Lambda bodies are not first-class report rows in the current source-first model.
Their internal decision nodes are excluded from the enclosing source method's
complexity, and javac synthetic JaCoCo methods named like `lambda$...$<digits>`
are ignored during coverage attribution.

Anonymous-class bodies are also outside the supported source-method scope.
`crap-java` does not synthesize `$N` owners from source, does not report methods
or nested declarations inside anonymous classes, and does not fold anonymous-body
complexity into the enclosing source method.

## Coverage Pipeline

For each resolved module today:

1. Detect Maven or Gradle automatically, unless `--build-tool` is supplied.
2. Delete stale JaCoCo artifacts for the detected build tool.
3. Run the module-scoped coverage command:
   - Maven: `mvn` or `mvnw`, using JaCoCo `0.8.13`
   - Gradle: `gradle` or `gradlew`, running `test` and `jacocoTestReport`
4. Read the module report:
   - Maven: `target/site/jacoco/jacoco.xml`
   - Gradle: `build/reports/jacoco/test/jacocoTestReport.xml`
5. Analyze the selected Java files for that module

For Maven CLI coverage generation, crap-java invokes
`org.jacoco:jacoco-maven-plugin:0.8.13` explicitly. It does not inspect or
reuse a `jacoco-maven-plugin` version pinned in the analyzed project's POM. If
your build requires a different JaCoCo version, generate the XML report in your
own Maven build and run the Maven plugin path below, which consumes that report
without starting another coverage run.

Source discovery walks `src/main/java` roots by default without following
directory symlinks. The CLI can override those roots with repeatable
`--source-root <path>` options, the Maven plugin uses configured compile source
roots when they differ from Maven defaults, and the Gradle plugin reads the
Java plugin's configured `main` source set. Symlinked Java files inside a source
root can still be selected and are reported using the symlink path rather than a
canonicalized target path.

## Build and Test

```bash
mvn -B -pl cli -am package
```

Build and test the Gradle plugin module after packaging the core jar:

```bash
mvn -B -pl core -am package
cd gradle-plugin
./gradlew test
```

Build and test the Maven plugin module, including its invoker integration fixtures:

```bash
mvn -B -pl maven-plugin -am verify
```

## Shared Cognitive Gate

Repository CI also runs the shared published `cognitive-java` Maven plugin as a
separate `cognitive-java Gate` job. The plugin resolves from Maven Central, and
the current version is controlled by the `cognitive-java.version` property in
`pom.xml`.

From the repository root, run the same gate locally with:

```bash
mvn -B cognitive-java:check
```

`mvn -B verify` now also includes the cognitive gate at the reactor root.

## Self-Hosting Gate Scope

Consumer Maven repos standardize on `mvn -B -ntp verify`, but this repository
keeps dedicated self-hosting gate jobs where needed to preserve full-repo metric
ownership across the embedded `gradle-plugin/` source tree.

In CI:

- `crap-java Gate` owns `core`, `cli`, `maven-plugin`, and `gradle-plugin/src/main/java`
- `cognitive-java Gate` owns the same full-repo source scope
- `Gradle Plugin` validates Gradle plugin build and test behavior only

The standalone `Gradle Plugin` job is not the owner of metric failures for
`gradle-plugin/src/main/java`; those failures still belong to the metric gate
jobs.

## Run

Build the CLI jar:

```bash
mvn -B -pl cli -am -DskipTests package
```

From the project root you want to analyze:

```bash
java -jar cli/target/crap-java-cli-0.6.0.jar
```

## CLI

```text
--help                Print usage to stdout
(no args)             Analyze all Java files under any nested source root
--changed             Analyze changed Java files under any nested source root
--build-tool <tool>   Force `auto`, `maven`, or `gradle`
--format <format>     Write `toon`, `json`, `text`, `junit`, or `none` output (`toon` by default)
--agent               Apply AI-agent defaults: `toon`, failures only, omit redundancy
--failures-only[=true|false]  Only include failing methods in the primary report
--omit-redundancy[=true|false]  Omit redundant method fields from the primary report
--exclude <glob>     Exclude source paths by normalized relative glob; repeatable
--exclude-class <regex>  Exclude fully-qualified class names by regex; repeatable
--exclude-annotation <name>  Exclude classes by annotation name; repeatable
--use-default-exclusions[=true|false]  Enable built-in generated-code exclusions (`true` by default)
--source-root <path>  Override production source roots; repeatable
--output <path>       Write the selected output format to a file instead of stdout
--junit-report <path> Also write a JUnit XML report for CI test-report UIs
--threshold <number>  Override the CRAP threshold (`8.0` by default)
<file ...>            Analyze only these files
<directory ...>       Analyze all Java files under each directory's nested source roots
```

Value-taking long options may also be written with inline assignment, such as
`--build-tool=maven`, `--format=json`, or `--exclude='module-a/**'`.

Examples:

```bash
java -jar cli/target/crap-java-cli-0.6.0.jar --help
java -jar cli/target/crap-java-cli-0.6.0.jar
java -jar cli/target/crap-java-cli-0.6.0.jar --changed
java -jar cli/target/crap-java-cli-0.6.0.jar --build-tool gradle
java -jar cli/target/crap-java-cli-0.6.0.jar --build-tool=maven
java -jar cli/target/crap-java-cli-0.6.0.jar --format json
java -jar cli/target/crap-java-cli-0.6.0.jar --format none --junit-report target/crap-java/TEST-crap-java.xml
java -jar cli/target/crap-java-cli-0.6.0.jar --format json --output target/crap-java/report.json
java -jar cli/target/crap-java-cli-0.6.0.jar --failures-only=false --format json
java -jar cli/target/crap-java-cli-0.6.0.jar --omit-redundancy=false --format json
java -jar cli/target/crap-java-cli-0.6.0.jar --agent
java -jar cli/target/crap-java-cli-0.6.0.jar --agent --format junit --output target/crap-java/TEST-crap-java-primary.xml
java -jar cli/target/crap-java-cli-0.6.0.jar --junit-report target/crap-java/TEST-crap-java.xml
java -jar cli/target/crap-java-cli-0.6.0.jar --threshold 6
java -jar cli/target/crap-java-cli-0.6.0.jar --threshold=6
java -jar cli/target/crap-java-cli-0.6.0.jar --exclude 'module-a/**' --exclude-class '.*MapperImpl$'
java -jar cli/target/crap-java-cli-0.6.0.jar --exclude='module-a/**' --exclude-class='.*MapperImpl$'
java -jar cli/target/crap-java-cli-0.6.0.jar --source-root src/java --source-root src/main/java17
java -jar cli/target/crap-java-cli-0.6.0.jar --build-tool maven module-a/src/main/java/demo/Sample.java
java -jar cli/target/crap-java-cli-0.6.0.jar src/main/java/demo/Sample.java
java -jar cli/target/crap-java-cli-0.6.0.jar module-a module-b
```

The CLI writes only the requested primary report format to stdout unless
`--output` is set. Warnings and threshold errors are written to stderr. Use
`--format none` when you only want the exit status or a JUnit sidecar.
Report paths passed to `--output` and `--junit-report` are filesystem targets:
relative paths resolve against the analyzed project root, absolute paths remain
absolute, and normalized `..` segments may target locations outside that root.
Keep those values fixed or otherwise trusted in CI configurations.

Machine-readable primary reports include top-level `status` (`passed` or
`failed`) and `threshold` values. Method entries use compact fields `status`,
`crap`, `cc`, `cov`, `covKind`, `method`, `src`, `lineStart`, and `lineEnd`.
`src` is the project-relative source file path. `coverageKind` identifies the
coverage input used for each CRAP score (`instruction`, `branch`, or `N/A`).
`N/A` also covers methods whose JaCoCo coverage cannot be attributed
unambiguously to one source method.
Full primary reports also include exclusion audit counts when any source was
considered; optimized primary reports produced through `--agent` omit that audit
detail by default to stay focused on actionable failures. The JUnit sidecar
keeps the complete exclusion audit.

Built-in exclusions are conservative and generated-code focused. They exclude
source files under any directory segment containing `generated`, source files
under `**/src/main/java-gen/**`, and classes annotated with any annotation whose
simple name is `Generated` regardless of package. Default class-name regexes
are `(^|.*\.)generated(\..*)?`, `(^|.*\.)gen(\..*)?`,
`(^|.*\.)[^.]*MapperImpl$`, `(^|.*\.)Dagger[^.]*$`,
`(^|.*\.)Hilt_[^.]*$`, and `(^|.*\.)AutoValue_[^.]*$`.
The defaults intentionally do not exclude handwritten-looking parser/listener/
visitor classes, `Immutable*` classes, QueryDSL metamodels, vendor trees,
examples, migrations, bootstrap/configuration classes, or operational scripts.
User exclusions compose with those defaults unless
`--use-default-exclusions=false` is set.

`--agent` is a composite shortcut for `--format toon --failures-only
--omit-redundancy` when those settings are not overridden explicitly.
`--failures-only` and `--omit-redundancy` affect only the primary report.
Assigned boolean CLI values must be lowercase `true` or `false`; bare boolean flags mean `true`.
`--junit-report <path>` always writes the complete unfiltered JUnit XML sidecar,
and it can be combined with any primary format, including `none`.

The default threshold is `8.0`. Values below `4.0` print a warning because they
are likely too noisy; values above `8.0` print a warning because they are too
lenient even for hard gates. The warning recommends `8.0` for hard gates,
targeting `6.0` during implementation, and using the `8.0` default when in
doubt.

The JUnit XML format exposes each analyzed method as a testcase and is shaped
for GitLab's Tests tab. Testcases use the project-relative source path for
`classname` and `file`, use `method:lineStart` as the testcase `name`, write the
measured analysis duration on the testsuite, and divide that duration across
testcases. Methods with CRAP scores over the configured threshold fail, methods
with unavailable coverage are skipped, and failure/skipped element text includes
CRAP score, threshold, coverage kind, source path, and line range. Custom
properties remain for tools that read them, but GitLab-visible details do not
rely on properties.

## Distribution

The current `0.6.0` release ships through Maven Central, with the Gradle Plugin Portal as the primary Gradle plugin channel:

- `media.barney:crap-java-core:0.6.0`
- `media.barney:crap-java-cli:0.6.0`
- `media.barney:crap-java-maven-plugin:0.6.0`
- Gradle plugin id `media.barney.crap-java` version `0.6.0`

### Gradle Plugin Portal

Apply the plugin in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.crap-java") version "0.6.0"
}
```

No custom `pluginManagement` repository configuration is required for published releases.

Run:

```bash
./gradlew crap-java-check
```

By default the Gradle plugin writes no primary report and writes a JUnit XML
sidecar to `build/reports/crap-java/TEST-crap-java.xml`.

Configure default report behavior in `build.gradle(.kts)`:

```kotlin
crapJava {
    threshold.set(6.0)
    format.set("json")
    agent.set(false)
    failuresOnly.set(false)
    omitRedundancy.set(true)
    output.set(layout.buildDirectory.file("reports/crap-java/report.json"))
    junit.set(true)
    junitReport.set(layout.buildDirectory.file("reports/crap-java/custom-junit.xml"))
    excludes.set(listOf("module-a/**"))
    excludeClasses.set(listOf(".*MapperImpl$"))
    excludeAnnotations.set(listOf("Generated"))
    useDefaultExclusions.set(true)
}
```

`agent` switches the default primary report to TOON and defaults
`failuresOnly` and `omitRedundancy` to `true` unless you override them
explicitly. The same properties are available on individual
`CrapJavaCheckTask` instances for task-specific overrides.

### Maven Central Gradle Plugin

If you want to prefer resolving the Gradle plugin from Maven Central, add Maven Central ahead of the Plugin Portal in `settings.gradle(.kts)`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply the same plugin id in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.crap-java") version "0.6.0"
}
```

The marker publication lives at
`media.barney.crap-java:media.barney.crap-java.gradle.plugin:0.6.0` and
resolves to the implementation artifact
`media.barney:crap-java-gradle-plugin:0.6.0`.

### Maven Central

Add the plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.13</version>
      <executions>
        <execution>
          <goals>
            <goal>prepare-agent</goal>
          </goals>
        </execution>
        <execution>
          <id>report</id>
          <phase>verify</phase>
          <goals>
            <goal>report</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
    <plugin>
      <groupId>media.barney</groupId>
      <artifactId>crap-java-maven-plugin</artifactId>
      <version>0.6.0</version>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

The Maven plugin consumes the JaCoCo XML files produced by your build. It does
not spawn a nested Maven run to generate coverage, so your project's configured
JaCoCo version remains in control.

No custom `<pluginRepositories>` or consumer-side authentication are required for published releases.

Run:

```bash
mvn verify
```

By default the Maven plugin writes no primary report and writes a JUnit XML
sidecar to `target/crap-java/TEST-crap-java.xml`.

Control the reports with:

```bash
mvn verify -DcrapJava.format=json -DcrapJava.output=target/crap-java/report.json
mvn verify -DcrapJava.agent=true
mvn verify -DcrapJava.failuresOnly=false -DcrapJava.omitRedundancy=true
mvn verify -DcrapJava.junit=false
mvn verify -DcrapJava.junitReport=target/custom-crap-java.xml
mvn verify -DcrapJava.threshold=6.0
mvn verify -DcrapJava.excludes='module-a/**,**/custom-generated/**'
mvn verify -DcrapJava.excludeClasses='.*MapperImpl$' -DcrapJava.excludeAnnotations=Generated
mvn verify -DcrapJava.useDefaultExclusions=false
```

In comma-separated Maven properties, escape a literal comma as `\,`, for
example `-DcrapJava.excludeClasses='demo.Name{1\,3}$'`.

Defaults: `crapJava.format=none`, `crapJava.agent=false`, and
`crapJava.junit=true`. `crapJava.agent=true` switches the default primary report
to TOON and defaults `crapJava.failuresOnly` and `crapJava.omitRedundancy` to
`true` unless they are supplied explicitly.

Equivalent XML configuration is available through `<excludes>`,
`<excludeClasses>`, `<excludeAnnotations>`, and
`<useDefaultExclusions>`.

Override the JUnit XML path with:

```bash
mvn verify -DcrapJava.junitReport=target/custom-crap-java.xml
```

Override the threshold with:

```bash
mvn verify -DcrapJava.threshold=6.0
```

In GitLab CI, upload the generated XML with `artifacts:reports:junit`. In
GitHub Actions, upload the file as an artifact or feed it into a JUnit
test-report action.

## Exit Codes

- `0` success, threshold respected
- `1` invalid CLI usage
- `2` CRAP threshold exceeded (`> configured threshold`)

## Contributing

See `CONTRIBUTING.md` for the issue-linked branch, commit, and PR flow used in this repository.
