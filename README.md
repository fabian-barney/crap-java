# crap-java

`crap-java` is a shared CRAP metric toolkit for Java projects.

It combines method cyclomatic complexity with JaCoCo method coverage and reports CRAP scores.
The toolkit resolves Maven and Gradle modules natively, including standard multi-module layouts, and publishes a standalone CLI plus dedicated Gradle and Maven plugins.

## Modules

- `core`: analysis engine, build-tool-neutral CLI orchestration, and Maven/Gradle coverage runner
- `cli`: executable entrypoint that packages the core as a runnable jar
- `gradle-plugin`: self-contained Gradle plugin build exposing `media.barney.crap-java`
- `maven-plugin`: native Maven plugin exposing the `check` goal

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

## Run

Build the CLI jar:

```bash
mvn -B -pl cli -am -DskipTests package
```

From the project root you want to analyze:

```bash
java -jar cli/target/crap-java-cli-0.2.0.jar
```

## CLI

```text
--help                Print usage to stdout
(no args)             Analyze all Java files under any nested src/main/java tree
--changed             Analyze changed Java files under any nested src/main/java tree
--build-tool <tool>   Force `auto`, `maven`, or `gradle`
<file ...>            Analyze only these files
<directory ...>       Analyze all Java files under each directory's nested src/main/java trees
```

Examples:

```bash
java -jar cli/target/crap-java-cli-0.2.0.jar --help
java -jar cli/target/crap-java-cli-0.2.0.jar
java -jar cli/target/crap-java-cli-0.2.0.jar --changed
java -jar cli/target/crap-java-cli-0.2.0.jar --build-tool gradle
java -jar cli/target/crap-java-cli-0.2.0.jar --build-tool maven module-a/src/main/java/demo/Sample.java
java -jar cli/target/crap-java-cli-0.2.0.jar src/main/java/demo/Sample.java
java -jar cli/target/crap-java-cli-0.2.0.jar module-a module-b
```

## GitHub Packages

Release `0.2.0` publishes these coordinates to GitHub Packages:

- `media.barney:crap-java-core:0.2.0`
- `media.barney:crap-java-cli:0.2.0`
- `media.barney:crap-java-maven-plugin:0.2.0`
- Gradle plugin id `media.barney.crap-java` version `0.2.0`

### Gradle

Configure the plugin repository in `settings.gradle(.kts)`:

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/fabian-barney/crap-java")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .get()
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Apply the plugin in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.crap-java") version "0.2.0"
}
```

Run:

```bash
./gradlew crap-java-check
```

### Maven

Configure GitHub Packages as a plugin repository:

```xml
<pluginRepositories>
  <pluginRepository>
    <id>github</id>
    <url>https://maven.pkg.github.com/fabian-barney/crap-java</url>
  </pluginRepository>
</pluginRepositories>
```

Authenticate Maven with a matching `github` server entry, for example in `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>${env.GITHUB_ACTOR}</username>
    <password>${env.GITHUB_TOKEN}</password>
  </server>
</servers>
```

The token used here needs package read access.

Add the plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>media.barney</groupId>
      <artifactId>crap-java-maven-plugin</artifactId>
      <version>0.2.0</version>
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

Run:

```bash
mvn verify
```

## Release

Tag `v0.2.0` from `main` after the pull request checks are green. The tag-triggered release workflow publishes the Maven artifacts, publishes the Gradle plugin publications, and creates the GitHub release.

## Exit Codes

- `0` success, threshold respected
- `1` invalid CLI usage
- `2` CRAP threshold exceeded (`> 8.0`)

## Contributing

See `CONTRIBUTING.md` for the issue-linked branch, commit, and PR flow used in this repository.
