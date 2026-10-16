# Phase 3 Exit Record — Orders extraction

Date: 2026-10-16
Commits: `b3d1714`, `b15a155`, `095f5b8`, `0c1f4bf`, `d285b87`, `2b4c851`, `711e6ec`,
`63d2e7e`, `0c94614`, `a67cd04`, `b7180a7`, `b9ed88d`, `68c2b85`, and this record.

---

## 1. Result

**PASS.** The full five-module reactor is green, including packaging.

```
. ~/opt/env.sh && mvn -B -fae clean verify          # from the repository root
```

| Module | Result | Tests |
|---|---|---|
| Trébol Platform | SUCCESS | — (aggregator) |
| Trébol Shared Contracts | SUCCESS | — |
| Trébol Backend Monolith | SUCCESS | 460, 0 failures, 0 errors |
| Trébol Catalogue Service | SUCCESS | 130, 0 failures, 0 errors |
| Trébol Identity Service | SUCCESS | 124, 0 failures, 0 errors |
| **Trébol Orders Service** | **SUCCESS** | **192, 0 failures, 0 errors** |

**906 tests, zero failures, zero errors, BUILD SUCCESS in 17:30.** No test was deleted in
this phase in any module.

Two notes on the build command, both corrections to what earlier phases recorded:

1. **`-fae` is no longer load-bearing.** It was introduced in Phase 1 because the monolith's
   three known D18 failures stopped the reactor before the new modules ran. D18 was fixed in
   step 3.1, so the flag now changes nothing. `BASELINE.md` predicted this; it is now
   confirmed. It is kept in the documented command only so that a future failure still
   reports every module rather than the first one.
2. **Do not add `-o`.** A run with `-o` (offline) fails at the monolith's
   `maven-war-plugin:war` goal — three of its dependencies were never fetched into this
   machine's local repository. That is an environment artefact, not a code fault: the same
   tree passes online. It cost one full reactor cycle to learn, so it is written down here.
   Per-module `test`-phase runs are safe offline because they never reach packaging.

The one `[ERROR]` line in a green log is
`Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after
System.exit(0)` — a Surefire shutdown-timing message after a successful exit, not a test
result. Recorded so it is not mistaken for a failure on a later read.

---

## 2. What moved

### 2.1 Landed inside the monolith first (invariant G6, Stage 3A)

Six register items were fixed **and tested in the monolith** before any order code was
copied, so that the extraction ported corrected behaviour rather than carrying defects
across a service boundary:

| Item | Change |
|---|---|
| **D18** | Payment adapter made testable: `WebpayTransactionFactory` seam extracted, test rewritten 5 → 12 tests, **no network access**. This is what turned the monolith green for the first time in the programme. |
| **D3** | A payment-infrastructure failure is no longer recorded as a customer decline. |
| **D4** | Payment callbacks authenticated (per-order nonce) and made idempotent. **Closes X9.** |
| **D5 + D10** | Order status machine enforced through one transition table; the four unreachable statuses rejected as targets. |
| **D11** | Mail moved behind a transactional outbox — the only genuinely new infrastructure in the programme. |

The monolith went from **414 tests with 3 known failures** to **460 tests with zero**.

### 2.2 The new module

`trebol-services/orders-service`, the fifth reactor module: **158 main sources, 46 test
files, 192 tests.** Six route surfaces: `/data/orders`, `/data/order_statuses`,
`/data/billing_types`, `/data/billing_companies`, `/data/shippers`, and the anonymous
`/public/checkout` + `/public/receipt/{token}` pair.

Verify-only security stack per ADR-002 — no login route, no guest filter, no
`UserDetailsService`, no `PasswordEncoder`; `jjwt-impl` is a **runtime**-scope dependency
because this service verifies tokens and never signs one.

### 2.3 Tables owned — and an arithmetic correction

The schema declares **9 tables**, which is **not** the "remaining 8" Phase 2 predicted:

| Owned | Origin |
|---|---|
| `orders`, `order_details`, `order_statuses`, `addresses`, `billing_companies`, `billing_types`, `payment_types`, `shippers` | 8 of the original 22 |
| `order_mail_outbox` | **New**, created by D11's outbox. Not one of the 22. |

So the correct statement of X2 is **22 of 22 original tables assigned**, plus one new
infrastructure table. Catalogue 7 + Identity 7 + Orders 8 = 22.

---

## 3. Gate verification — run, not assumed

