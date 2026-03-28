# crap4java

`crap4java` is a shared CRAP metric toolkit for Java projects.

It combines method cyclomatic complexity with JaCoCo method coverage and reports CRAP scores.
The current CLI now resolves Maven and Gradle modules natively, including standard multi-module layouts, while the dedicated build tool plugins are being added in separate modules.

## Modules

- `core`: analysis engine, build-tool-neutral CLI orchestration, and Maven/Gradle coverage runner
- `cli`: executable entrypoint that packages the core as a runnable jar
- `gradle-plugin`: self-contained Gradle plugin build exposing `media.barney.crap4java`
- `maven-plugin`: placeholder module for the upcoming Maven integration

## Formula

`CRAP = CC^2 * (1 - coverage)^3 + CC`

- `CC` is cyclomatic complexity.
- `coverage` is method coverage fraction from JaCoCo `INSTRUCTION` counters.

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

## Build and Test

```bash
mvn test
```

Build and test the Gradle plugin module after packaging the core jar:

```bash
mvn -pl core -am package
gradle-plugin/gradlew test
```

## Run

Build the CLI jar:

```bash
mvn -pl cli -am -DskipTests package
```

From the project root you want to analyze:

```bash
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar
```

## CLI

```text
--help                Print usage to stdout
(no args)             Analyze all Java files under src/
--changed             Analyze changed Java files under src/
--build-tool <tool>   Force `auto`, `maven`, or `gradle`
<file ...>            Analyze only these files
<directory ...>       Analyze all Java files under each directory's nested src/ subtrees
```

Examples:

```bash
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar --help
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar --changed
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar --build-tool gradle
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar --build-tool maven module-a/src/main/java/demo/Sample.java
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar src/main/java/demo/Sample.java
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar module-a module-b
```

## Exit Codes

- `0` success, threshold respected
- `1` invalid CLI usage
- `2` CRAP threshold exceeded (`> 8.0`)

## Contributing

See `CONTRIBUTING.md` for the issue-linked branch, commit, and PR flow used in this repository.
