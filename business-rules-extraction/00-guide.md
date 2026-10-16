# How To Use This Analysis

A reading guide to `business-rules-extraction/` — what each artefact is for, which
ones to trust as authoritative, and how to use them by role.

## What this analysis is

A **rewrite-ready documentation set** for the Trébol backend monolith, produced
by **static analysis only**. Nothing was built, compiled, executed or modified;
every statement is grounded in a file that was read.

It answers four questions:

1. **What does the system do?** — 139 features and 327 business rules across 12
   domains, each cited to its implementing file.
2. **Where are the boundaries?** — 12 bounded contexts owning all 22 tables with
   no overlap and no orphans.
3. **What will break if we split it?** — 12 cross-boundary foreign keys, 7
   repository-level or cascade couplings, and 2 database rules that cannot
   survive a split.
4. **What looks present but is not?** — stock control, shipping cost, invoicing,
   events, caching, scheduled work, password change and delivery tracking are all
   modelled-but-absent, and are documented as such.

## Directory map

```
business-rules-extraction/
├── 00-master-index.md              start here for metrics and the domain table
├── 00-guide.md                     this file
├── analysis-dashboard.html         interactive summary, 5 tabs, open in a browser
│
├── database-documentation/
│   ├── 00-global-schema.sql        22 tables, transcribed VERBATIM + divergence notes
│   └── 00-seed-data.json           every seeded lookup row, with its business meaning
│
├── detailed-domain-bre/            BEHAVIOURAL half
│   ├── README.md                   conventions and how to read a domain
│   ├── cross-domain-features.md    10 features that span domains
│   ├── requirements-summary.md     consolidated counts, absences and defects
│   ├── traceability-matrix.md      rule and feature IDs mapped to source files
│   └── domain-{slug}/              x12
│       ├── overview.md             responsibility, owned data, components
│       ├── features.md             F-NNN
│       ├── business-rules.md       BR-{DOMAIN}-NNN, testable, cited
│       ├── workflows.md            ordered steps + the state model
│       ├── validations.md          validation layers and the gaps
│       ├── integrations.md         coupling, events, jobs, caching
│       └── diagrams/               sequence, component, workflow (Mermaid)
│
├── domain-decomposition-overview/  STRUCTURAL half
│   ├── README.md                   the 12 domains and the key structural findings
│   ├── domain-manifest.json        the machine-readable contract
│   ├── bounded-contexts.md         the boundary rule + evidence per boundary
│   ├── data-ownership.md           table to domain, conflicts, cross-boundary keys
│   ├── shared-kernel.md            components used by more than one domain
│   ├── anti-corruption-layers.md   existing translation + contracts a split needs
│   ├── execution-order.md          the recommended sequence
│   └── service-{slug}.md           x12 proposed service boundaries
│
├── cross-cutting-documentation/
│   ├── authentication-authorization.md
│   ├── error-handling-patterns.md
│   ├── event-publishing-patterns.md      (documents an ABSENCE)
│   ├── caching-patterns.md               (documents an ABSENCE)
│   ├── shared-entities.md                canonical spec for the 9 shared entities
│   ├── common-error-codes.md             every error literal in the codebase
│   ├── common-permissions.md             authority catalogue + enforcement matrix
│   └── common-events.md                  (documents an ABSENCE)
│
├── reference-documentation/
│   ├── api-endpoint-catalog.md           67 routes + 2 filter routes
│   ├── service-component-inventory.md    106 components, interface to implementation
│   ├── external-integrations-map.md      the 2 outbound integrations
│   ├── cron-daemon-inventory.md          (documents an ABSENCE)
│   ├── feature-flag-inventory.md         4 booleans + 1 profile
│   ├── coupling-hotspots.md              10 hotspots, prioritised
│   └── dead-code-decomposition-notes.md  12 items that mislead a decomposition
│
└── diagrams/
    ├── architecture-diagrams.md          layers, domain map, request paths
    ├── sequence-diagrams.md              6 cross-domain flows
    └── class-er-diagrams.md              full entity-relationship + class structure
```

## Authority of each artefact

When two documents appear to disagree, prefer the one higher in this list.

| Rank | Artefact | Why |
|---|---|---|
| 1 | `database-documentation/00-global-schema.sql` | transcribed verbatim from the source dump; column types, constraints and cascades are reproduced exactly, with divergences flagged as `TODO` rather than corrected |
| 2 | `domain-decomposition-overview/domain-manifest.json` | mechanically validated against its schema; the only machine-readable artefact |
| 3 | `reference-documentation/api-endpoint-catalog.md` and `cross-cutting-documentation/common-permissions.md` | generated directly from controller annotations and seed data, not written by hand |
| 4 | each `detailed-domain-bre/domain-*/business-rules.md` | every rule cites the file that enforces it and quotes real literals |
| 5 | everything else | synthesis and analysis built on the above |

**Do not** treat the following as specifications, even though they look like
them — all three are stale or inert and are documented in
`reference-documentation/dead-code-decomposition-notes.md`:

- `src/main/resources/META-INF/persistence.xml` (names a non-existent entity,
  omits seven real ones);
