# Migrating from 1.x to 2.0.0

Version 2.0.0 requires a full JDK 21 or 25 and emits Java 21 bytecode.
Java 17 runtime support is removed as a project policy decision; this does not
mean that every vendor's Java 17 distribution is EOL. The parser still requires
`jdk.compiler` and accepts the running JDK's supported non-preview source syntax.

Use Maven 3.9.16 or newer in 3.9.x, or Maven 3.10.x. Use Gradle 8.14.6 on JDK 21,
or Gradle 9.8.1 on JDK 21/25. Keep the existing plugin names and configuration.

Update CLI filenames, Maven coordinates, the Gradle plugin version, and cached
artifacts from `1.0.1` to `2.0.0` together. Then run the existing CLI commands
or plugin checks and review reports consumed by automation before deployment.

Reporting now uses Jackson 3.2.3 and JToon 3.0.0. JSON/JUnit field meanings, text
formatting, exit codes, filtering, and full JUnit sidecars retain their contracts.
JToon 3 advances the TOON specification beyond the published 1.x TOON 4.1 contract;
review machine-consumer fixtures, numeric encodings, escaping, and empty arrays
before upgrading. There is no legacy runtime or legacy TOON mode.

Cognitive Java 1.0.0 correctly counts mixed Boolean operator transitions in
infix order. Its scores may increase; fix affected methods rather than raising
quality thresholds. NullAway and SpotBugs upgrades may identify additional
nullability or constructor/finalizer problems.

These changes do not alter artifacts already published as 1.0.1.
The following migration notes describe historical releases and retain their
original support and format statements.

# Migrating from 0.6.4 to 1.0.0

Version 1.0.0 establishes the supported public contract for crap-java. The CLI,
Maven plugin, and Gradle plugin keep their existing names and configuration,
but TOON output has one intentional breaking change.

## Update the runtime and build tools

- Run the CLI and plugins on Java 17, 21, or 25.
- Use Maven 3.9.0 or newer within the 3.9.x line.
- Use Gradle 8.14.x on Java 17 or 21, or Gradle 9.7.x on Java 17, 21, or 25.
- Replace every `0.6.4` CLI filename, Maven coordinate, Gradle plugin version,
  and cached artifact with `1.0.0` at the same time.

The artifacts still contain Java 17-compatible bytecode. The `core` artifact is
internal; applications should integrate through the CLI or a build plugin.

## Migrate TOON consumers

Version 0.6.4 used JToon 1.0.9. Version 1.0.0 uses JToon 2.0.2 and TOON
Specification 4.1. Empty method arrays now use the canonical representation:

```toon
methods: []
```

Update parsers, snapshots, and golden files that expect the legacy empty-list
encoding. Do not infer a tabular header when `methods` is empty. Non-empty
method lists remain tables whose declared columns include `covKind`, for
example:

```toon
methods[1]{status,crap,cc,cov,covKind,method,src,lineStart,lineEnd}:
  passed,1,1,100,instruction,example,src/main/java/demo/Sample.java,4,6
```

The compact primary-report field is named `covKind`. `coverageKind` is used
only as the corresponding JUnit property name. The JSON field remains
`covKind`.

TOON 4.1 semantics are fixed for the entire 1.x release line. A future
incompatible upstream TOON change requires a new crap-java major version.

## Verify the upgrade

1. Run the existing command or plugin task with `--format toon` and with any
   JSON or JUnit outputs consumed by automation.
2. Exercise a project with no selected methods and confirm the consumer accepts
   `methods: []`.
3. Confirm successful analysis exits with `0`, invalid input with `1`, and a
   threshold violation with `2`.
4. Refresh snapshots only after reviewing schema differences.
5. Verify the downloaded release checksums, PGP signatures, and GitHub
   attestations using the commands in the README.

