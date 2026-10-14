# Phase 1 Exit Record — Catalogue extraction

Verified state at the end of Phase 1. Every number below was produced by a command
re-run for this record, not carried over from a commit message.

Commits: `edc435d` (ADR-003 ruling), `d7c23ef` (porting-depth specs), `ade605b`
(reactor + shared contracts), and the Catalogue service commit this record
accompanies.

---

## 1. What exists now

| Module | GAV | Role |
|---|---|---|
| root aggregator | `org.trebolecommerce:trebol-platform:0.3.0-SNAPSHOT` (pom) | Aggregates the reactor. **Not** the monolith's parent. |
| shared contracts | `org.trebolecommerce:trebol-shared-contracts:0.3.0-SNAPSHOT` (jar) | Publishes the generated authority catalogue. Dependency-free by design. |
| monolith | `org.trebolecommerce:trebol-backend-monolith-java17:0.2.5-SNAPSHOT` (war) | Unchanged except for one added dependency. Still owns and serves all 67 routes. |
| **catalogue service** | `org.trebolecommerce:trebol-catalogue-service:0.3.0-SNAPSHOT` (jar) | **New.** 104 main classes, 7 owned tables, 6 route surfaces, 130 tests. |

---

## 2. Build and test gate

```sh
. ~/opt/env.sh && cd <repo root> && mvn -B clean verify -fae
```

| Module | Result |
|---|---|
| Trébol Platform | SUCCESS |
| Trébol Shared Contracts | SUCCESS |
| Trébol Backend Monolith | **Tests run: 414, Failures: 1, Errors: 2** |
| Trébol Catalogue Service | **Tests run: 130, Failures: 0, Errors: 0** — SUCCESS |

**PASS.** All three monolith failures are the known D18 environmental ones, all in
`WebpayplusPaymentServiceImplTest`, caused by the unmocked live Transbank sandbox:
`TestMode.fetches_payment_result`,
`TestMode.fetches_payment_page_for_started_transactions`,
`ProductionModeWithoutConfig.will_not_fetch_transaction_result`. The monolith's test
count is **unchanged from Phase 0** (414), so nothing regressed and nothing was
removed.

### `-fae` is now required, and why

The monolith's three known failures fail its module, which in a plain reactor build
**stops the reactor before the catalogue module runs** — the first full run produced
no Catalogue result at all. `-fae` (fail at end) lets every module report. This is a
consequence of D18 being unfixed; once D18 is fixed before Phase 3, the plain command
works again. The flag does **not** weaken any gate: the monolith's jacoco bundle
check and both modules' surefire results are still evaluated, and the overall build
still exits non-zero.

---

## 3. Gate state, re-verified mechanically

| Gate | Command | Result |
|---|---|---|
| B — no authority literals (monolith) | the published grep over `trebol-backend-monolith/src/main/java` | **0** |
| B — no authority literals (catalogue) | `NoAuthorityLiteralsArchitectureTest` in the new module | green, 3/3 |
| B — catalogue integrity | `AuthorityCatalogueIntegrityTest` | green, 7/7, now reading the **moved** file in `shared-contracts` |
| A — order domain imports no foreign repository | the published grep | 4 lines, **all own-domain** (`OrdersRepository`, `OrderDetailsRepository`, `OrderStatusesRepository`) — the known over-broad-grep limitation recorded in PHASE-0-EXIT.md, unchanged |
| C, D | artefacts present | `ADR-001`, `ADR-002` readable |
| ADR-003 | artefact present | status **accepted — option A** |

---

## 4. Exit criteria moved by Phase 1

| # | Before Phase 1 | Now |
|---|---|---|
| X1 | FAIL — no service existed | **PARTIAL** — one of three services exists and builds independently; it owns its 7 tables exclusively and reads no other schema. Identity and Orders remain. |
| X2 | FAIL — 22 tables in one schema | **PARTIAL** — 7 of 22 tables assigned, with **no cross-service foreign key** in the catalogue schema (§5). |
| X5 | PARTIAL | **PARTIAL, improved** — all 26 catalogue-domain test classes now exist in the service that owns the code, reporting a non-zero count (G8 satisfied for this module). |
| X6 | PASS, nominally | **PASS, genuinely** — the catalogue is a separate versioned artefact consumed by two modules, not a file one module happens to own. |
| X10 | FAIL | **PARTIAL** — the new module has **no secret in committed configuration**; the monolith's own secrets are untouched and still fail this criterion (D20). |
| X3, X4 | PARTIAL | unchanged in status; route parity for the 6 catalogue surfaces is now test-enforced (§6). |
| X12 | FAIL | unchanged **by design** — see §7. |

