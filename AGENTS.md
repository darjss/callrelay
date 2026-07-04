# callrelay

Surface incoming call notifications from your Android phone on your Linux desktop — even when an app is fullscreen.

See [README.md](./README.md) for the full project overview.

## Agent skills

### Issue tracker

Issues live on GitHub at [darjss/callrelay](https://github.com/darjss/callrelay). External PRs are not a triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`) configured on the GitHub repo. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context repo. `CONTEXT.md` at the root holds the domain glossary; `docs/adr/` holds architecture decision records. See `docs/agents/domain.md`.

## Coding guidelines

When writing, reviewing, or refactoring code in this repo, follow the [Karpathy guidelines](https://x.com/karpathy/status/2015883857489522876):

1. **Think before coding** — state assumptions explicitly, surface tradeoffs, ask when unclear.
2. **Simplicity first** — minimum code that solves the problem. No speculative abstractions, no unrequested flexibility.
3. **Surgical changes** — touch only what you must. Match existing style. Don't refactor things that aren't broken.
4. **Goal-driven execution** — define verifiable success criteria. Loop until verified.

Every changed line should trace directly to the request. If a senior engineer would say it's overcomplicated, simplify.
