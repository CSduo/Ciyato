# Which document to believe

Thirteen documents used to sit in the repository root, describing three different
directory layouts, two superseded specifications and one repository that is no longer the
live one. Nothing marked which was current. A contributor — or a coding agent — reading
`CLAUDE_HANDOFF.md` would have worked in the wrong repo, against the wrong spec, to finish
features that were already deleted on purpose (F-064, F-208).

So: **if a document is not listed under Current below, it is history.** History is useful
for intent and useless as a backlog.

---

## Current

These describe the code as it is. If one of them disagrees with the code, that is a bug in
the document.

| Document | What it is for |
|---|---|
| [`README.md`](../README.md) | What Ciyato is, and how to build it |
| [`docs/FEATURE_MATRIX.md`](FEATURE_MATRIX.md) | **Every user-facing feature: owner route, maturity, permissions, tests, source.** The answer to "is this shipped, or just compiled?" |
| [`CLAUDE_ARCHITECTURE_MAP.md`](../CLAUDE_ARCHITECTURE_MAP.md) | Who owns which surface, and which of the two activities it belongs to |
| [`DATA_INVENTORY.md`](../DATA_INVENTORY.md) | Every piece of data the app touches and where it goes. The Data Safety form is built from this |
| [`PLAY_POSITIONING.md`](../PLAY_POSITIONING.md) | Core purpose, and the reasoning behind each restricted permission |
| [`STORE_READINESS.md`](../STORE_READINESS.md) | Permission-by-permission release checklist |
| [`PLAY_RELEASE_EVIDENCE.md`](../PLAY_RELEASE_EVIDENCE.md) | What has been proven for release, and how |
| [`CLAUDE_VALIDATION.md`](../CLAUDE_VALIDATION.md) | What is verified, what is verified only by unit test, and what still needs a device |
| [`CLAUDE_IMPLEMENTATION_LEDGER.md`](../CLAUDE_IMPLEMENTATION_LEDGER.md) | Every Revision III finding that has been implemented, and what the defect actually was |
| [`CLAUDE_REMOVAL_SALVAGE_LEDGER.md`](../CLAUDE_REMOVAL_SALVAGE_LEDGER.md) | What was deleted, what was salvaged, and the reachability proof for each |
| [`SECURITY.md`](../SECURITY.md) | Threat model and the boundaries Ciyato does and does not enforce |
| [`TESTING.md`](../TESTING.md) | How to run the suite |

**The one current specification** is the Revision III audit — 210 findings, F-001 to F-210
— held outside the repository by the project owner. Nothing in `docs/archive/` supersedes
it, and it supersedes everything in there.

---

## Historical — kept for intent, not authority

Every file in [`docs/archive/`](archive/) is superseded. They are kept because they record
*why* decisions were made, which is genuinely valuable and completely different from
recording what to do next.

### [`archive/2026-07/`](archive/2026-07/)

| Document | Date | Why it is history |
|---|---|---|
| `PROJECT_AUDIT.md` | 2026-07-06 | Audits a directory layout that no longer exists, against a contract that has been replaced twice |
| `EMERGENCY_RECOVERY_AUDIT.md` | 2026-07-08 | The recovery it describes completed |
| `IMPLEMENTATION_PLAN.md` | 2026-07 | The plan for that recovery |
| `GRID_LAUNCHER_PLAN.md` | 2026-07 | The positioned-grid and universal-drag rework, which shipped. The canvas model it proposed is now `WorkspaceStore` |
| `CIYATO_REMAINING_WORK_MANIFEST.md` | 2026-07-23 | Derived from the V2 specification, which Revision III supersedes |
| `V2_IMPLEMENTATION_AUDIT.md` | 2026-07 | Audits against the V2 specification |
| `V2_TRACEABILITY.md` | 2026-07 | Traceability register for the V2 specification |
| `SUGGESTIONS.md` | 2026-07 | **The source of every "Suggestion #N" comment in the code.** See below — this is the one most likely to be mistaken for a backlog |
| `ciyato_2000_requirements.md` | 2026-07 | 2000 generated requirements. An idea document, never a contract |
| `ciyato_grand_ux_plan.md` | 2026-07 | 500 generated UX suggestions. Same |
| `task.md`, `walkthrough.md` | 2026-07 | Notes from one phase of work |

### [`archive/2026-08/`](archive/2026-08/)

| Document | Why it is history |
|---|---|
| `CLAUDE_HANDOFF.md` | Hands the project to another agent, pointing at `StudioProjects\-nv-k-ik-\ciyato-android` and a `xiyatosaanvi-creator` remote. **Neither is the live repository.** It also names the V2 PDF as the only active product source. Following it today would mean editing a copy of the code nobody ships |

---

## About "Suggestion #N"

Dozens of files carry comments like `// PermissionAuditScreen — Suggestion #139`. Those
numbers point at `archive/2026-07/SUGGESTIONS.md`, a list of 150 ideas generated in one
sitting.

**A suggestion number is not a requirement, and it never was.** It records where an idea
came from. Several features were restored at some point purely because a working
implementation existed and was "only" unwired — which is how Settings became a catalogue of
things nobody decided to ship.

The rule, from F-208: a feature is reachable because `FEATURE_MATRIX.md` says it earns its
place, not because a suggestion exists. When code with a suggestion comment is touched, the
comment should be replaced with the reason the code exists *now*.

---

## Adding a document

Put it in Current and link it here, or do not add it. A document that is neither current nor
archived is the state this directory exists to prevent.
