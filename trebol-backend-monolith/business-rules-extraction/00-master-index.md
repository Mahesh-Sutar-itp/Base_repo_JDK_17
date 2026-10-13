# Master Index — Trébol Backend Monolith

Business-rules extraction and domain decomposition, produced by **static
analysis only**: no build, no compilation, no execution, no source modification.

New here? Read `00-guide.md`, or open `analysis-dashboard.html` in a browser.

## Subject system

| Property | Value |
|---|---|
| Project | `trebol-backend-monolith` |
| Artifact | `org.trebolecommerce:trebol-backend-monolith-java17:0.2.5-SNAPSHOT` |
| Packaging | war |
| Framework | Spring Boot 3.2.12 |
| Persistence | JPA / Hibernate with QueryDSL predicates |
| Declared language level | Java 21 (despite the repository folder name) |
| Database | H2 in-memory by default; MariaDB dump is the authoritative schema |
| Primary languages | Java, SQL |
| Enrichment inputs | none — no `ATXDocumentation/` and no `/additionalInput/` present |

## Summary metrics

| Metric | Value |
|---|---|
| Main source files | 294 |
| Main lines of code | 17,800 |
| Domains identified | 12 |
| Shared-kernel files / lines | 36 / 2,085 (12% of the codebase) |
| Database tables | 22 |
| ORM entity classes | 22 |
| Shared entities | 9 |
| Controllers | 22 |
| HTTP routes | 67 controller routes + 2 filter-implemented routes |
| Publicly reachable routes | 12 |
| Repositories | 23 |
| Transport models | 25 |
| Features catalogued | 139 |
| Business rules documented | 327 |
| Cross-domain features | 10 |
| Coupling hotspots | 10 |
| Seeded authority codes | 54 (14 granted but never enforced) |
| Seeded roles | 4 |
| Seeded order statuses | 12 (4 never reached by any code path) |
| External integrations | 2 |
| Domain events | 0 |
| Scheduled jobs | 0 |
| Cache regions | 0 |
| Test classes | 108 |

Every count above is verifiable against the filesystem; the commands are listed
in `detailed-domain-bre/requirements-summary.md`.

## The twelve domains

Listed in the recommended execution order.

| # | Domain | Slug | Cx | Tables | Files | LOC | Features | Rules | Depends on |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Person Registry | `person` | small | 1 | 14 | 755 | 7 | 13 | — |
| 2 | Image Registry | `image` | small | 1 | 13 | 691 | 8 | 15 | — |
| 3 | Product Category Tree | `product-category` | medium | 1 | 14 | 903 | 14 | 20 | — |
| 4 | Billing | `billing` | small | 2 | 24 | 1,095 | 10 | 19 | — |
| 5 | Shipper | `shipper` | small | 1 | 13 | 648 | 9 | 15 | — |
| 6 | Company Profile | `company-profile` | small | 1 | 5 | 249 | 5 | 13 | — |
| 7 | Identity & Access | `user` | large | 4 | 56 | 3,404 | 15 | 55 | `person`, `customer` |
| 8 | Customer | `customer` | small | 1 | 12 | 657 | 11 | 22 | `person` |
| 9 | Salesperson | `salesperson` | small | 1 | 12 | 664 | 8 | 23 | `person` |
| 10 | Product Catalog | `product` | medium | 2 | 16 | 1,120 | 13 | 32 | `image`, `product-category` |
| 11 | Product List | `product-list` | medium | 2 | 21 | 1,286 | 16 | 30 | `product` |
| 12 | Order, Checkout & Fulfilment | `order` | large | 5 | 58 | 4,243 | 23 | 70 | `customer`, `product`, `billing`, `shipper`, `salesperson` |
| | **totals** | | | **22** | **258** | **15,715** | **139** | **327** | |

The 36 remaining files (2,085 lines) are shared kernel. 258 + 36 = 294.

Per-domain detail: `detailed-domain-bre/domain-{slug}/` (behaviour) and
`domain-decomposition-overview/service-{slug}.md` (structure).

## Key findings

1. **Only two domains orchestrate.** `order` and `user` coordinate others; the
   remaining ten only participate. That is why they extract last — and why
   `order`, being a pure consumer that nothing reads, carries no risk to other
   domains once its contracts exist.
2. **Four of `order`'s five domain dependencies are repository-level.** That one
   converter class is the largest body of extraction work in the system
   (`reference-documentation/coupling-hotspots.md` HS-01).
3. **The security kernel depends outward on business domains.** Registration and
   guest authentication write `person` and `customer` rows — the reverse of the
   usual direction (HS-02).
4. **Five cross-domain edges are already clean service calls** and survive a split
   unchanged. `product` → `product-category` is the model to copy.
5. **All 22 tables have exactly one owner.** No orphans, no shared-kernel
   infrastructure tables (`domain-decomposition-overview/data-ownership.md`).
6. **Stock is stored and never read.** An out-of-stock product can be ordered,
   paid for and completed with no warning (`BR-PRODUCT-022`, `BR-ORDER-012`).
7. **The order status machine is bypassable.** A `status` key on a patch moves an
   order to any status with no transition guard, and full replacement escapes the
   edit lock entirely (`BR-ORDER-028`, `BR-ORDER-031`).
8. **Both payment callbacks are public and unauthenticated**, with no signature,
   origin or idempotency check; replay protection is incidental
   (`BR-ORDER-040`).
9. **No events, no caching, no scheduled work exist anywhere** — so any
   split-related eventual consistency has no infrastructure to build on.
10. **Silent tolerance is the dominant error-handling pattern.** A success
    response does not mean the request was fully applied
    (`cross-cutting-documentation/error-handling-patterns.md`).