---

## 5. Table ownership — the seven catalogue tables

`trebol-services/catalogue-service/src/main/resources/schema-mariadb.sql`, extracted
verbatim (DDL, column types, collations, index and constraint names unchanged):

`app_params`, `images`, `product_lists`, `product_categories`, `products`,
`product_images`, `product_list_items`.

**Every foreign key in that file points at a table in the same file:**

| Constraint | References |
|---|---|
| `FK_PROD_CAT_PARENT` | `product_categories` (self) |
| `FK_PRODUCT_CAT_ID` | `product_categories` |
| `FK_PIMGS_IMAGE_ID` | `images` |
| `FK_PIMGS_PROD_ID` | `products` |
| `FK_PLIST_ITEM_PARENT_ID` | `product_lists` |
| `FK_PLIST_ITEM_PROD_ID` | `products`, `ON DELETE CASCADE` — **preserved deliberately**: BR-PRODLIST-023 depends on it, because the application-level cleanup `deleteByProductId` has no caller |

**The only cross-boundary foreign key in the original schema points inward** —
`order_details.product_id` → `products(product_id)` `ON DELETE SET NULL` — and
`order_details` belongs to Orders. It is cut in **Phase 3**, not here. It is already
nullable and `order_details` already stores a description and unit-value snapshot, so
cutting it loses nothing.

Seed data: the four `company` rows of `app_params` **and** the
`ALTER TABLE ... RESTART WITH 5` statement, without which the first inserted parameter
collides on `param_id` (BR-COMPANY-012).

---

## 6. What the 130 catalogue tests consist of

| Source | Count |
|---|---|
| Ported domain tests (product, product-category, product-list, image, company-profile) | 26 classes |
| Ported shared-kernel tests (`CrudGenericServiceTest`, `DataGenericControllerTest`, `DataCrudGenericControllerTest`, `SortSpecParserServiceImplTest`) | included above |
| Ported test helpers | `ProductsTestHelper`, `ProductCategoriesTestHelper`, `ImagesTestHelper`, `TestConstants` |
| **New** — `CatalogueApplicationTest` | context load; asserts all 6 controllers **and** the published `ProductLookupContract` bean are wired |
| **New** — `CatalogueRouteParityTest` | 4 tests pinning the 6 route prefixes, the HTTP method set per prefix, the authority **value** per write, the five deliberately anonymous reads, and the image registry's class-level `isAuthenticated()` |
| **New** — `NoAuthorityLiteralsArchitectureTest` (ported guard) | applied to catalogue sources |

**No test was deleted or disabled anywhere** (G3). The tests also remain in the
monolith, because the monolith still owns and serves that code until Phase 4 — see §7.

Two deviations in the new/ported test code, both called out rather than quiet:

1. `NoAuthorityLiteralsArchitectureTest` is a copy of the monolith's guard with **one
   number changed**: the expected count of reflectively-discovered authority checks is
   19 here versus 51 in the monolith. That number is module-specific input, not
   behaviour. Documented in the class javadoc.
2. `CatalogueRouteParityTest` initially asserted that all six reads were anonymous and
   **failed**. The assumption was wrong, not the code: `DataImagesController` carries a
   class-level `@PreAuthorize("isAuthenticated()")` and is the only catalogue domain
   whose `:read` authority is actually enforced. The test now pins the real behaviour,
   including that exception. The failure is recorded here because it is exactly the
   kind of thing a parity test exists to catch.

---

## 7. The intentional duplication — read this before Phase 2

**Catalogue code now exists in two places**, and that is per the definition, not an
oversight. The monolith keeps every catalogue class, test, table and route until
**Phase 4** decommissions it. Until then:

