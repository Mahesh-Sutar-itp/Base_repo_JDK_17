# Image Registry — Spec Suite Plan

Domain slug: `image` · 18 files · covers 13 source files (691 LOC) at 100%

**Standard this suite is written to:** a developer must be able to reimplement this domain
in any language or framework without reading the original source.

---

## 1. Document inventory

| File | What it provides | Read it when |
|---|---|---|
| `00-domain-index.md` | Coverage table, entity list, five boundary warnings, spec inventory. | First, always. |
| `01-image-entity-and-transport.md` | Entity and transport model: constructors, generated members, equality semantics, quirks. | Implementing the data types. |
| `02-image-services.md` | Repository, converter, CRUD service, patch service, predicate service, sort helper and the write-capable controller — method by method, branch by branch. | Implementing behaviour. **The core document.** |
| `specs/01-data-model.md` | Columns, verbatim types, the three-way uniqueness problem, ORM/DDL divergences. | Writing the schema. |
| `specs/03-business-rules.md` | 24 numbered testable assertions. | Writing tests; settling "is this intended?". |
| `specs/04-user-stories.md` | 9 stories with GIVEN/WHEN/THEN across happy, alternate and error paths. | Acceptance testing; scoping work. |
| `specs/05-api-contract.md` | All five endpoints in full, selector semantics, the service surface, extraction gaps. | Building the API. |
| `specs/06-integration-points.md` | External integrations (none, with evidence); the inbound call census and the `product` bypass. | Planning a service split. |
| `diagrams/domain-er-diagram.md` | ER diagram, constraint layout, integrity notes. | Schema review. |
| `diagrams/domain-sequence-diagrams.md` | List, create, patch, the four delete hazards, the cross-domain write. | Understanding control flow quickly. |
| `diagrams/domain-class-diagram.md` | Self-contained class diagram including the cross-domain bypass. | Orienting in the structure. |
| `errors.md` | 11 error conditions with literal messages; deliberate non-errors; consumer-suppressed errors. | Implementing error handling. |
| `permissions.md` | Four authorities, what each grants, and the uncovered internal write path. | Implementing authorization. |
| `reference/api-endpoints.md` | Endpoint inventory plus the `product` domain's image-writing routes. | Route-level orientation. |
| `reference/background-jobs.md` | None — with the link-checking and orphan consequences. | Confirming nothing async exists. |
| `reference/integrations.md` | None — with the never-dereferenced-URL analysis. | Confirming no external dependency. |
| `reference/feature-flags.md` | None — the two kernel config values; behaviour that is not configurable. | Configuration review. |
| `specs/00-plan.md` | This document. | Orientation. |

---

## 2. Reading order

**For a developer reimplementing the domain**

1. `00-domain-index.md` — scope and the five boundary warnings. **Do not skip W1 and W2**;
   they are defects a faithful reimplementation would otherwise carry forward.
2. `specs/01-data-model.md` — build the table first; copy column types verbatim and keep all
   three unique constraints.
3. `01-image-entity-and-transport.md` — the data types. **Do not skip §1.5** (equality
   semantics): getting it wrong silently disables all patching.
4. `02-image-services.md` — the behaviour. Longest and most important. §6.2 (the predicate)
   and §7.3 (the controller) jointly define the write contract.
5. `specs/05-api-contract.md` — the five endpoints and the selector semantics.
6. `errors.md` and `permissions.md` — literal messages and authority strings.
7. `specs/03-business-rules.md` — verify the implementation against each assertion.

**For QA**

`specs/04-user-stories.md` → `specs/03-business-rules.md` → `errors.md` §3 (the deliberate
non-errors — these are the ones a tester will otherwise file as bugs). US-IMAGE-007 and
US-IMAGE-008 contain the highest-value negative cases.

**For an architect planning a service split**

`00-domain-index.md` §2 (boundary warnings) → `specs/06-integration-points.md` §2.3 (the
product bypass) → `specs/05-api-contract.md` §8 (what must be addressed) → `permissions.md`
§3 (the uncovered write path).

---

## 3. Coverage

**Covered.** All 13 source files. Every public method, protected method, constructor,
generated member and declared constant. Every conditional branch in the converter, patch
service, predicate service and CRUD override, plus every branch of the five controller
operations and the two shared-kernel guard conditions. Every literal error message,
authority string, selector parameter, sort key and column type, recorded verbatim. Every
known defect and quirk, labelled as such.

**Covered as context, not owned.** Shared-kernel generic bases — including the **two
distinct controller bases**, whose difference explains why this domain has five endpoints
and the structurally identical `person` domain has one (`00-domain-index.md` §3.1) — and the
`product` domain's call sites (`specs/06-integration-points.md` §2.2).

**Not covered — deliberately.** Test files; build configuration; generated query types
(excluded by the generated-code filter — their behaviour is described functionally wherever
the predicate and sort specifications use them).

