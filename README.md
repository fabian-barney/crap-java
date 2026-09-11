# crap-java

CRAP metrics for Java.

It combines method cyclomatic complexity with JaCoCo method coverage and reports CRAP scores.
The toolkit resolves Maven and Gradle modules natively, including standard multi-module layouts, and publishes a standalone CLI plus dedicated Gradle and Maven plugins.

## Modules

- `core`: analysis engine, build-tool-neutral CLI orchestration, and Maven/Gradle coverage runner
- `cli`: executable entrypoint that bundles the core as a runnable jar
- `gradle-plugin`: self-contained Gradle plugin build exposing `media.barney.crap-java`
- `maven-plugin`: native Maven plugin exposing the `check` goal

The `core` artifact exists to support the CLI and plugins. It is internal
implementation, not a supported Java library API, and carries no direct binary
or source compatibility guarantee.

## Requirements and support

All artifacts contain Java 17-compatible bytecode. The supported runtime and
build-tool combinations are:

| Integration | Supported versions | Tested boundaries |
| --- | --- | --- |
| CLI | Java 17, 21, and 25 | Java 17, 21, and 25 |
| Maven plugin | Maven 3.9.x on Java 17, 21, or 25 | Maven 3.9.0 and 3.9.16 on each Java version |
| Gradle plugin | Gradle 8.14.x on Java 17 or 21; Gradle 9.7.x on Java 17, 21, or 25 | Gradle 8.14.5 on Java 17/21; Gradle 9.7.1 on Java 17/21/25 |

Java 17 and Maven 3.9.0 are the minimums enforced by the Maven build and plugin
metadata. Gradle 8.14 is not supported on Java 25; use Gradle 9.7.x for that
runtime. The repository wrapper uses Gradle 9.7.1. Versions outside this matrix
may work but are not part of the v1 support commitment.

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
VERSION=1.0.0
CRAP_JAVA_JAR="cli/target/crap-java-cli-${VERSION}.jar"
java -jar "$CRAP_JAVA_JAR"
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
--threshold <number>  Override the CRAP threshold (`6.0` by default)
<file ...>            Analyze only these files
<directory ...>       Analyze all Java files under each directory's nested source roots
```

Value-taking long options may also be written with inline assignment, such as
`--build-tool=maven`, `--format=json`, or `--exclude='module-a/**'`.

Examples:

```bash
VERSION=1.0.0
CRAP_JAVA_JAR="cli/target/crap-java-cli-${VERSION}.jar"
java -jar "$CRAP_JAVA_JAR" --help
java -jar "$CRAP_JAVA_JAR"
java -jar "$CRAP_JAVA_JAR" --changed
java -jar "$CRAP_JAVA_JAR" --build-tool gradle
java -jar "$CRAP_JAVA_JAR" --build-tool=maven
java -jar "$CRAP_JAVA_JAR" --format json
java -jar "$CRAP_JAVA_JAR" --format none --junit-report target/crap-java/TEST-crap-java.xml
java -jar "$CRAP_JAVA_JAR" --format json --output target/crap-java/report.json
java -jar "$CRAP_JAVA_JAR" --failures-only=false --format json
java -jar "$CRAP_JAVA_JAR" --omit-redundancy=false --format json
java -jar "$CRAP_JAVA_JAR" --agent
java -jar "$CRAP_JAVA_JAR" --agent --format junit --output target/crap-java/TEST-crap-java-primary.xml
java -jar "$CRAP_JAVA_JAR" --junit-report target/crap-java/TEST-crap-java.xml
java -jar "$CRAP_JAVA_JAR" --exclude 'module-a/**' --exclude-class '.*MapperImpl$'
java -jar "$CRAP_JAVA_JAR" --exclude='module-a/**' --exclude-class='.*MapperImpl$'
java -jar "$CRAP_JAVA_JAR" --source-root src/java --source-root src/main/java17
java -jar "$CRAP_JAVA_JAR" --build-tool maven module-a/src/main/java/demo/Sample.java
java -jar "$CRAP_JAVA_JAR" src/main/java/demo/Sample.java
java -jar "$CRAP_JAVA_JAR" module-a module-b
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
The per-method `status` field is omitted when `--omit-redundancy` is enabled;
consumers can derive `skipped` when `crap` is `null`, `failed` when `crap` is
greater than the top-level `threshold`, and `passed` otherwise.
`src` is the project-relative source file path. `covKind` identifies the
coverage input used for each CRAP score (`instruction`, `branch`, or `N/A`).
`N/A` also covers methods whose JaCoCo coverage cannot be attributed
unambiguously to one source method. The corresponding JUnit property retains
the descriptive name `coverageKind`.
Full primary reports also include exclusion audit counts when any source was
considered. The `exclusions` object contains `candidateFiles`, `analyzedFiles`,
`excludedFiles`, `excludedClasses`, `excludedFileReasons`, and
`excludedClassReasons`; each reason entry contains `reason` and `count`.
Optimized primary reports produced through `--agent` omit that audit detail by
default to stay focused on actionable failures. The JUnit sidecar keeps the
complete exclusion audit.

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

The default threshold is `6.0`. Values below `4.0` print a warning because they
are likely too noisy; values above `8.0` print a warning because they are too
lenient even for hard gates. The warning recommends `8.0` for hard gates,
targeting `6.0` during implementation, and using the `6.0` default when in
doubt.

