# Third-party distribution inventory

This inventory records the runtime content distributed in crap-java binary
artifacts for issue #226. Build-only dependencies are reviewed separately in
`BUILD_TOOLING_AUDIT.md`.

| Component | Distributed in | License | Packaged evidence |
| --- | --- | --- | --- |
| JToon | CLI; runtime dependency of Gradle plugin | MIT | `META-INF/LICENSE-jtoon` in the shaded CLI |
| Jackson 2.x and 3.x | CLI; runtime dependencies of Gradle plugin | Apache-2.0 | project Apache-2.0 license and merged `META-INF/NOTICE` in the shaded CLI |
| stax2-api | CLI; runtime dependency of Gradle plugin | BSD-2-Clause | `META-INF/LICENSE-stax2-api` in the shaded CLI |
| Woodstox Core | CLI; runtime dependency of Gradle plugin | Apache-2.0 | full terms in `META-INF/LICENSE-crap-java` |
| FastDoubleParser | CLI through Jackson Core | MIT | `META-INF/FastDoubleParser-LICENSE` in the shaded CLI |
| FastDoubleParser third-party code | CLI through Jackson Core | Boost Software License 1.0 | `META-INF/FastDoubleParser-ThirdParty-LICENSE` in the shaded CLI |
| Schubfach | CLI through Jackson Core | MIT | `META-INF/Schubfach-LICENSE` in the shaded CLI |

The stax2-api license is the canonical BSD-2-Clause text from tag
`stax2-api-4.3.0`, replacing the unresolved template shipped in its binary JAR.
Woodstox Core 7.2.0 uses the same Apache-2.0 license as this project and its
tagged source and binary distributions do not contain a NOTICE file.
The root project license is copied without modification to
`META-INF/LICENSE-crap-java` in every binary JAR.
The Jackson notices from all packaged Jackson artifacts are merged into
`META-INF/NOTICE`; the verifier requires both the Jackson 2.x and 3.x notices and
the bundled FastDoubleParser and Schubfach attributions.

All listed licenses are permissive and compatible with distribution of the
combined work under Apache-2.0. No unresolved source-offer, reciprocal-license,
attribution, or notice obligation remains for the binary distribution.
