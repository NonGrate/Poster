# Working with an AI assistant

The repository carries its own instructions for coding assistants, so a
developer can open it in their tool of choice and say "make this my app" or
"add a field to posts" and get the same procedure every time.

| Tool | Reads | Notes |
|---|---|---|
| Claude Code | `CLAUDE.md`, `.claude/commands/*.md` | `/adapt-template`, `/add-feature-flag`, `/add-post-field`, `/toggle-feature`, `/quality-gates` |
| OpenAI Codex, Jules, others reading `AGENTS.md` | `AGENTS.md` → `CLAUDE.md` | a pointer, so there is one source |
| Cursor | `.cursor/rules/poster.mdc` | always-on rule that says to read `CLAUDE.md` |
| GitHub Copilot | `.github/copilot-instructions.md` | same |
| A person | `docs/AdaptationChecklist.md` | the checklist the playbook executes |

`CLAUDE.md` has two parts: how to work here day to day (the rules the
architecture script enforces, where things go, what looks like a bug and is
not, what never to do) and the **adaptation playbook** (Phase 0 interview →
Phase 8 quality gates), each phase ending in a green build and a commit.

## Typical requests

- "Turn this into <name>, package <id>, scheme <scheme>, domain <host>" →
  `/adapt-template` runs all phases and reports what needs the developer's
  own accounts.
- "Turn off comments and images" → `/toggle-feature comments images off`:
  edits `poster.properties`, rebuilds, runs the suite (the suite is
  flag-aware: every flag-dependent test skips itself when its flag is off).
- "Add a `location` field to posts" → `/add-post-field location String
  optional`: schema + migration, model, mappers, validation, form, card,
  strings, tests, in one commit.
- "Add a feature flag for X" → `/add-feature-flag x`: one entry in the
  catalogue (`buildSrc/PosterFeatures.kt`), the property line, the gate at the
  UI entry point and around the routes; `docs/Features.md` regenerates itself.

## What the assistant must not do

Commit secrets or personal data; add dependencies for what a few lines cover;
rename the `POSTER_*` environment variables; edit an existing `.sqm` or
`databases/*.db`; reformat files it was not asked to touch. It is written
down in `CLAUDE.md` so the tool sees it every session.
