# Branch protection configuration

## Required status check

Configure **exactly one** required status context on protected branches:

- **`required`** — the aggregate job.

This single context is produced under the same name on both PR origins, so one
branch-protection rule covers both.

## How the two origins produce it

### Fork PRs (privileged path)
Fork PRs run through the privileged entrypoint `external-pr-ci.yml`
(`pull_request_target`, gated on the `safe-to-test` label and a verified fork
head). The entrypoint calls each in-scope workflow as a reusable
`workflow_call`, so their job contexts are **prefixed by the caller job name**:

- `ci / jvm (17)`, `ci / linux`, `ci / all-platforms`, `ci / protocol-tests`,
  `ci / downstream`, ...
- `changelog / changelog-verification`
- `required` (the entrypoint's aggregate job)

Note: `macos` and `windows` use no credentials/kat and already pass on forks.
They are guarded with `if: ${{ github.event_name != 'workflow_call' }}` so they
run **only** on the unprivileged (same-repo) path and are skipped when
`continuous-integration.yml` is invoked via `workflow_call` from the entrypoint.
This avoids duplicate runs.

### Same-repo PRs (unprivileged path)
Same-repo PRs run the in-scope workflows directly via their own `pull_request` /
`merge_group` triggers, producing **unprefixed** contexts:

- `jvm (17)`, `macos (macos-15-large)`, `windows`, `linux`, `all-platforms`,
  `protocol-tests`, `downstream`
- `changelog-verification`
- `required` — produced by `ci-required.yml`, under the **same name** as the
  entrypoint's aggregate.

## Why only the aggregate is marked required

A status context that can be produced by only one origin must **not** be marked
required: it would never appear on PRs from the other origin and would block
them forever. Because fork contexts are caller-prefixed (`ci / jvm (17)`) while
same-repo contexts are unprefixed (`jvm (17)`), no individual job name is common
to both origins. Only the aggregate `required` job shares one name across both
paths, so it is the single safe required context.

### Limitation of `ci-required.yml`

`ci-required.yml` only reproduces the `required` context name on the same-repo
path — it does **not** itself gate the individual same-repo checks. Those checks
(`jvm`, `linux`, etc.) remain independently required by virtue of being part of
the same-repo PR run; the aggregate does not aggregate their results on the
same-repo path. If you need same-repo individual checks enforced, keep them as
independently required contexts in addition to the aggregate, or convert
`ci-required.yml` into a `needs:`-based aggregation of the same-repo jobs.