```
# Gate B — raw authority literals, every module. MUST be 0.
grep -rhoE "hasAuthority\('[^\"']+'\)" <module>/src/main/java | wc -l
#   monolith 0 | catalogue 0 | identity 0 | orders 0.   PASS
#   Total authority CHECKS (all via AuthorityCodes): 51 | 20 | 17 | 14.

# Gate A — order domain may import only its OWN repositories
grep -rn "import org.trebol.jpa.repositories" orders-service/src/main/java/org/trebol/api/services/
#   -> OrdersProcessServiceImpl (Orders, OrderDetails, OrderStatuses) and
#      ReceiptServiceImpl (Orders). All own-domain. PASS
#   The published grep remains over-broad in exactly the way recorded since PHASE-0-EXIT;
#   OrderDomainCouplingArchitectureTest asserts the stated criterion instead.

# Cuts C1/C2 — no sibling-service ENTITY anywhere in orders main
grep -rhoE "import org\.trebol\.jpa\.entities\.(Product|Customer|Salesperson|Person|Image)" ...
#   -> nothing. PASS   (now also asserted by a test — see §4)

# Cut C3 — the sort map
#   -> 8 keys; "customer" appears only in the comment explaining its removal. PASS

# Schema — 9 CREATE TABLE, 8 FK REFERENCES, every parent inside the owned set. PASS

# G7 / X10 — no secret in orders main resources
#   -> ${TREBOL_JWT_SECRET_KEY} and ${TREBOL_ORDERS_DB_PASSWORD} with NO default;
#      ${WEBPAY_API_KEY:} empty by default. PASS
```

---

## 4. The three boundary cuts, and why the implementation beat the plan

The plan assumed the three cross-service foreign keys could be cut leaving the surrogate
id columns in place — *"the columns are unchanged in name and type, so no data migrates;
only the constraints go."* **That assumption does not hold.** Neither sibling service
exposes a surrogate id in its public API: `ProductPojo` is keyed by `barcode`,
`PersonPojo` by `idNumber`, and neither carries an `id` field. So `orders.customer_id` and
`order_details.product_id` could be neither populated on create nor resolved on read.

Resolved with **business keys** (option A of the recorded decision): three additive
columns — `customer_id_number`, `salesperson_id_number`,
`order_detail_product_barcode` — with the three legacy surrogate columns **retained,
nullable and unmapped, so no existing data migrates.** This is an **intentional deviation**
from the plan, recorded in the schema header and the entity javadoc.

It produced a **better** result than the original design in two concrete ways:

1. **Cut C2 removes the receipt's catalogue dependency entirely** rather than replacing it
   with a `RestClient` call. The barcode is stored on the order line, so
   `/public/receipt/{token}` — which is **anonymous and public** — makes no remote call at
   all. Had C2 been implemented as specified, receipts would have failed whenever the
   Catalogue was down. This was noticed while implementing and reversed an earlier draft.
2. **Customer resolution needs no extra round trip.** The submitted id number *is* the key,
   so there is nothing to read back after a create.

Cut C1 additionally consolidated **four duplicated product-resolution blocks** in
`OrdersProcessServiceImpl` onto one converter method — one lookup policy instead of five.

### Tests added because the cuts removed the compiler's protection

| Test | Why it exists |
|---|---|
| `OrdersSortSpecTest` (3) | Pins cut C3's **exact** eight-key set. Nothing else failed if the cross-service `customer` key returned. Also what keeps D21's mitigation argument true. |
| `ShipperLookupContractImplTest` (4) | The Gate A seam that had **no behavioural pin at all**, against Billing's seven and CustomerProvisioning's four. Pins that this contract is read-**only**, unlike `BillingContract`'s deliberate resolve-or-create. |
| `OrderDomainCouplingArchitectureTest` — extended | **This is the blind spot that hid cut C2.** Gate A greps for *repository* imports, so `ReceiptServiceImpl`'s import of the `Product` **entity** passed cleanly. The new assertion is an **allow-list of the nine owned entities**, so a sibling's future entity is caught without anyone remembering to update the test. A second new test pins that the receipt path makes no remote call. |

One stale ported assertion was also **fixed**: the test asserted
`ProductLookupContractImpl` contains `ProductsRepository`, which cannot hold after
extraction — it is now a `RestClient`. It would have failed at **runtime**, not compile
time, which is why the step-3.8 handoff's "all errors confined to one file" did not reveal
it. It now asserts the two *kinds* of seam: the three **remote** impls must hold a
`RestClient` and must not import a repository; the two **local** impls (billing, shipper)
must delegate to a repository and must not make a network call.

---

## 5. Two defects introduced in step 3.7 and caught in step 3.8

Both were invisible at compile time, and neither was a test problem.

### (a) The service could not have started — a real regression

