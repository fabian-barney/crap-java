# Changelog

## Unreleased

### Changed

- CRAP scoring now uses the worse method coverage out of JaCoCo instruction and branch counters, reporting the selected coverage kind per method.
- Simplified machine-readable reports to a top-level status plus method-level entries.

## 0.4.1 - 2026-04-08

### Changed

- Resolved Gradle plugin module and JaCoCo report mappings during configuration so `crap-java-check` no longer performs execution-time build-marker discovery.
- Added root-plus-subproject Gradle coverage tests and an explicit configuration-cache reuse test before advertising support in the published plugin metadata.

## 0.4.0 - 2026-04-06

### Changed

- Renamed the public Java package namespace from `media.barney.crapjava.*` to `media.barney.crap.*`.
- Continued publishing all Java artifacts and the Gradle plugin marker through Maven Central.

### Fixed

- Wait for forcibly terminated subprocesses to exit after a timeout so Windows test and release runs do not leave the working directory locked.

### Publishing

- Re-submitted the Gradle plugin publication after adding Gradle's requested DNS verification for `barney.media`.
- Kept Maven Central as the secondary Gradle plugin channel while Plugin Portal approval remains pending.

## 0.3.2 - 2026-04-05

### Added

- Published the Gradle plugin implementation artifact to Maven Central as a secondary channel.
- Published the Gradle plugin marker artifact to Maven Central so Gradle builds can resolve `media.barney.crap-java` via `pluginManagement.repositories`.

### Changed

- Updated the release workflow to upload the Gradle plugin Maven publications through Sonatype's OSSRH compatibility endpoint and finalize the deployment automatically.
- Expanded publishing preflight to build and sign the Gradle plugin's Maven publications locally.
- Documented the Maven Central plugin-consumption path in the README for use before Plugin Portal approval completes.

## 0.3.1 - 2026-04-05

### Fixed

- Kept published Maven artifact `url` and `scm` metadata pointed at the repository root instead of artifact-specific child paths.

### Changed

- Removed release-process details from the README so it stays consumer-facing.
- Updated the contribution guide to recommend repository-neutral branch names.
- Removed the bootstrap `spec.md` document.

## 0.3.0 - 2026-04-04

### Added

- JSpecify annotations and NullAway validation in CI.
- Coverage gate tests across the CLI, Gradle plugin, and Maven plugin.
- Apache License 2.0 metadata for published artifacts.

### Changed

- Improved changed-file detection, CLI analysis flow, and Java method parsing robustness.
- Refined CRAP analysis behavior, module resolution, and Maven plugin checks for multi-module projects.
- Updated the public project description and versioned CLI examples to match the `0.3.0` release.

### Publishing

- Switched public distribution to Maven Central and the Gradle Plugin Portal.
- Added release signing, Central Portal publishing, Plugin Portal publishing, and publishing preflight automation.
- GitHub releases are now generated from the matching `CHANGELOG.md` entry for the tagged version.
