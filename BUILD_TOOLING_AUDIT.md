# Build tooling audit

## Unreleased baseline for issue #248 — 2026-10-16

This section supersedes the support and dependency conclusions of the historical
v1 audit below. Product versions remain at 1.0.1 pending separate major-release preparation.

### Selected tools and compatibility

- Full JDK 21/25; release 21 bytecode; Maven 3.9.16 minimum and primary Maven 3.10.0.
- Cognitive Java 1.0.0, NullAway 0.14.2, SpotBugs 4.10.5,
  SpotBugs Maven 4.10.5.0 and Gradle 6.5.13.
- Maven Plugin Tools 3.16.0, Central Publishing 0.12.0, Dependency 3.11.0,
  Clean 3.5.0, Resources 3.5.0, Install/Deploy 3.2.0.
- Gradle Plugin Publish 2.2.1, CycloneDX Gradle 3.5.1, and setup-java 6.1.0.
- Gradle 8.14.6/JDK 21 and Gradle 9.8.1/JDK 21/25. The older 8.14.5 and 9.7.x
  boundaries are retired. Gradle 8.14.6 includes three published high-severity security fixes.

All selected artifacts were verified against registry metadata and upstream releases.
JToon 3.0.0 metadata requires Java 21; Jackson 3.2.3 requires Java 17, and JSpecify 1.0.1
requires Java 8, so their runtime-constraint intersection is Java 21 or newer.
Error Prone 2.50.0 requires a build JDK of at least 21. Maven Install/Deploy 3.2.0
and Dependency 3.11.0 require Maven 3.9.16. The provided Maven APIs remain 3.9.16.

### Distribution and license boundary

The product now uses JToon 3.0.0, Jackson 3.2.3 with shared annotations 2.22,
Woodstox 7.3.0, and stax2-api 4.3.2. Their permissive runtime license obligations
are recorded in THIRD_PARTY_LICENSES.md and checked against the shaded archives.
The curated JToon MIT and stax2-api BSD texts were refreshed from the exact selected tags.
Jackson's FastDoubleParser and Schubfach attribution resource hashes are unchanged.

The build-only dependency licenses retain the previous delivery boundary:
SpotBugs remains LGPL tooling and Saxon-HE remains build-only; neither is included
in the distributed product runtime. The refreshed plugin publishers remain the
existing Apache, Gradle, SpotBugs, Uber, CycloneDX, and project maintainers.

### Exact-version advisory check

The fresh product component SBOM contains nine external coordinates. An OSV
query for all nine exact versions returned no advisories on 2026-10-16.
This is a dated database observation, not a guarantee of absence of vulnerabilities.

A broader Maven resolve-plugins snapshot, including implicit reporting tools,
contained 281 unique coordinates. Exact-version OSV queries flagged 15 coordinates:

| Tool boundary | Retained flagged components |
| --- | --- |
| SpotBugs | snappy 0.4 |
| Source/Javadoc plugins | aircompressor 0.27, plexus-utils 4.0.2; Javadoc also commons-beanutils 1.9.4 |
| Resources/GPG/Shade plugins | plexus-utils 3.6.0 |
| Clean/Antrun plugins | plexus-utils 4.0.2 |
| GPG plugin | bcpg-jdk18on 1.81, bcprov-jdk18on 1.81 |
| JaCoCo plugin | plexus-utils 3.0.24 |
| CycloneDX Maven plugin | cyclonedx-core-java 9.0.5, Jackson core/databind 2.17.2 |
| Implicit Site plugin | Jetty http/security/server 9.4.58.v20250814, jsoup 1.22.1, commons-beanutils 1.9.4 |

The previous Jackson 3.0.4 exception is removed: Cognitive Java 1.0.0 resolves
Jackson 3.2.3. The Snappy 0.4 advisory GHSA-8wh2-6qhj-h7j9 remains build-only.
The wider snapshot includes tools outside the previous four-plugin audit, so its
finding count is not comparable to that older audit. The Site lifecycle is not
used by CI or publication. These transitive findings are retained upstream-tool
risks, not resolved or globally declared non-applicable by this change; avoid
untrusted build inputs and reassess when upstream plugins release fixes.
Product Jackson consolidation does not remove Jackson from isolated build-plugin realms.

Reproduce the bounded graph with Maven 3.10.0 on JDK 25:

```sh
mvn -B -ntp -N -P'!quality-gates-all,quality-gate-cognitive,quality-gate-spotbugs' \
  org.apache.maven.plugins:maven-dependency-plugin:3.11.0:resolve-plugins
```

Advisory source: https://api.osv.dev/v1/querybatch (Maven package names plus exact versions).
The Gradle plugin build-tool graph is additionally exercised by hosted CI; the
Windows host cannot start Gradle's daemon because loopback connections are denied.
Final build, static-analysis, publication, and reproducibility evidence is recorded
on the implementation PR and linked issue.

## Historical v1.0.0 baseline

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