`src/main/resources/application.properties` wrote the payment settings under
`trebol.integration.webpayplus.*`, but `WebpayplusPaymentProperties` binds at prefix
**`trebol.payment.webpayplus`**, and its `callback-url` and `browser-redirection-url` are
`@NotBlank`. **The context fails on a validation error in every environment.** The file also
declared an `environment` key that is not a field on the class — the real switch is the
boolean `production` — which `ignoreUnknownFields=true` would have swallowed silently.

Corrected to the monolith's prefix, with `production` externalised as `WEBPAY_PRODUCTION`
and every value still env-overridable and secret-free.

> **The three `@SpringBootTest` context probes failing was the only reason this was found
> before deployment.** No unit test touches property binding. This is the strongest
> argument in the programme for keeping a context-load test in every extracted module.

### (b) Configuration copied from identity-service, describing the wrong service

Both the test **and** main property files had been copied from identity-service and still
described it:

| File | Problem | Fix |
|---|---|---|
| `src/test/resources/application.properties` | Identity's CORS routes (`/data/users`, `/public/login`); H2 url named `identity-test`; `spring.sql.init.data-locations` pinned to a `data-test.sql` **that does not exist in this module** — it would have failed the moment a test reached datasource initialisation | Rewritten for this service; seed left at the default so this module's own `data.sql` (12 statuses, 2 billing types, 1 payment type) loads |
| `src/main/resources/application.properties` | Comments claimed this service **"SIGNS"** tokens, is **"the issuer"**, and **"owns the password store and the PasswordEncoder bean"**. All three contradict ADR-002 and the verify-only `SecurityConfig` | Comments corrected. **Keys deliberately retained** — `SecurityProperties` is shared-kernel and `@Validated` (`@NotBlank` key, `@Min(6)` bcrypt strength), so the context cannot start without them |

The test-resources header now records the shadowing rule that caused it, because it is
counter-intuitive and bit this programme twice: **a test `application.properties` replaces
main's entirely**, whereas `data.sql` has a `classpath*:` wildcard default and therefore
loads *both* copies. The same asymmetry is register item 4 of step 2.6.

**Open, recorded, not fixed:** `trebol.validation.id-number-regexp` and
`.phonenumber-regexp` are **inert in this module** — `ValidationProperties` was not ported
and nothing here reads them. Retained with a corrected comment rather than deleted, so the
patterns stay visibly identical across services. Also noted: this file has **mixed line
endings** (53 CRLF, 110 LF) from step 3.7's authoring, against a repository that is
otherwise CRLF throughout.

---

## 6. Exit criteria — movement in this phase

| # | Before Phase 3 | After Phase 3 |
|---|---|---|
| X1 three services deploy independently | PARTIAL (2 of 3) | **PASS (3 of 3)** — all three build, package and own their schema |
| X2 all 22 tables assigned | PARTIAL (14/22) | **PASS (22/22)** — plus one new table, `order_mail_outbox` (§2.3) |
| X3 all 67 routes served | PARTIAL | **PASS** — the three services now serve every route surface |
| X4 all 327 rules preserved or ruled | PARTIAL | **PASS** — the final 104 rules (70 order, 19 billing, 15 shipper) reached porting depth in step 3.6 |
| X5 every baseline test in its owning service | PARTIAL | **PASS** — 192 order-domain tests green in the owning module |
| X6 no authority literals, generated catalogue | PASS | **PASS** — holds across all five modules |
| X7 no cross-domain repository imports | PASS | **PASS, strengthened again** — now also enforced at **entity** level by test |
| X8 tested consistency model | PASS | **PASS** |
| **X9 payment callbacks authenticated** | FAIL | **PASS** — D4's nonce plus idempotency, with four dedicated tests |
| X10 no secret in committed configuration | PARTIAL | **PARTIAL** — satisfied for all three services; the monolith still holds its own until Phase 4 |
| **X11 every ruling implemented and tested** | PARTIAL | **PASS for every Phase-3-assigned ruling** — D3, D4, D5, D10, D11, D18 fixed and tested; D21 trigger actioned |
| X12 monolith decommissioned | FAIL | **FAIL, untouched by design** — Phase 4 |

---

## 7. What is still in the monolith, deliberately

**Everything.** Nothing was deleted in this phase. The monolith still compiles every order,
billing and shipper class, still serves all 67 routes, and still owns the shared 22-table
schema. Per the definition's phasing, migrated code is removed only at **Phase 4**:

> Until Phase 4 completes, all three services are **verified copies**, not completed
> extractions.

The duplication and its drift mitigation remain as recorded in `PHASE-1-EXIT.md` §7.

---

## 8. What Phase 4 inherits

