# Contributing

All changes in this repository are expected to be issue-linked.
Release preparation follows the same rule: open a dedicated issue first, land
the version and documentation updates through a green PR, and tag only after
the merged `main` commit is green.

## Workflow

1. Create or confirm the GitHub issue first.
2. Create a descriptive issue-linked branch such as `<issue-number>-<slug>`, `feature/<issue-number>-<slug>`, or another clear issue-linked variant.
3. Reference the issue number in every commit message.
4. Open a PR that closes the issue and keeps the change scoped to that issue.
5. Keep the PR green, reply to review comments, and resolve threads only after the fix or an explicit invalidation response.
6. Merge only after the latest review is newer than the latest push and all required checks are green.

Maintainers preparing a tag or recovering a publication must also follow
[RELEASING.md](RELEASING.md).