**Verification performed.** Following the correction found in the `person` domain, this
suite's absence claims were verified by codebase-wide grep rather than inherited from the
Mode A catalog. Confirmed: no consumer calls `create`, `delete`, `update`, `partialUpdate`
or `readOne`; no consumer reaches the repository; both deprecated stubs have **no caller**
(unlike the `person` domain, where the identical patch stub is a live defect); and
`ProductsCrudServiceImpl.makeProductImageRelationships` is the sole internal write path.

---

## 4. Omitted standard specs

| Spec | Omitted because |
|---|---|
| `specs/02-state-machines.md` | The domain has **no state**. `Image` has no status field, no type discriminator, no lifecycle flag, not even a boolean `active`/`deleted` marker, and no state expressed through collection membership. A record is created and thereafter only edited field-by-field or deleted outright. With no states there is nothing to enumerate, no transition to tabulate and no terminal state to identify. |
| `specs/07-query-specs.md` | The threshold is three or more repository methods with multi-table joins, subqueries or dynamic WHERE construction. This domain has **one** custom repository method (`findByFilename` — single-table, exact equality, no join). The dynamic filter construction lives in the predicate service, not the repository, and is already specified exhaustively parameter-by-parameter in `02` §6.2 — including the three-way short-circuit, the missing `url` filter and the unescaped metacharacters. A separate query spec would restate it. |
| `specs/08-config-parameters.md` | The domain has **no configuration entity** and no typed parameterised settings with per-subtype minima, maxima or defaults. The only configuration affecting its behaviour is two application-wide pagination values owned by the shared kernel, recorded in `reference/feature-flags.md` and specified in `02` §7.2. |
| `events.md` | The domain **publishes and consumes no events**. No domain event, message, notification, webhook or outbox exists. Significant for an extraction: there is no event stream for consumers to subscribe to in place of in-process calls, and no signal when an image is created by the `product` domain's cascade. See `cross-cutting-documentation/event-publishing-patterns.md`. |

Each omission is an **absence verified against source**, not an untested assumption.

---

## 5. Gaps and open questions for a rewrite

Decisions the specification cannot make on the implementer's behalf.

| # | Decision | Reference |
|---|---|---|
| 1 | **Resolve the uniqueness incoherence.** Widen `getExisting` to check all three unique fields (turning 500s into 409s), or drop two constraints. The present combination guarantees 500s under normal use. | BR-IMAGE-002 (W1) |
| 2 | **Make `DELETE` safe.** Require at least one *recognised* selector, and bound the batch. Today a mistyped parameter deletes the registry. | BR-IMAGE-013 (W2) |
| 3 | **Define the contract for the `product` bypass.** Should product-driven image creation go through the service API? If so, what happens to the currently silently-dropped blank-filename image — fail the product, or report a partial success? | BR-IMAGE-024 (W3) |
| 4 | **Decide who may create images.** `images:create` is not checked on the product path. Propagate end-user identity, or accept that product permissions imply image creation. | `permissions.md` §3 |
| 5 | **Replace query-parameter write addressing** with a path identifier or a filename-keyed route. The current predicate short-circuits non-deterministically when two identity selectors are supplied. | BR-IMAGE-021, BR-IMAGE-008 |
| 6 | **Add a `url` selector?** `url` is unique and sortable but unfilterable, so an image cannot be found, updated or deleted by URL. Adding it widens the addressable surface for writes as well as reads. | BR-IMAGE-019 |
| 7 | **Should URLs be validated or host-allow-listed?** They are served to clients and rendered, with no checks today. A service boundary is the natural enforcement point. | BR-IMAGE-005 (W5) |
| 8 | **Should the domain own binary content at all,** or remain formally metadata-only? There is no upload path, no processing and no stored checksum or MIME type. | `reference/integrations.md` |
| 9 | **Should `POST` return the created resource and its identifier?** Today it returns 201 with an empty body and the model has no identifier, so callers cannot confirm what they created. | BR-IMAGE-012 |
| 10 | **Should `images:update` be split** so that destructive replacement requires a different authority from guarded patching? | `permissions.md` §1.2 |
| 11 | **Should an unknown `sortBy` be a 400 or ignored,** instead of the current 500? Shared-kernel behaviour, so the decision affects every domain. | BR-IMAGE-018 |
| 12 | **Should a link-checking job exist?** Nothing ever verifies a stored URL. Introducing one is new behaviour and needs a defined outcome for a broken link. | `reference/background-jobs.md` |

---

## 6. Completeness statement

This suite is **complete**. It is not truncated, and no artifact is missing or thin:

- 13/13 source files have behavioural coverage in `01` and `02`.
- `specs/01-data-model.md`, `specs/03-business-rules.md` and `specs/04-user-stories.md` are
  present and populated.
- `specs/05-api-contract.md` and `specs/06-integration-points.md` are present and populated;
  `02`, `07` and `08` are omitted with the reasons in §4.
- `errors.md`, `permissions.md` and all four `reference/` files carry domain-specific
  content; none is a template or a bare pointer.
- `events.md` is omitted rather than emitted as an empty file, per §4.
- All three diagram files are self-contained Mermaid documents.
