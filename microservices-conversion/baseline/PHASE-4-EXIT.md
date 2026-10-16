# Phase 4 Exit Record — Decommission

Date: 2026-10-16

---

## 1. Result

**PASS. The transformation is complete.** The monolith is gone; the platform is four reactor
modules, three of them independently deployable services.

```
. ~/opt/env.sh && mvn -B -fae clean verify          # from the repository root
```

| Module | Result | Tests |
|---|---|---|
| Trébol Platform | SUCCESS | — (aggregator) |
| Trébol Shared Contracts | SUCCESS | 7 |
| Trébol Catalogue Service | SUCCESS | 131 |
| Trébol Identity Service | SUCCESS | 127 |
| Trébol Orders Service | SUCCESS | 230 |

**495 tests, zero failures, zero errors, zero `[ERROR]` lines, BUILD SUCCESS in 09:59.**

The headline count falls from Phase 3's 906 because the monolith's 460 tests were almost entirely
**duplicates** of the service tests — it had been a verified copy since Phase 1. The question that
matters is not the total but whether any baseline test *class* was lost, which is §4.

---

## 2. What Phase 4 did

The definition specifies three steps. They were executed in a different order than written, for a
reason worth recording: **step 3 had to come first.** You cannot "confirm all 67 routes are served"
after deleting the code that serves them.

### 2.1 The route census, run before anything was deleted

| | Routes |
|---|---|
| Monolith | **67** — matching the definition's stated number, which validates the measurement |
| Catalogue | 26 |
| Identity | 23 |
| Orders | 20 |
| Services combined, deduplicated | **67** |

Measured again *after* the deletion by recovering the monolith's controllers from git history:
**0 routes served by no service, 0 routes invented by a service.** One route is served by more than
one service — `GET /` — deliberately, per the ruling in §3.

The census found exactly one gap before it was fixed: 66 of 67. See §3.

### 2.2 Removal

- `trebol-backend-monolith` removed from the reactor and deleted: **465 tracked files**, 316 main
  and 132 test Java sources, plus the shared **23-table** `schema-mariadb.sql` and its seed data.
- The 23 tables were the 22 of record plus `order_mail_outbox`, which D11 added to the monolith's
  schema as well as to orders-service. This is the same arithmetic corrected at the Phase 3 exit.
- No service's pom referenced the monolith, so the reactor change was dependency-clean.
- The code is **deleted, not rewritten**: it remains recoverable from git history at `8f4e22b` and
  its ancestors. That history is not a footnote — it is what both the route census and the X5
  comparison in §4 were executed against.

### 2.3 Relocation, which had to precede removal

Two things lived inside the monolith module and would have been destroyed with it.

**256 documentation files** moved to the repository root: `business-rules-extraction` (154),
`detailed-business-specification` (86), `microservices-conversion` (16). That is every ADR, all
four phase exit records, the 327-rule extraction, all 20 porting-depth specifications and the
defect register this phase is still appending to. They are the evidence base X4 and X11 are proven
against.

**The authority catalogue generator** moved into `shared-contracts`:
`AuthorityCatalogueGenerator` and `AuthorityCatalogueIntegrityTest`, with both paths repointed and
three test-scope dependencies added (`junit-jupiter`, `assertj-core`, `jackson-databind` — not
`spring-boot-starter-test`, which would have put Spring into a deliberately Spring-free module).

> X6 does not merely require the absence of authority literals. It requires the catalogue to be
> **"a generated, versioned artefact"**. Deleting the generator with the monolith would have left
> `AuthorityCodes` hand-maintained while X6 continued to read PASS. The integrity test now runs
> green from its new home, which is what proves the relocation actually works.

