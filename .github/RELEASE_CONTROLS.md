# Release controls

The release workflow accepts a signed stable-version tag only when its commit is reachable from protected
`main`, or from a protected `release/hotfix/**` branch that contains the preceding stable release tag. The tag
must be annotated and signed by fingerprint `E6BB1FB6EE83EEAB7B408C6B5CA409BD8EE61724`, whose public key is
committed at `.github/release-signing-key.asc`.

Source, signature, fingerprint, and version alignment are validated before the protected `release` environment
can expose publishing credentials. Only the environment-gated `publish` job may reference those credentials.

## Accepted residual risk

The Maven Central, Gradle Plugin Portal, and PGP secrets remain repository-scoped by owner decision. This is an
accepted deviation from environment-scoped secret storage. Their use is restricted to the protected release job;
workflow commands must never echo secret values, and derived authorization values must be masked before use.

## Isolated hotfixes

Create an isolated hotfix from the latest stable tag on a protected `release/hotfix/**` branch. After its release
is verified, merge the hotfix branch back into `main`. A stale branch that does not contain the preceding stable
tag is not eligible to publish.