The JUnit XML format exposes each analyzed method as a testcase and is shaped
for GitLab's Tests tab. Testcases use the project-relative source path for
`classname` and `file`, include a concise metric summary in the testcase `name`
as `method:lineStart [CRAP=score, CC=complexity, Cov=percent (kind)]`, write the
measured analysis duration on the testsuite, and divide that duration across
testcases. Methods with CRAP scores over the configured threshold fail, methods
with unavailable coverage are skipped, and testcase-level `system-out` plus
failure/skipped element text include CRAP score, threshold, coverage kind,
source path, and line range. Custom properties remain for tools that read them,
but GitLab-visible details do not rely on properties.

## v1 compatibility contract

Semantic Versioning applies to the user-facing CLI options and their behavior,
Maven and Gradle plugin configuration, process exit codes, and the JSON, TOON,
and JUnit output schemas. Within the 1.x line, existing names and meanings on
those surfaces will not be removed or changed incompatibly. Additions remain
possible when existing correct configurations and parsers continue to work.
The human-readable text report is intended for people and is not a stable
machine schema.

TOON output follows TOON Specification 4.1 for the entire 1.x line. An
incompatible upstream TOON format change requires a new crap-java major
version. The `core` Java artifact remains internal and is excluded from the
binary/source compatibility promise; supported integrations are the CLI and
the two build plugins.

Users upgrading from 0.6.4 must account for the TOON 4.1 empty-array encoding.
See [Migrating from 0.6.4 to 1.0.0](MIGRATING.md).

## Distribution

Releases are published through Maven Central, with the Gradle Plugin Portal as
the primary Gradle plugin channel. The current installation coordinates are:

- `media.barney:crap-java-core:1.0.0`
- `media.barney:crap-java-cli:1.0.0`
- `media.barney:crap-java-maven-plugin:1.0.0`
- `media.barney:crap-java-gradle-plugin:1.0.0`
- Gradle plugin id `media.barney.crap-java` version `1.0.0`

### Direct CLI download

Download the executable JAR from the matching GitHub Release and run it on a
supported Java runtime:

```bash
VERSION=1.0.0
curl --fail --location --remote-name \
  "https://github.com/fabian-barney/crap-java/releases/download/v${VERSION}/crap-java-${VERSION}.jar"
java -jar "crap-java-${VERSION}.jar" --help
```

Each release also includes four CycloneDX 1.6 JSON component SBOMs, SHA-256
and SHA-512 manifests, and ASCII-armored detached PGP signatures for the JAR,
SBOMs, and checksum manifests.

### Verify a GitHub release

Install GitHub CLI, GnuPG, and GNU `sha256sum`/`sha512sum`, then download and
verify the complete release bundle:

```bash
set -euo pipefail
VERSION=1.0.0
RELEASE_DIR="crap-java-${VERSION}-release"
mkdir -p "$RELEASE_DIR"
gh release download "v${VERSION}" \
  --repo fabian-barney/crap-java \
  --dir "$RELEASE_DIR" \
  --clobber
curl --fail --location \
  --output "$RELEASE_DIR/release-signing-key.asc" \
  "https://raw.githubusercontent.com/fabian-barney/crap-java/v${VERSION}/.github/release-signing-key.asc"

cd "$RELEASE_DIR"
test "$(gpg --show-keys --with-colons release-signing-key.asc \
  | awk -F: '$1 == "fpr" { print $10; exit }')" \
  = "E6BB1FB6EE83EEAB7B408C6B5CA409BD8EE61724"
gpg --import release-signing-key.asc
sha256sum --check --strict SHA256SUMS
sha512sum --check --strict SHA512SUMS
for payload in crap-java-"${VERSION}".jar *.cdx.json SHA256SUMS SHA512SUMS; do
  gpg --verify "${payload}.asc" "$payload"
done

SOURCE_DIGEST="$(gh api \
  "repos/fabian-barney/crap-java/commits/v${VERSION}" --jq .sha)"
gh attestation verify "crap-java-${VERSION}.jar" \
  --repo fabian-barney/crap-java \
  --signer-workflow fabian-barney/crap-java/.github/workflows/release.yml \
  --source-digest "$SOURCE_DIGEST"
gh attestation verify "crap-java-${VERSION}.jar" \
  --repo fabian-barney/crap-java \
  --signer-workflow fabian-barney/crap-java/.github/workflows/release.yml \
  --source-digest "$SOURCE_DIGEST" \
  --predicate-type https://cyclonedx.org/bom
```

The first attestation command verifies build provenance; the second verifies
that the CLI JAR is bound to its CycloneDX SBOM. The same two attestation types
are published for the core, Maven plugin, and Gradle plugin artifacts in their
registry builds.

### Gradle Plugin Portal

Apply the plugin in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.crap-java") version "1.0.0"
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
    threshold.set(8.0)
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
    id("media.barney.crap-java") version "1.0.0"
}
```

The marker publication lives at
`media.barney.crap-java:media.barney.crap-java.gradle.plugin:1.0.0` and
resolves to the implementation artifact
`media.barney:crap-java-gradle-plugin:1.0.0`.

### Maven Central

Add the plugin:

```xml
<properties>
  <crap-java.version>1.0.0</crap-java.version>
</properties>

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
      <version>${crap-java.version}</version>
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
mvn verify -DcrapJava.threshold=8.0
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