- the `trebol.cors.mappings` property value (reads like a route inventory; the
  builder that consumes it is wired into nothing);
- the four `order_types:*` authority codes (granted, referenced by no code).

## Reading paths by role

### If you are planning the modernisation

1. `00-master-index.md` — the metrics and the domain table.
2. `domain-decomposition-overview/README.md` — the structural findings.
3. `domain-decomposition-overview/execution-order.md` — the sequence and the
   prerequisite refactors.
4. `reference-documentation/coupling-hotspots.md` — the ten hotspots in
   resolution order. **This is the actual work list.**
5. `detailed-domain-bre/requirements-summary.md` — the absences and the defect
   inventory, so that scope decisions are explicit.

### If you are rewriting one domain

1. `domain-decomposition-overview/service-{slug}.md` — the boundary, the owned
   data, the contracts and the risks.
2. `detailed-domain-bre/domain-{slug}/overview.md` then `features.md` then
   `business-rules.md` — in that order.
3. `detailed-domain-bre/domain-{slug}/workflows.md` and `validations.md` — the
   ordered steps and the validation layers.
4. `detailed-domain-bre/domain-{slug}/diagrams/` — the same information visually.
5. `database-documentation/00-global-schema.sql` — the exact DDL for your tables.
6. `cross-cutting-documentation/shared-entities.md` — if your domain consumes a
   shared entity, your obligations are listed there.

Treat the domain's rule set as the **regression suite**: each rule is phrased as
a testable assertion precisely so it can be checked against a reimplementation.

### If you are reviewing security

1. `cross-cutting-documentation/authentication-authorization.md` — including the
   fourteen-row security findings table.
2. `cross-cutting-documentation/common-permissions.md` — the enforcement matrix,
   and the fourteen granted-but-never-enforced codes.
3. `reference-documentation/api-endpoint-catalog.md` — the twelve publicly
   reachable routes and why each is public.
4. `detailed-domain-bre/domain-order/business-rules.md` — the payment callback
   rules (`BR-ORDER-040` onward).
5. `reference-documentation/feature-flag-inventory.md` — the two high-blast-radius
   toggles.

### If you are reviewing the data model

1. `database-documentation/00-global-schema.sql` — read the divergence notes at
   the end first.
2. `diagrams/class-er-diagrams.md` — the full entity-relationship diagram.
3. `domain-decomposition-overview/data-ownership.md` — ownership, the five
   resolved conflicts, and the twelve cross-boundary keys.
4. `database-documentation/00-seed-data.json` — the signed order-status codes and
   the role grants.

### If you want a five-minute overview

Open `analysis-dashboard.html` in a browser. Five tabs: Overview, Business Logic
(with a full drill-down to every rule), Domain Decomposition (with a dependency
graph), Shared Kernel & Cross-Domain, and Data Ownership & Schema. Every number
in it is computed at runtime from the manifest.

## Conventions used throughout

| Convention | Meaning |
|---|---|
| `F-NNN` | a feature, numbered within its domain |
| `BR-{DOMAIN}-NNN` | a business rule, numbered within its domain |
| `W-{DOMAIN}-NN` | a workflow |
| `CDF-NN` | a cross-domain feature |
| `HS-NN` | a coupling hotspot |
| `DC-NN` | a dead-code item |
| **absence stated explicitly** | if a capability does not exist, it is written down as a finding rather than omitted |
| **defects recorded, not corrected** | where the code is wrong, the documents state what it does today and flag it |
| **"wired but unreachable"** | a fully implemented service with no route exposing it — capability, not dead code |

Domain tokens in rule identifiers are abbreviated where the slug is long:
`PRODCAT` for `product-category`, `PRODLIST` for `product-list`, `COMPANY` for
`company-profile`.

## Five things worth knowing before you start

1. **Most of the code is plumbing.** Of 17,800 lines, the genuine business logic
   is concentrated in four places: the order status machine, the order monetary
   arithmetic, the order edit lock, and the authority resolution and token chain.
   Everything else follows one repeated generic-CRUD shape.
2. **The integer truncation order in the tax arithmetic is load-bearing.** A
   rewrite that accumulates in decimal and rounds at the end will produce
   different totals (`BR-ORDER-015`).
3. **Status transitions compare status *names*.** Renaming a seeded status row
   breaks the machine at runtime (`BR-ORDER-020`).
4. **A success response does not mean the request was fully applied.** Silent
   tolerance is the dominant error-handling pattern — dropped filters, skipped
   images, skipped list members (`error-handling-patterns.md`).
5. **Authority codes are unchecked string literals** in every domain. A rename
   silently disables an access check (`BR-USER-031`).

## What is deliberately not here

- **No Mode B output.** This run produced the decomposition only. Per-domain
  implementation specifications live in `detailed-business-specification/` and are
  generated separately, one invocation per domain slug, keyed off
  `domain-manifest.json`.
- **No build, test or runtime observation.** Everything is derived from reading
  source, resources and the build manifest.
- **No effort or duration estimates.** Sequencing and dependencies are given;
  sizing is not.