## Database documentation

| Artefact | Contents |
|---|---|
| `database-documentation/00-global-schema.sql` | all 22 tables transcribed **verbatim** from the MariaDB dump — exact column types, lengths, collations, unique keys, indexes, cascades — plus 11 flagged DDL/ORM divergences |
| `database-documentation/00-seed-data.json` | every seeded lookup row with its business meaning: 4 company parameters, 12 order statuses with signed codes, 2 billing types, 1 payment type, 4 roles, 54 authority codes, the role grants (54/31/14/1) and 4 bootstrap accounts |

## Decomposition overview

| Artefact | Contents |
|---|---|
| `domain-decomposition-overview/README.md` | the 12 domains and the structural findings |
| `domain-decomposition-overview/domain-manifest.json` | the machine-readable contract — validates against its schema with zero errors |
| `domain-decomposition-overview/bounded-contexts.md` | the boundary rule applied, with evidence for each of the 12 boundaries |
| `domain-decomposition-overview/data-ownership.md` | table ownership, 5 resolved conflicts, 12 cross-boundary foreign keys |
| `domain-decomposition-overview/shared-kernel.md` | 36 components grouped by type, including the dead ones |
| `domain-decomposition-overview/anti-corruption-layers.md` | the existing translation surface and the 6 contracts a split needs |
| `domain-decomposition-overview/execution-order.md` | the sequence, the three tiers and the prerequisite refactors |
| `domain-decomposition-overview/service-{slug}.md` | x12 — boundary, owned data, contracts, risks, readiness |

## Cross-cutting documentation

| Artefact | Headline |
|---|---|
| `authentication-authorization.md` | stateless tokens, method-level enforcement only, 14 security findings |
| `error-handling-patterns.md` | 4 mapped conditions; everything else is a 500; silent tolerance is the norm |
| `event-publishing-patterns.md` | **ABSENT** — and the 2 database cascades that would need events to replace them |
| `caching-patterns.md` | **ABSENT** — and the token-as-cache consequence for revoked grants |
| `shared-entities.md` | canonical specification for the 9 shared entities and each consumer's obligations |
| `common-error-codes.md` | every error literal in the codebase, grouped by envelope code |
| `common-permissions.md` | the 54-code catalogue with grants and per-route enforcement |
| `common-events.md` | **ABSENT** — recorded explicitly |

## Reference documentation

| Artefact | Headline |
|---|---|
| `api-endpoint-catalog.md` | 67 controller routes + 2 filter routes, with access level and status code |
| `service-component-inventory.md` | 106 components as name / interface / implementation / package / domain |
| `external-integrations-map.md` | Transbank Webpay Plus and Mailgun — both synchronous, neither with retry |
| `cron-daemon-inventory.md` | **ABSENT** — plus the 9 jobs the code implies are missing |
| `feature-flag-inventory.md` | 4 boolean toggles and 1 profile; 2 are high-blast-radius |
| `coupling-hotspots.md` | 10 hotspots with a recommended resolution order |
| `dead-code-decomposition-notes.md` | 12 items that would mislead a decomposition if trusted |

## Diagrams

| Artefact | Contents |
|---|---|
| `diagrams/architecture-diagrams.md` | layered architecture, domain map with coupling styles, request paths by access level, package-to-domain mapping, the repeated CRUD pattern |
| `diagrams/sequence-diagrams.md` | 6 cross-domain flows including the full purchase journey and the order converter's four-domain reach |
| `diagrams/class-er-diagrams.md` | the full entity-relationship diagram with column annotations, ownership overlay, entity and service class hierarchies, the order aggregate, the security chain |
| per-domain | `detailed-domain-bre/domain-{slug}/diagrams/` — sequence, component and workflow diagrams for each of the 12 domains (36 files) |

All diagrams are Mermaid. The dashboard graph uses the Canvas API; no external
charting library is used anywhere.

## Interactive dashboard

`analysis-dashboard.html` — self-contained, no external resources. Five tabs:

| Tab | Contents |
|---|---|
| Overview | complexity distribution, shared-kernel intensity, top domains by features and by lines of code, execution order, three domain-health statements |
| Business Logic | domain cards plus a full drill-down from domain to feature to rule to source file, cross-domain features, integration map |
| Domain Decomposition | canvas dependency graph, service cards, outbound dependency counts, execution order detail, anti-corruption layers |
| Shared Kernel & Cross-Domain | shared entities, cross-cutting concern cards, error codes, permission and event summary, kernel component table |
| Data Ownership & Schema | database statistics, ownership matrix, schema partition viewer, migration considerations |

Every displayed number is computed at runtime from the embedded manifest.

## Validation

All mechanical gates were **executed**, with real output recorded in
`validation_summary.md`. Headlines:

| Check | Result |
|---|---|
| manifest is valid JSON | PASS |
| manifest validates against its schema (`jsonschema`, Draft-07) | PASS — 0 errors |
| every table maps to exactly one domain | PASS — 22 of 22, no duplicates, no orphans |
| multi-domain entities declared shared | PASS — 0 missing by name, 0 missing by table |
| execution order covers every slug exactly once | PASS — 12 of 12 |
| dashboard is self-contained with no hardcoded numbers | PASS — 0 external references, 0 numeric literals |
| per-domain diagrams present | PASS — 12 domains x 3 diagrams |
| no stub files, no empty directories | PASS |

## Next step

This run produced the **decomposition**. The per-domain implementation
specifications are generated separately — one invocation per domain slug from
`domain-manifest.json`, in the order given by `execution_order`, writing into
`detailed-business-specification/domain-{slug}/`.
