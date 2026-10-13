# Person Registry — Spec Suite Plan

Domain slug: `person` · 18 files · covers 14 source files (755 LOC) at 100%

**Standard this suite is written to:** a developer must be able to reimplement this domain
in any language or framework without reading the original source.

---

## 1. Document inventory

| File | What it provides | Read it when |
|---|---|---|
| `00-domain-index.md` | Coverage table, entity list, boundary warnings, spec inventory. | First, always. |
| `01-person-entity-and-transport.md` | The entity, the transport model and the exception type: every constructor, generated member, equality semantic and quirk. | Implementing the data types. |
| `02-person-services.md` | Repository, converter, CRUD service, patch service, predicate service, sort helper and controller — method by method, branch by branch. | Implementing behaviour. **The core document.** |
| `specs/01-data-model.md` | Columns, verbatim types, indexes, ORM/DDL divergences, shared-entity status. | Writing the schema. |
| `specs/03-business-rules.md` | 28 numbered testable assertions, plus corrections to the Mode A catalog. | Writing tests; settling "is this intended?". |
| `specs/04-user-stories.md` | 9 stories with GIVEN/WHEN/THEN across happy, alternate and error paths. | Acceptance testing; scoping work. |
| `specs/05-api-contract.md` | The one HTTP endpoint in full; the live vs dead service surface; what an extraction must add. | Building the API. |
| `specs/06-integration-points.md` | External integrations (none, with evidence) and the 17-site inbound call census. | Planning a service split. |
| `diagrams/domain-er-diagram.md` | ER diagram, index layout, referential-integrity notes. | Schema review. |
| `diagrams/domain-sequence-diagrams.md` | Read, create, patch and failure-path flows. | Understanding control flow quickly. |
| `diagrams/domain-class-diagram.md` | Self-contained class diagram of domain types and kernel bindings. | Orienting in the structure. |
| `errors.md` | 9 error conditions with literal messages and statuses; the deliberate non-errors. | Implementing error handling. |
| `permissions.md` | `people:read`; the absent write authorities; extraction considerations. | Implementing authorization. |
| `reference/api-endpoints.md` | Endpoint inventory, plus the other domains' person-writing routes. | Route-level orientation. |
| `reference/background-jobs.md` | None — with the orphan-reclamation consequence. | Confirming nothing async exists. |
| `reference/integrations.md` | None — with two notable absences. | Confirming no external dependency. |
| `reference/feature-flags.md` | None — plus the two kernel config values that do affect behaviour. | Configuration review. |
| `specs/00-plan.md` | This document. | Orientation. |

---

## 2. Reading order

**For a developer reimplementing the domain**

1. `00-domain-index.md` — scope and the four boundary warnings.
2. `specs/01-data-model.md` — build the table first; copy column types verbatim.
3. `01-person-entity-and-transport.md` — the data types. **Do not skip §1.5** (equality
   semantics): getting it wrong silently disables all patching.
4. `02-person-services.md` — the behaviour. Longest and most important.
5. `specs/05-api-contract.md` — the endpoint and the service surface.
6. `errors.md` and `permissions.md` — error messages and the authority literal.
7. `specs/03-business-rules.md` — verify the implementation against each assertion.

**For QA**

`specs/04-user-stories.md` → `specs/03-business-rules.md` → `errors.md` §3 (the deliberate
non-errors — these are the ones a tester will otherwise file as bugs).

**For an architect planning a service split**

`00-domain-index.md` §2 (boundary warnings) → `specs/06-integration-points.md` §2 (the call
census) → `specs/05-api-contract.md` §5 (what must be added) → `permissions.md` §5.

---

## 3. Coverage

**Covered.** All 14 source files. Every public method, protected method, constructor,
generated member and declared constant. Every conditional branch in the converter, patch
service, predicate service and CRUD override. Every literal error message, authority
string, filter parameter, sort key and column type, recorded verbatim. Every known defect
and quirk, labelled as such.

**Covered as context, not owned.** Shared-kernel generic bases (behaviour specified where
this domain inherits it, see `00-domain-index.md` §3.1) and consumer-domain call sites
(censused in `specs/06-integration-points.md` §2.2, owned by the `user`, `customer` and
`salesperson` specifications).

**Not covered — deliberately.** Test files; the build configuration; the generated QueryDSL
query types (excluded by the generated-code filter — their behaviour is described
functionally wherever the predicate and sort specifications use them).

