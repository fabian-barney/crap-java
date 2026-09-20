# Releasing crap-java

This runbook covers normal releases from `main` and isolated patch releases
from single-segment `release/hotfix/<slug>` branches. A release is complete
only when Maven Central, the Gradle Plugin Portal, the signed GitHub assets,
and GitHub attestations are all public and verified.

## Access and prerequisites

The releaser needs repository-owner access and GitHub CLI authentication for
preparation and recovery work. CI owns the private signing key whose
fingerprint is `E6BB1FB6EE83EEAB7B408C6B5CA409BD8EE61724`; maintainers do not need to
unlock the key, create a tag, or approve a deployment locally.

The `release` environment accepts deployments only from protected branches and
does not require a manual reviewer. The following Actions secrets deliberately
remain at repository scope as an accepted residual risk:

- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`
- `MAVEN_CENTRAL_TOKEN_PASSWORD`
- `MAVEN_CENTRAL_TOKEN_USERNAME`
- `MAVEN_GPG_PASSPHRASE`
- `MAVEN_GPG_PRIVATE_KEY`

Only the environment-gated publish job may reference them. Never print or
include their values in issues, commits, artifacts, or logs. In particular,
never copy `MAVEN_GPG_PASSPHRASE` into command-line arguments or files; the
workflow supplies it to GPG through a private file descriptor. Existing
publishing-plugin credentials remain masked when their command interfaces
require workflow arguments.

Before preparing a release, confirm:

- the release tracker and all prerequisites are closed;
- the worktree is clean and the release branch is synchronized with its remote;
- every change since the selected baseline is linked to release scope;
- `verify / required` is green on the source commit;
- the release version does not already exist in either registry, as a tag, or
  as a GitHub Release.

## Prepare through a pull request

Use one issue-linked release branch. Set the root Maven version, every module
parent/project version, and the Gradle project version to the exact release
version. Move `Unreleased` changelog entries under the version and actual date,
update current installation examples and release notes, and preserve old
versions only in historical or migration context.

Run the local gates supported by the current host:

```bash
VERSION=x.y.z # replace with the version being prepared
bash .github/scripts/verify-version-alignment.sh --expected-version "$VERSION"
mvn -B -ntp verify
mvn -B -ntp -P'!quality-gates-all,release' \
  -DskipTests -Dgpg.skip=true -Dcentral.skipPublishing=true verify
java .github/scripts/VerifyLegalContents.java
```

The pull request must close its issue, pass the full hosted compatibility and
reproducibility matrix, resolve every review conversation, and receive a fresh
Copilot review after its latest push. Merge only through a merge commit.

Merging that pull request is the irreversible release action. A protected-branch
push that changes the aligned project version from its first parent is treated
as an intentional release candidate. Ordinary merges that leave the version
unchanged finish the `Release` workflow as a successful no-op without accessing
publishing secrets.

## Normal release from main

1. Merge the reviewed release-preparation pull request into protected `main`.
2. Monitor both the merged commit's `verify / required` check and its `Release`
   workflow run.
3. The secret-free candidate job validates the stable version increase,
   aligned Maven and Gradle versions, dated changelog heading, protected source,
   and absence of release collisions. It waits for `verify / required` on the
   exact merge commit.
4. The environment-gated job imports the existing GitHub GPG secrets, verifies
   the private-key fingerprint, creates and verifies the immutable signed
   annotated tag, and pushes it with `GITHUB_TOKEN`.
5. The same run creates the draft GitHub Release, publishes both registries,
   uploads the signed bundle, verifies every target and attestation, and only
   then publishes the draft.

Do not create release tags manually. Tag pushes do not trigger publication,
and a tag already present on an initial workflow attempt is treated as an
interference failure.

## Isolated hotfix release

Use this path only when the patch must not include newer `main` work.

1. Create `release/hotfix/<slug>` from the latest stable tag. The protected
   branch history must contain that tag.
2. Land the minimal fix and patch-version preparation through reviewed PRs into
   that branch. The same `verify / required` policy applies.
3. Merge the patch-version preparation into the protected hotfix branch. That
   merge automatically signs the tag and publishes exactly as on `main`.
4. After publication, merge the hotfix branch back into `main` through a PR.
   Resolve conflicts without dropping the released fix or release history.
5. Delete the hotfix branch only after the merge-back is complete.

The source validator rejects unprotected hotfix branches and branches that do
not contain the preceding stable tag.

## Verify publication

The GitHub Release must be non-draft, marked latest when appropriate, and point
to the same commit as the signed tag. It must contain exactly the executable
CLI JAR, four CycloneDX JSON SBOMs, `SHA256SUMS`, `SHA512SUMS`, and a detached
`.asc` signature for each of those seven payload/manifest files.

Use the README verification procedure for checksums, signatures, build
provenance, and SBOM attestations. Also verify all four artifact families on
Maven Central have main, sources, and Javadoc JARs plus PGP signatures:

- `media.barney:crap-java-core:VERSION`
- `media.barney:crap-java-cli:VERSION`
- `media.barney:crap-java-maven-plugin:VERSION`
- `media.barney:crap-java-gradle-plugin:VERSION`

Finally, open
`https://plugins.gradle.org/plugin/media.barney.crap-java/VERSION` and confirm
the version is available. Close the release issue and umbrella tracker only
after all checks pass.

## Failure recovery

- Before any target is public, leave the GitHub Release as a draft. For a
  transient service or credential failure, correct the external configuration
  and re-run the failed workflow jobs on the same commit. Do not create another
  trigger commit or tag.
- A rerun may reuse an existing tag only when it points to the exact release
  commit and has a valid signature from the committed public key. Any mismatch
  is a hard failure.
- If one registry is public but another step fails, do not overwrite or delete
  the public artifact. Re-run the immutable commit only when the remaining work
  is retry-safe and requires no code change.
- If any code change is required after tagging, never move or recreate the tag.
  Preserve the failed draft and prepare the next patch version.
- After any v1.0.0 artifact becomes public, a code change burns that version:
  keep all v1.0.0 history intact and release v1.0.1.
- If verification cannot establish artifact, signature, attestation, registry,
  and tag identity, keep the GitHub Release in draft state and record the exact
  failing target on the release issue.