One assertion had to be split: the integrity test checked
`Constants.AUTHORITY_CHECKOUT`, and `Constants` is per-service application code that
`shared-contracts` cannot see. The load-bearing half (the catalogue's literal token value) stayed
with the catalogue; the alias half moved to identity-service as `AuthorityCheckoutAliasTest`,
because identity is the only service that **issues** tokens.

---

## 3. The one route gap, and its ruling

`GET /` — the `RootController` health check — was served by the monolith and by **no** service, and
had no test in any service. Nothing in the build would have reported its loss.

**Ported to all three services**, byte-for-byte with its test. The alternatives were rejected on
record: dropping it is defensible on the code's own "Non-operating endpoint" wording, but a load
balancer or liveness probe hitting `GET /` would break in deployment infrastructure that no code
review inspects; Actuator answers on a different path and so would not satisfy X3 as written.

Consequence, recorded rather than left to be "tidied up": `GET /` is now the only route served by
all three services. That is correct — three independently deployable services each need their own
liveness endpoint (X1).

---

## 4. X5 — five test classes had been silently lost, and one of them was X9's only evidence

Comparing the baseline's **132** monolith test classes against the services found **seven** absent.
Five covered live service code and had simply been missed by the Phase 3 port:

| Restored into orders-service | Tests |
|---|---|
| `CheckoutServiceImplTest` | 15 |
| `PublicCheckoutControllerTest` | 3 |
| `AddressesConverterServiceImplTest` | 2 |
| `BadInputExceptionTest` | 3 |
| `CorsMappingParseExceptionTest` | 2 |

> **`CheckoutServiceImplTest` is the only executing evidence for X9.** It contains D3's
> `infrastructure_failure_does_not_mark_the_order_as_failed` and D4's four nonce and replay tests.
> The main code had been ported in Phase 3; the tests had not. Deleting the monolith would have
> removed every running test for "payment callbacks are authenticated and idempotent" while X9
> still read **PASS** in the Phase 3 exit record.
>
> All 15 pass unchanged against the business-key cuts — no adaptation was needed, which also
> confirms the Phase 3 cuts did not disturb checkout behaviour.

This was found only because the comparison was run against git history instead of against the
Phase 3 exit record's claims. A phase exit that asserts "every baseline test is present" is worth
exactly as much as the mechanical check behind it.

Two classes are **deliberately not carried forward**, both recorded in the register:

- `BackendAppTest` — superseded by one context-load test per service. `OrdersApplicationTest` was
  added in this phase; Orders had been loading its context only incidentally, via
  `OrderStatusPatchPersistenceProbeTest`.
- `PhoneNumberValidatorTest` — `@PhoneNumber` and `PhoneNumberValidator` were referenced by nothing
  in the monolith's own main source. Verified, not assumed. Dead code; omitting it loses no
  behaviour.

---

## 5. D31 — a generated credential in two verify-only services

Spring Boot's `UserDetailsServiceAutoConfiguration` creates an in-memory user with a **random
generated password** when no `UserDetailsService`, `AuthenticationProvider` or
`AuthenticationManager` bean exists — exactly the state of a verify-only service. Catalogue and
Orders were both logging `Using generated security password: ...` on every startup, in services
whose own `SecurityConfig` javadoc says they have no `UserDetailsService`, and whose ADR-002 role
is to verify tokens and never authenticate.

It was **inert** — verified, not assumed: neither chain configures `formLogin` or `httpBasic`, so
the account had no way to authenticate over HTTP. Excluded anyway, because that inertness rested on
an absence somewhere else; adding `httpBasic()` for any reason would have silently promoted it to a
working account.

Fixed on both application classes, confirmed by the generated-password line being **absent** from
the final reactor log, and asserted going forward by
`OrdersApplicationTest.no_token_issuing_or_credential_machinery_is_present`.

Found because that new test asserts ADR-002 **positively, by bean type**, rather than by assuming
absence. This is the second consecutive phase in which a context-load test caught something no unit
test could; D29 was the first.

---

## 6. Tests added in this phase

| Test | Module | Why |
|---|---|---|
| `RootControllerTest` ×3 | all three services | the ported health check |
| `OrdersRouteParityTest` (5) | orders | Orders was the only service without a parity test, leaving checkout and the payment callbacks — the most security-sensitive surface — with no pin on their HTTP shape |
| `NoAuthorityLiteralsArchitectureTest` (3) | orders | Orders was also the only service without this guard, despite declaring 14 authority checks |
| `OrdersApplicationTest` (4) | orders | the missing context-load test; found D31 immediately |
| `AuthorityCheckoutAliasTest` (2) | identity | the half of the split integrity assertion that needs `Constants` |
| 5 restored classes (25) | orders | §4 |

`OrdersRouteParityTest` asserts the **anonymous** half of the surface explicitly, which is the part
most at risk from well-meaning hardening: `GET /data/billing_types` and `GET /data/shippers` are
public reads that anonymous checkout needs, and the two `/public/checkout/validate` callbacks carry
no authority because Transbank sends no credential — their protection is D4's nonce. A future change
"securing" either group would break payment confirmation or anonymous checkout.

---

## 7. Exit criteria — final state

| # | Criterion | State |
|---|---|---|
| X1 | Three services deploy independently, each owning its tables exclusively | **PASS** |
| X2 | All 22 tables assigned, no cross-service foreign key | **PASS** (plus `order_mail_outbox`, new in D11) |
| X3 | All 67 routes served and behaviour-identical except where a ruling records a change | **PASS** — 67/67, verified against history both before and after removal |
| X4 | All 327 rules preserved or ruled | **PASS** |
| X5 | Every baseline test present and passing | **PASS** with the two recorded exceptions in §4 |
| X6 | No authority literals; catalogue is a generated, versioned artefact | **PASS** — and the generator now lives with the artefact |
| X7 | No cross-domain repository imports; cross-service access via published contracts | **PASS** — enforced at repository *and* entity level by test |
| X8 | Registration and checkout have a defined, tested consistency model | **PASS** |
| X9 | Payment callbacks authenticated and idempotent | **PASS** — and its evidence now actually executes (§4) |
| X10 | No secret in committed configuration in any service | **PASS** — the monolith was the last holder; D31 removed the last generated credential |
| X11 | Every register item has a ruling; every "fix" ruling implemented and tested | **PASS** |

**All eleven criteria PASS.** X10 was PARTIAL in every previous phase solely because the monolith
retained its own configuration; removing it closes that.

Note on numbering: the definition's criteria are **X1–X11**. The "X12 — monolith decommissioned"
tracked through Phases 0–3 was derived by this programme, not specified. It is satisfied, but it is
recorded here as a derived criterion rather than a twelfth requirement.

---

## 8. What remains for the owner

Nothing blocks the conversion. These are carried forward from earlier phases and are unchanged:

| Item | Detail |
|---|---|
| **Retiring the schema in a live environment** | The only genuinely unrecoverable act in this programme, and it is **not** done here. This phase deleted `.sql` files, which git retains. Dropping those tables against a populated database destroys data git never had. Sequence it against a backup and a verified migration of the three service schemas. |
| **The orphaned-customer window** | Administrative order-create path only. Identity writes the customer, Orders writes the order, two transactions, no shared commit. Guest and registered paths are unaffected — D14 provisions the guest customer at token issue, so checkout resolves rather than creates. Consistent with ADR-001. |
| **Three legacy surrogate columns** | `orders.customer_id`, `orders.salesperson_id`, `order_details.product_id` — retained nullable and unmapped so no data migrated. Now that nothing reads them, they can be dropped. |
| **D27** | `paymentType` and `shipper` patches are silent no-ops, same `updatable = false` mapping that made D5 one. |
| **D28** | `MailgunMailingServiceImpl` mutates one shared `Unirest` request. Safe only while the outbox dispatcher is single-threaded. |
| **D23** | Closed with the monolith. Verified not to reproduce in orders-service. |
| **D24, D25, D26** | Unchanged. **D26 remains the security-relevant one** — the untrusted-token message interpolates the whole bearer token; nothing emits it today, but the first handler that logs `getMessage()` leaks a live credential. |
| **The dropped `customer` sort key** | The one client-visible capability this conversion removed. Pinned by `OrdersSortSpecTest`. |
| **Inert validation properties** | `trebol.validation.*` in orders-service binds to nothing. |
| **Repository hygiene** | `target/` build output is tracked from earlier phases and `.gitignore` covers only `*.log`. Deliberately untouched inside a conversion phase. |

### CVE position — unchanged, and now uniform

**QueryDSL 5.0.0, CVE-2024-49203 (HIGH), still no fixed release.** Inherited, not introduced. The
mitigation — `orderBy` only ever receives an `OrderSpecifier` from a closed map — is now **asserted**
by `OrdersSortSpecTest` rather than argued from reading code. Re-review trigger passes to whoever
owns the platform next.

Removing the monolith resolved the split position recorded since Phase 1: it was the last module
shipping `mariadb-java-client 3.3.3` and `commons-lang3 3.17.0`. **All three services pin the fixed
3.3.5 and 3.18.0**, so the platform is no longer internally inconsistent about those two CVEs.

---

## 9. The transformation is complete

Four phases: Phase 0 hardened the monolith and built the gates; Phase 1 extracted Catalogue;
Phase 2 extracted Identity; Phase 3 fixed six payment and lifecycle defects before extracting
Orders; Phase 4 decommissioned the monolith.

From a 115-class, 363-test monolith with 3 permanently failing tests and a shared 22-table schema,
to three independently deployable services with exclusive schemas, 495 passing tests, zero
failures, and all eleven exit criteria met.
