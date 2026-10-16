# Trebol Monolith to Microservices

## Name

Trebol Monolith to Microservices

## Scope and applicability

**This transformation applies to exactly one repository:**
`org.trebolecommerce:trebol-backend-monolith-java17`.

It is deliberately **not** generic. It names specific classes, line numbers, the 54
authority codes, the 11 order statuses, and a 17-item defect register derived from a
static analysis of this codebase. Running it against any other repository will
produce wrong guidance.

If you need a reusable version, generalise it first — the *method* transfers
(gate on cross-domain repository coupling, publish the authority catalogue as a
generated artefact, build a defect register, phase by dependency order), but all
the specifics do not.

## Pre-flight validation

Before proceeding, verify all of the following. **Stop if any check fails.**

1. `pom.xml` declares `<artifactId>trebol-backend-monolith-java17</artifactId>`.
   If not: output `ERROR: This transformation applies only to trebol-backend-monolith-java17.` and stop.
2. `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
   exists and validates. This transformation **consumes** it; it does not produce it.
   If absent, run `AWS/business-rules-extraction` first.
3. `detailed-business-specification/` exists with at least `domain-person/` and
   `domain-image/`. These are the worked examples the porting phases follow.

## Objective

Decompose the monolith — 294 source files, 17,800 LOC, 22 tables, 67 HTTP routes —
into **three** independently deployable services plus a shared contract artefact,
preserving observable behaviour except where the defect register rules otherwise.

**Target, approved by the project owner:**

| Service | Absorbs | Owned tables |
|---|---|---|
| Identity | `person`, `user`, `customer`, `salesperson` | 7 |
| Catalogue | `product`, `product-category`, `product-list`, `image` | 6 |
| Orders | `order`, `billing`, `shipper` | 8 |

`company-profile` (`app_params`, one public read, no write path) folds into
Catalogue. It is not a service.

Full rationale, evidence and per-grouping justification:
`references/00-transformation-definition.md` §2.

## Non-goals

- Changing the external HTTP contract. Route-compatible migration only.
- Rewriting in another language.
- Building the modelled-but-unintegrated capabilities (shipping rates, electronic
  invoicing, inventory, image hosting). That is new product work.
- Twelve services, one per bounded context. Explicitly rejected — see §2.3.

## Invariants

| # | Invariant |
|---|---|
| G1 | No behavioural change except by an explicit ruling in the defect register (§6). Silent "fixes" are forbidden; several defects are load-bearing for clients. |
| G2 | Preserve every client-observable literal — error messages, authority codes, order status names, parameter names. They are contract. |
| G3 | Preserve all tests. They may be modified for compatibility, never deleted or disabled. |
| G4 | No new custom package repositories. Maven Central or already-configured sources only. |
| G5 | No dependency downgrades; no dependency with known CVEs. |
| G6 | **Refactor inside the monolith before extracting.** Every phase lands in the monolith, green, before any service is split out. This is what makes the work reversible. |
| G7 | No secret in committed configuration in any new service. The baseline keeps the JWT signing key, database credentials and bootstrap passwords in committed files; do not replicate that three times. |

## Mandatory gates

**No extraction may begin until Gates A and B are complete and the build is green.**
Attempting otherwise fails at compile time.

| Gate | Requirement | Exit condition |
|---|---|---|
| **A** | Replace cross-domain repository reads with contracts. `OrdersConverterServiceImpl` resolves products (by barcode, per line), billing types, billing companies (read **and create**) and shippers through **four other domains' repository interfaces**, inside one checkout call. `OrdersPatchServiceImpl` adds two more. | No class in the `order` domain imports another domain's repository. The order domain's 70 rules are the regression suite. |
| **B** | Publish the authority catalogue as a generated, versioned artefact. 53 of 54 authority codes exist **only as duplicated string literals** across eleven domains' controllers; only `checkout` is a constant. Renaming one silently disables an access check. | Zero authority string literals in controller code; build fails on an unknown code. |
| **C** | Record the consistency decision for registration and checkout. Note: **neither flow is transactional today** — verified, no `@Transactional` on `RegistrationServiceImpl` or `CheckoutServiceImpl`. Partial state is already possible. | Decision recorded. Recommended: keep co-located, which the approved grouping already achieves. |
| **D** | Record the authentication propagation decision. `SecurityConfig` declares **no request-matcher rules at all** — enforcement is entirely method-level, so **any endpoint without an authority annotation is publicly reachable**. A gateway therefore cannot derive authorization from paths. | Decision recorded: shared signing key, local verification per service, method-level enforcement retained, gateway for routing and TLS only. |

Gate detail: `references/00-transformation-definition.md` §5.

## Defect register — rulings required before porting

17 items. Every one is current observable behaviour, so porting silently either
preserves a defect or breaks a contract. **Each requires an explicit ruling.**

Seven are marked fix-before-extraction:

| # | Finding |
|---|---|
| D1 | A **mistyped** `DELETE` query parameter deletes an entire table. Affects every write-capable domain. |
| D2 | Malformed-token handling writes the **full token value** to the log. |
| D6 | Profile update **always fails** for any account that already has a profile. |
| D7 | Duplicate detection covers 1 of 3 unique columns; collisions return 500 instead of 409. |
| D9 | Payment session identifier is a **Java hash code** — breaks with more than one instance, and a split means more instances. |
| D14 | Guest login writes real `person` and `customer` rows **from inside an authentication filter**. |
| D17 | No uniqueness constraint on the role-permission join table. |

Also registered and requiring rulings: public unauthenticated payment callbacks with
no signature or idempotency (D4); payment commit failures swallowed and recorded as
declines (D3); a `PATCH` that bypasses the order state machine entirely (D5); four
order statuses seeded but reached by no code (D10); mail sent after commit with no
retry (D11); `transportValue` never computed and excluded from the payment amount
(D8).

Full register with recommendations: `references/00-transformation-definition.md` §6.

## Execution phases

| Phase | Work | Why this order |
|---|---|---|
| **0** | Gates A–D; land every fix-before-extraction defect (D1, D2, D6, D7, D9, D14, D17). | Nothing compiles across a boundary until Gate A is done. |
| **1** | Catalogue extraction. Mode B specs for `product`, `product-category`, `product-list` first; split 6 tables; stand up behind the product-by-barcode contract; fold in the `company-profile` read. | Fewest inbound dependencies; `image`'s boundary is already service-level and clean. |
| **2** | Identity extraction. Mode B specs for `user`, `customer`, `salesperson`; resolve HS-02 (the identity kernel writes **outward** into `person` and `customer`, including from an auth filter); split 7 tables; port `/public/login` and `/public/guest` **as filters**; distribute the signing key; make registration genuinely transactional now that all three writes are co-located. | Catalogue must exist first; Orders depends on Identity. |
| **3** | Orders extraction. Mode B specs for `order` and `billing` — the largest remaining analysis task, partitioned by subsystem (status machine, checkout, payment adapter, mail adapter, receipt, search/patch); split 8 tables; port the payment adapter with the D3/D4/D9 fixes; put mail behind an outbox (D11); enforce the state machine and close the D5 bypass. | Reads from both other services, so it goes last. |
| **4** | Decommission: remove migrated code, retire the shared schema, confirm all 67 routes served. | — |

Phase detail: `references/00-transformation-definition.md` §7.

## Inputs

| Input | Location |
|---|---|
| Domain manifest (machine-readable) | `business-rules-extraction/domain-decomposition-overview/domain-manifest.json` |
| Global schema, 22 tables, 11 recorded divergences | `business-rules-extraction/database-documentation/00-global-schema.sql` |
| Seed data incl. 54 authority codes, 11 order statuses | `business-rules-extraction/database-documentation/00-seed-data.json` |
| Coupling hotspots HS-01…HS-03 | `business-rules-extraction/reference-documentation/coupling-hotspots.md` |
| The two outbound integrations and their failure semantics | `business-rules-extraction/reference-documentation/external-integrations-map.md` |
| Token mechanics and the enforcement model | `business-rules-extraction/cross-cutting-documentation/authentication-authorization.md` |
| 327 business rules — the regression suite | `business-rules-extraction/detailed-domain-bre/domain-*/business-rules.md` |
| Worked porting-depth examples | `detailed-business-specification/domain-{person,image}/` |

**Known input gap.** Porting-depth specifications exist for `person` and `image`
only. The other ten domains are covered at Mode A depth, which is sufficient for
planning but **not** for porting — so each phase begins by producing Mode B specs
for the domains it touches.

## Exit criteria

| # | Criterion |
|---|---|
| X1 | Three services deploy independently; each owns its tables exclusively; no service reads another's schema. |
| X2 | All 22 tables assigned; no cross-service foreign key. |
| X3 | All 67 routes served and behaviour-identical, except where a register ruling records an intended change. |
| X4 | All 327 business rules preserved or with a recorded ruling. |
| X5 | Every baseline test present and passing. |
| X6 | No authority string literals; catalogue is a generated versioned artefact. |
| X7 | No cross-domain repository imports; all cross-service access via published contracts. |
| X8 | Registration and checkout have a defined, tested consistency model. |
| X9 | Payment callbacks authenticated and idempotent. |
| X10 | No secret in committed configuration in any service. |
| X11 | Every register item has a recorded ruling; every "fix" ruling implemented and tested. |

## A decision to make before executing

At 17,800 LOC and 22 tables, a **modular monolith** — these same bounded contexts
enforced as module boundaries in one deployable — delivers the ownership and
testability benefits without distributed transactions, and keeps checkout atomic.

Phase 0 is identical for both targets, so the decision can be deferred until it is
complete, when the remaining split cost is concrete rather than estimated.
**Reject the modular monolith consciously rather than by default.**
See `references/00-transformation-definition.md` §2.3.

## Reference

| Document | Contents |
|---|---|
| `references/00-transformation-definition.md` | The full definition: objective, target architecture with evidence, inputs, guardrails, four gates, the 17-item defect register with recommendations, five phases, exit criteria, risks. |
