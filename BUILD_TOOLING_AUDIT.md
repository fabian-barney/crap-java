# Build tooling audit

This audit records the build-only dependency review for the v1.0.0 baseline. It was
performed on 2026-09-08 for issue #229.

## Selected versions

| Tool | Previous | Selected | License and stewardship |
| --- | --- | --- | --- |
| cognitive-java Maven plugin | 0.6.0 | 0.7.1 | Apache-2.0; maintained in the public `fabian-barney/cognitive-java` repository and released from `main` |
| Maven Compiler Plugin | 3.15.0 | 3.16.0 | Apache-2.0; maintained and released by the Apache Maven project |
| Maven JAR Plugin | 3.5.0 | 3.5.1 | Apache-2.0; maintained and released by the Apache Maven project |
| SpotBugs Maven Plugin | 4.9.8.3 | 4.10.4.1 | Apache-2.0; maintained and released by the SpotBugs organization |

All four repositories were active and unarchived when reviewed. The selected
versions have public release tags and Maven Central artifacts. No version
substitution was necessary.

## Runtime dependency changes

The old and selected plugin POMs were resolved independently and their compile and
runtime trees were compared.

- cognitive-java updates its core from 0.6.0 to 0.7.1 and its Jackson 2.x runtime
  from 2.22.1 to 2.22.2.
- Maven Compiler Plugin replaces QDox with JavaParser and updates ASM, Commons IO,
  Plexus Java, Plexus Compiler, and Plexus Utils.
- Maven JAR Plugin updates Maven Archiver, Plexus Archiver, Commons IO, XZ, and
  zstd-jni, and removes aircompressor from its resolved runtime tree.
- SpotBugs Maven Plugin updates the SpotBugs engine and its reporting stack,
  including ASM, Groovy, Log4j-to-SLF4J, Maven Doxia, Plexus, Saxon, and SLF4J.

The changed runtime dependencies retain open-source terms suitable for build use.
The graph includes weak-copyleft build tools such as the SpotBugs engine and
Saxon-HE, but plugin dependencies are not redistributed in crap-java artifacts.
The Apache-2.0 distribution inventory is therefore unchanged by this upgrade.

## Security review

An exact-version OSV query covered every unique compile/runtime dependency in both
sets of plugin graphs: 97 before the upgrade and 92 after it. The old graph had
advisories for seven coordinates. The selected graph removes the findings affecting
Commons IO 2.11.0, aircompressor 0.27, Log4j API 2.25.4, and Plexus Utils 4.0.2.

Three pre-existing build-only coordinates remain reported:

- `org.iq80.snappy:snappy:0.4` (`GHSA-8wh2-6qhj-h7j9`)
- `tools.jackson.core:jackson-core:3.0.4`
- `tools.jackson.core:jackson-databind:3.0.4`

They are unchanged by the selected upgrades and are not packaged into the core,
Maven-plugin, or Gradle-plugin binaries. The shaded CLI contains the product's
separately resolved Jackson 3.2.1 libraries, not the build plugin's Jackson 3.0.4
libraries. The Snappy issue requires decompressing attacker-controlled Snappy data;
this build does not read Snappy inputs. The Jackson findings affect parsing,
deserialization, or polymorphic-input paths; cognitive-java analyzes repository
source and serializes its own reports. Those vulnerable paths are not used by the
release build, so the retained findings are non-applicable to this build boundary.

## Compatibility evidence

- `mvn -B -ntp clean verify` passes on JDK 25 while emitting Java 17 bytecode.
- Core, CLI, and Maven-plugin unit and integration tests pass with the selected
  Compiler and JAR plugins.
- Error Prone/NullAway compilation and SpotBugs analysis resolve and execute with
  the selected plugin graph.
- The Gradle plugin build and the repository CI matrix exercise the upgraded Maven
  outputs independently. The expanded Java/Maven/Gradle boundary matrix introduced
  by #227 will rerun these selected versions before v1.0.0.