| Item | Detail |
|---|---|
| **The decommission itself (X12)** | Remove the migrated code from the monolith and retire the shared schema. This is the whole of Phase 4. |
| **The orphaned-customer window** | On the **administrative create** path only. `resolveOrCreateCustomerIdNumber` writes a customer in Identity, then the order is written in Orders — two services, two transactions, no shared commit. A failure between them leaves a customer with no order. **The guest and registered paths are unaffected**, because Phase 2's D14 fix provisions the guest customer at token issue, so checkout resolves rather than creates. Consistent with ADR-001's recorded position for `resolveOrCreateBillingCompany`. |
| **Three legacy surrogate columns** | `orders.customer_id`, `orders.salesperson_id`, `order_details.product_id` are retained, nullable and unmapped so that no data migrates. Phase 4 should decide whether to drop them once the monolith no longer reads them. |
| **The dropped `customer` sort key** | The **one client-visible capability removed** by this phase. `GET /data/orders?sortBy=customer` no longer sorts by customer surname. Pinned by `OrdersSortSpecTest`; alternatives recorded in `OrdersSortSpec`. |
| **D28 — Unirest thread safety** | `MailgunMailingServiceImpl` builds one shared mutable `HttpRequestWithBody` in its constructor and mutates it per call. Safe today only because D11's dispatcher drains sequentially on a single thread. **Raising dispatcher concurrency or instance count makes it live.** |
| **D27 — three silent no-op patch fields** | `paymentType` and `shipper` patches are discarded by the same `updatable = false` mapping that made D5 a no-op. Ruled PRESERVE; owner action recorded. |
| **D23** | Still open **in the monolith**. Verified **not** to reproduce in orders-service: five classes report `Tests run: 0` for the outer class, but declared and executed counts match in every one. |
| **D24, D25, D26** | Unchanged from Phase 2. **D26 remains the security-relevant one** — the untrusted-token message interpolates the whole bearer token; nothing emits it today, but the first handler that logs `getMessage()` leaks a live credential. |
| **Inert validation properties** | §5, closing paragraph. |
| **Repository hygiene** | 830 files under `target/` are tracked from earlier phases, and `.gitignore` contains only `*.log`. Left alone deliberately — changing it is an owner decision, not extraction work. |

### Inherited items from Phase 2 that this phase **closed**

| Item | How |
|---|---|
| The two inbound foreign keys | `FK_ORDER_CUSTOM_ID` and `FK_ORDER_SPERSON` cut. |
| `Order.salesperson`'s unguarded cascade write into `salespeople` | **Closed structurally, not by adding a check** — exactly as Phase 2 predicted. The association is now a plain `salesperson_id_number` column, so the `PERSIST`/`MERGE` cascades are gone with it. |
| `SalespersonLookupContract.describeSalesperson(Salesperson)` | Now takes a `String` id number, as Phase 2 required. |
| D18, before the adapter was ported | Fixed in step 3.1, as its own commit. |
| D21 review trigger | Actioned at step 3.0 and again at 2.3. |

---

## 9. Known CVE position — unchanged and surfaced, not buried

**QueryDSL 5.0.0 — CVE-2024-49203 (HIGH). Still no fixed release**, including 5.1.0.
Inherited from the monolith, not introduced. Re-checked at the start of Phase 3 as register
item D21 required.

Orders was predicted in Phase 1 to be the higher-risk consumer, and it is: the largest
search surface in the system. The accepted mitigation is that `orderBy` only ever receives
an `OrderSpecifier` drawn from a **closed** map — verified by reading all four of this
service's sort maps, and now **asserted by `OrdersSortSpecTest`** rather than argued.

Also unchanged and still split between modules: the monolith ships
`mariadb-java-client 3.3.3` and `commons-lang3 3.17.0`, both with CVEs fixed in the
versions the three extracted services pin (3.3.5 / 3.18.0). Deliberately **not** bumped
inside an extraction phase — 460 tests of regression risk for no Phase 3 benefit — and
recorded so the position is not silently inconsistent.

The two dependencies this phase added were CVE-scanned clean: `transbank-sdk-java 3.0.2`
and `unirest-java` / `unirest-mocks 3.13.8`.

---

## 10. Phase 3 is complete

Stage 3A (six register fixes inside the monolith), step 3.6 (20 porting-depth specification
documents covering the final 104 rules), and Stage 3B (the orders-service extraction, its
192 tests, and this record) are all done.

**Nine of twelve exit criteria now PASS.** X10 is partial only because the monolith retains
its own configuration until it is decommissioned; X12 is Phase 4 by design.

Next: **Phase 4** — decommission the monolith and retire the shared schema.