- The monolith is still the serving system for all 67 routes.
- The catalogue service is a verified, independently buildable copy with identical
  fully-qualified class names, literals and schema.
- **X12 stays FAIL by design.** The definition is explicit: "a service that duplicates
  code still present in the monolith is a copy, not an extraction".

**Drift risk, and the mitigation.** Any change made to catalogue code in the monolith
during Phases 2–3 will not reach the service. Mitigations: identical FQCNs make a diff
trivial; the tests exist in both modules, so a behavioural divergence surfaces as a
test disagreement; and no Phase 2 or Phase 3 work is expected to touch catalogue
classes (the remaining product leaks are in **order-domain** files —
`OrderDetail.product`, `OrderDetailPojo`, `ReceiptDetailPojo`, `ReceiptServiceImpl`).
If a catalogue change does become necessary, it must be applied to both modules in the
same commit.

---

## 8. Register items added or resolved

| Item | Outcome |
|---|---|
| D12 | Phase 1 **PRESERVE** — `images:create` bypass ported unchanged; now documented where it is implemented. Post-conversion backlog. |
| D13 | Phase 1 **PRESERVE** — contract defined in writing: the product write succeeds and the image is dropped silently (DEBUG log). Post-conversion backlog. |
| **D21 (new)** | QueryDSL 5.0.0 / CVE-2024-49203 (HIGH, no fixed release). **Accepted as inherited**, with the SortSpec whitelists verified — not assumed — as mitigation. Re-review at Phase 2 and Phase 3. |
| **D22 (new)** | `CompanyDetailsPojo` is `@JsonInclude(ALWAYS)` while BR-COMPANY-006 asserts omission. **Code preserved; the recorded rule is wrong.** Owner action required. |
| Follow-up | The monolith still ships `mariadb-java-client` 3.3.3 and `commons-lang3` 3.17.0, both with CVEs. The catalogue pins 3.3.5 and 3.18.0. Bumping the monolith is its own reviewed change, deliberately not folded in. |

---

## 9. Known limitations of this phase, stated plainly

1. **The service has never been run against MariaDB.** `schema-mariadb.sql` is
   extracted verbatim and structurally verified, but it loads only when
   `spring.sql.init.platform=mariadb`; the tests run on H2 with a Hibernate-generated
   schema, exactly as the monolith's own tests do. A deployment smoke test against
   MariaDB is outstanding.
2. **No integration test exercises an HTTP route end to end.** The ported tests are
   unit tests with mocked collaborators, plus one context-load test. That is the
   coverage the monolith had; the port neither improves nor degrades it.
3. **No deployment artefacts.** No container image, no pipeline, no service discovery,
   no gateway. Those are the from-nothing infrastructure costs ADR-003 accepted, and
   none of them is built yet.
4. **The catalogue module declares no jacoco coverage gate.** The monolith's 0.20
   bundle gate is untouched and still enforced; an equivalent gate for the new module
   was not added, and should be before Phase 4 so the extracted services are held to
   the same standard.
5. **`ProductLookupContract` is published from the catalogue service but nothing calls
   it across a process boundary yet.** Orders still calls the in-monolith
   implementation. The remote-call seam is built in Phase 3, and with it the
   per-order-line network cost that ADR-003 accepted.

---

## 10. Phase 1 is complete

Gates A–D remain satisfied, ADR-003 is ruled, porting-depth specifications exist for
every domain Phase 1 touched, the Catalogue service builds and tests independently
with 130 green tests and exclusive ownership of its 7 tables, the monolith is
unregressed at 414 tests, and no secret appears in the new module's committed
configuration.

**Next: Phase 2 — Identity.** Its prerequisites are Catalogue (done) and the Gate D
decision (recorded in ADR-002). Note that Phase 2 is the phase that must resolve
coupling hotspot HS-02 and make registration genuinely transactional (X8), and that
the `product_lists`/`products` precedent above — a preserved `ON DELETE CASCADE` — has
no equivalent in Identity, where `people` is referenced by `customers`, `salespeople`
and `app_users`.