**Verified corrections.** Two Mode A claims did not survive call-site verification and are
superseded here: `PersonNotFoundException` *is* thrown (once, from the `user` domain), and
`create` is *not* the creation path (it has no caller). Recorded in
`specs/03-business-rules.md` §Corrections.

---

## 4. Omitted standard specs

| Spec | Omitted because |
|---|---|
| `specs/02-state-machines.md` | The domain has **no state**. `Person` has no status field, no type discriminator, no lifecycle flag, and not even a boolean `active`/`deleted` marker. There is no workflow with sequential steps and no state expressed through collection membership. A person row is created and thereafter only ever edited field-by-field. With no states there is nothing to enumerate, no transition to tabulate and no terminal state to identify. |
| `specs/07-query-specs.md` | The threshold is three or more repository methods with multi-table joins, subqueries or dynamic WHERE construction. This domain has **one** custom repository method (`findByIdNumber` — single-table, exact equality, no join). The dynamic filter construction that does exist lives in the predicate service, not the repository, and is already specified exhaustively parameter-by-parameter in `02` §6.2, including the identity short-circuit and the unescaped-metacharacter flaw. A separate query spec would restate it. |
| `specs/08-config-parameters.md` | The domain has **no configuration entity** and no typed parameterised settings with per-subtype minima, maxima or defaults. The only configuration affecting its behaviour is two application-wide pagination values owned by the shared kernel; they are recorded in `reference/feature-flags.md` §Configuration and their behaviour specified in `02` §7.2. |
| `events.md` | The domain **publishes and consumes no events**. There is no domain event, no message, no notification, no webhook and no outbox. Nothing observes a person being created or changed — which is itself significant for an extraction, since there is no existing event stream for consumers to subscribe to in place of in-process calls. See `cross-cutting-documentation/event-publishing-patterns.md` for the system-wide position. |

Each omission is an **absence verified against source**, not an untested assumption.

---

## 5. Gaps and open questions for a rewrite

Not defects in this specification — decisions the specification cannot make on the
implementer's behalf.

| # | Decision | Reference |
|---|---|---|
| 1 | Should the disabled phone-format rule be enabled? It would reject values the current system accepts and may already have stored. | BR-PERSON-006 |
| 2 | Should the silent blank-rejection on the four identity fields become an explicit error? It currently reports success while discarding the change. | BR-PERSON-007 |
| 3 | Should the identity short-circuit keep discarding other filters, and what precedence applies when both `id` and `idNumber` are supplied (currently non-deterministic)? | BR-PERSON-010 |
| 4 | Should `*Like` filter values be escaped? Today `?idNumberLike=%` returns the whole registry. | BR-PERSON-017 |
| 5 | Should an unknown sort key be a 400 or silently ignored, instead of the current 500? | BR-PERSON-018 |
| 6 | Where should `PersonNotFoundException` live, given its only throw site is in another domain? | BR-PERSON-023 |
| 7 | Should the broken profile-update path be fixed before or during extraction? Fixing it enables a flow that currently always fails, making patch semantics observable for the first time. | BR-PERSON-027 |
| 8 | Should a person ever be deletable, and what happens to inbound references? Today nothing deletes, so orphans accumulate — and registration has no compensating action available. | BR-PERSON-013, BR-PERSON-022 |
| 9 | Should `people:read` remain a single coarse authority granting full registry enumeration with no redaction? | `permissions.md` §3 |
| 10 | Should `person` and `customer` be one service, given that a customer's external representation *is* a person? | `00-domain-index.md` §2 W4 |

---

## 6. Completeness statement

This suite is **complete**. It is not truncated, and no artifact is missing or thin:

- 14/14 source files have behavioural coverage in `01` and `02`.
- `specs/01-data-model.md`, `specs/03-business-rules.md` and `specs/04-user-stories.md` are
  present and populated.
- `specs/05-api-contract.md` and `specs/06-integration-points.md` are present and populated;
  `02`, `07` and `08` are omitted with the reasons in §4 above.
- `errors.md`, `permissions.md` and all four `reference/` files are populated with
  domain-specific content; none is a template or a bare pointer.
- `events.md` is omitted rather than emitted as an empty file, per §4.
- All three diagram files contain self-contained Mermaid documents.
