# Phase 2 Exit Record — Identity extraction

**Phase:** 2 of 4 · **Status:** complete · **Reactor:** 5 modules, 4 of them building code
**Verification command:** `. ~/opt/env.sh && mvn -B -fae clean verify` at the repository root
**Raw output:** `test-baseline-post-phase2.txt`

---

## 1. Result

| Module | Outcome | Tests |
|---|---|---|
| Trébol Platform | SUCCESS | aggregator, no code |
| Trébol Shared Contracts | SUCCESS | no tests (generated catalogue only) |
| Trébol Backend Monolith | **FAILURE** | `Tests run: 420, Failures: 1, Errors: 2` |
| Trébol Catalogue Service | SUCCESS | `Tests run: 130, Failures: 0, Errors: 0` |
| **Trébol Identity Service** | **SUCCESS** | **`Tests run: 124, Failures: 0, Errors: 0`** |

**The monolith's three failures are the three known environmental ones** — all in
`WebpayplusPaymentServiceImplTest`, all caused by the unmocked live Transbank sandbox
(register item **D18**):

```
WebpayplusPaymentServiceImplTest$TestMode.fetches_payment_result
WebpayplusPaymentServiceImplTest$TestMode.fetches_payment_page_for_started_transactions
WebpayplusPaymentServiceImplTest$ProductionModeWithoutConfig.will_not_fetch_transaction_result
```

**Pass condition met: no new failure anywhere beyond those three.** The monolith's count
rose from 414 to **420** — the six tests Phase 2 added inside it (five for the two HS-02
contracts, one for transactional registration). Nothing was removed.

---

## 2. What moved

### 2.1 Landed inside the monolith first (invariant G6)

| Change | Detail |
|---|---|
| **HS-02 closed** | `OrdersConverterServiceImpl` no longer calls `CustomersCrudService`, `CustomersConverterService` or `SalespeopleConverterService`. It depends on two published contracts instead: `CustomerProvisioningContract` (`resolveOrCreateCustomer`, `describeCustomer` — read **and** write) and `SalespersonLookupContract` (`describeSalesperson` — read only). Both implementations are pure delegation, so behaviour is unchanged (G1) and the literals are untouched (G2). |
| **Why Gate A missed it** | Gate A's verification grep covers `import org.trebol.jpa.repositories` only. This leak was at **service** level, so it passed the gate. Recorded in the register. |
| **Registration is transactional** | `@Transactional(rollbackFor = {BadInputException.class, EntityExistsException.class})` on `RegistrationServiceImpl.register`, proven by `RegistrationServiceImplTransactionalTest` — a real rollback probe, not an annotation assertion. Closes **X8** with ADR-001. |

### 2.2 The new module

`trebol-services/identity-service` — `org.trebolecommerce:trebol-identity-service:0.3.0-SNAPSHOT`

| | |
|---|---|
| Main sources | **131** files, all keeping their exact fully-qualified `org.trebol.*` names |
| Test sources | **54** files — 52 ported verbatim, plus `IdentityRouteParityTest` and `IdentityApplicationTest` |
| Owned tables | **7** — `people`, `app_user_roles`, `app_permissions`, `app_users`, `app_user_role_permissions`, `customers`, `salespeople` |
| Route surfaces | `/data/users`, `/data/user_roles`, `/data/people`, `/data/customers`, `/data/salespeople`, `/account/profile`, `/access`, `/access/{apiRoute}`, `/public/register`, plus `/public/login` and `/public/guest` **as filters** |
| Published contracts | `CustomerProvisioningContract`, `SalespersonLookupContract` — what Orders will consume from Phase 3 |

**It is the only service that issues tokens.** It owns the login and guest filters, the
`UserDetailsService`, the `DaoAuthenticationProvider`, the `AuthenticationManager` and the
bcrypt `PasswordEncoder`. Catalogue verifies locally against the same shared key and owns
none of that — per `ADR-002`.

### 2.3 Porting-depth specifications

| Domain | Files | Rules | Coverage |
|---|---|---|---|
| `domain-user` | 5 documents | all 55 `BR-USER-*` | 55/55 source files |
| `domain-customer` | 5 documents | all 22 `BR-CUSTOMER-*` | 13/13 source files |
| `domain-salesperson` | 5 documents | all 23 `BR-SALESPERSON-*` | 11/11 source files |

Rule coverage verified by script: every one of the 100 ids appears in its domain's spec.

---

## 3. Gate verification — run, not assumed

```sh
# Gate A — only the order domain's OWN repositories may appear
grep -rn "import org.trebol.jpa.repositories" \
  trebol-backend-monolith/src/main/java/org/trebol/api/services/ | grep -iE "Orders|Checkout"
# -> OrdersProcessServiceImpl:33-35 (OrdersRepository, OrderDetailsRepository,
#    OrderStatusesRepository) and ReceiptServiceImpl:33 (OrdersRepository). PASS.

# Gate B — must return 0, in every module
grep -rhoE "hasAuthority\('[a-zA-Z:_-]*'\)" trebol-backend-monolith/src/main/java --include=*.java | wc -l   # 0
grep -rhoE "hasAuthority\('[a-zA-Z:_-]*'\)" trebol-services --include=*.java | wc -l                        # 0

# HS-02 — new check, must return nothing
grep -rnE "Customers(CrudService|ConverterService)|Salespeople(CrudService|ConverterService)" \
  trebol-backend-monolith/src/main/java/org/trebol/jpa/services/conversion/impl/OrdersConverterServiceImpl.java
# -> nothing. PASS.

# Identity schema — exactly 7 tables, no FK leaving the set
grep -c "CREATE TABLE" trebol-services/identity-service/src/main/resources/schema-mariadb.sql   # 7
grep -oE "REFERENCES \`[a-z_]+\`" .../schema-mariadb.sql | sort -u
# -> app_permissions, app_user_roles, people. All inside the set. PASS.

# G7 / X10 — no secret in any identity main resource
grep -nriE "password=[^$]|secret-key=[^$]|\$2a\$" trebol-services/identity-service/src/main/resources/
# -> nothing. PASS.
```

`ADR-001`, `ADR-002`, `ADR-003` and `defect-register-rulings.md` are present and readable
(Gates C and D).

---

## 4. Secrets and the bootstrap-account decision

**Identity's committed configuration contains no secret.** Every sensitive value is an
environment placeholder with **no default**:

```
trebol.security.jwt-secret-key=${TREBOL_JWT_SECRET_KEY}
spring.datasource.url=${TREBOL_IDENTITY_DB_URL}
spring.datasource.username=${TREBOL_IDENTITY_DB_USER}
spring.datasource.password=${TREBOL_IDENTITY_DB_PASSWORD}
trebol.cors.allowed-origins=${TREBOL_CORS_ALLOWED_ORIGINS}
```

Only `server.port` has a default (`${TREBOL_IDENTITY_PORT:8082}`). A missing signing key
cannot boot silently: `SecurityProperties` is `@Validated` with `@NotBlank jwtSecretKey`,
and `Keys.hmacShaKeyFor` additionally rejects anything under 256 bits.

**This matters more here than in Catalogue.** Identity *signs*; a committed key here would
mint valid credentials for every service, not merely verify them.

### 4.1 The decision, as approved

The monolith seeds four accounts — `admin`, `manager`, `salesperson`, `customer` — whose
password equals the username (`BR-USER-055`). **They are deliberately not carried into
identity-service's committed seed.**

| | |
|---|---|
| `src/main/resources/data.sql` | 4 roles, 54 permissions, 100 grants, and the two identity-generator restarts. **No `app_users` row, no `user_id` restart.** |
| `src/test/resources/data-test.sql` | the same, **plus** the four accounts with the monolith's bcrypt hashes, so every ported test runs unchanged (G3, G8) |
| New runtime code | **none** — no bootstrap component, no conditional seeding |

### 4.2 Operator step — required before first use

**A freshly migrated Identity database has roles and authorities but no accounts, so
nothing can authenticate.** The first administrator must be created out of band:

1. Provision the schema (`schema-mariadb.sql`) and the seed (`data.sql`). Confirm
   `app_user_roles` has 4 rows, `app_permissions` 54, `app_user_role_permissions` 100.
2. Generate a BCrypt hash of a password you control, at cost **10** to match
   `trebol.security.bcrypt-encoder-strength`.
3. Insert the person and the account in one transaction, against role id **1**
   (`Administrator`, which holds all 54 authorities):

```sql
INSERT INTO `people` (`person_first_name`, `person_last_name`, `person_id_number`,
                      `person_email`, `person_phone1`, `person_phone2`)
VALUES ('<first>', '<last>', '<id number>', '<email>', '', '');

INSERT INTO `app_users` (`user_name`, `user_password`, `person_id`, `user_role_id`)
VALUES ('<username>', '<bcrypt hash>', LAST_INSERT_ID(), 1);
```

4. Verify with `POST /public/login`, then **change the password out of band** — there is no
   password-change route anywhere in the system (register item **D15**, ruled PRESERVE and
   out of scope).

Note `protected-account-id=1` refers to `user_id` 1, so whichever account lands first is
the one the delete guard protects.

---

## 5. Exit criteria — movement in this phase

| # | Before Phase 2 | After Phase 2 |
|---|---|---|
| X1 three services deploy independently | PARTIAL (1 of 3) | **PARTIAL (2 of 3)** — Orders pending |
| X2 all 22 tables assigned | PARTIAL (7/22) | **PARTIAL (14/22)** — Orders owns the remaining 8 |
| X3 all 67 routes served | PARTIAL | **PARTIAL** — identity + catalogue surfaces now served by services |
| X4 all 327 rules preserved or ruled | PARTIAL | **PARTIAL** — 100 more rules now at porting depth; `order` and `billing` remain |
| X5 every baseline test in its owning service | PARTIAL | **PARTIAL** — 124 identity tests green in the owning module |
| X6 no authority literals, generated catalogue | PASS | **PASS** — holds across all four modules |
| X7 no cross-domain repository imports | PASS | **PASS, strengthened** — HS-02's service-level leak is closed too |
| **X8 tested consistency model** | PARTIAL | **PASS** — ADR-001 plus a real rollback test |
| X10 no secret in committed configuration | PARTIAL | **PARTIAL** — satisfied for both extracted services; the monolith still holds its own |
| X11 every ruling implemented and tested | PARTIAL | **PARTIAL** — D3, D4, D5, D11, D18 are Phase 3 |
| X9 payment callbacks authenticated | FAIL | **FAIL, untouched by design** — Phase 3 |
| X12 monolith decommissioned | FAIL | **FAIL, untouched by design** — Phase 4 |

---

## 6. What is still in the monolith, deliberately

**Everything.** The monolith still compiles every identity, customer, salesperson and
person class, still serves all 21 controllers and all 67 routes, and still owns the shared
22-table schema. Per the definition's own phasing, migrated code is removed only at
**Phase 4**, which means:

> Until Phase 4 completes, identity-service is a **verified copy**, not a completed
> extraction — exactly as catalogue-service is today.

Nothing was deleted from the monolith in this phase.

---

## 7. What Phase 3 inherits

| Item | Detail |
|---|---|
| **The two inbound foreign keys** | `FK_ORDER_CUSTOM_ID` (`orders` → `customers`) and `FK_ORDER_SPERSON` (`orders` → `salespeople`). Both still exist in the monolith's schema and are cut when Orders is extracted. |
| **The two order-side associations** | `Order.customer` (`@ManyToOne`, mandatory) and `Order.salesperson` (`@ManyToOne`, nullable, **with cascade**). Out of Phase 2 scope by the definition's phasing. |
| **A cross-service write inside checkout** | `CustomerProvisioningContract.resolveOrCreateCustomer` is a resolve-**or-create**. In-process today; a remote write across the boundary tomorrow. The consistency implication is the one already recorded for `BillingContract.resolveOrCreateBillingCompany` in ADR-001. |
| **An unguarded write path into `salespeople`** | `Order.salesperson`'s cascade can create or modify a salesperson row with **no `salespeople:*` authority check**, reachable from public checkout (BR-SALESPERSON-016). Harmless today only because no route ever sets an order's salesperson (BR-SALESPERSON-015). **Cutting the association is what closes it** — not adding a check. |
| **A contract signature to revisit** | `SalespersonLookupContract.describeSalesperson` takes a `Salesperson` **entity**, which only works while both sides share a process. It must take an identifier once Orders is separate. Recorded rather than pre-emptively redesigned, to avoid changing the call site twice. |
| **D18, before the payment adapter is ported** | The three known failures are the unmocked Transbank sandbox. The ruling is to rewrite the test to mock the SDK **as its own reviewed change, before Phase 3**. |
| **D21 review trigger** | Re-check QueryDSL CVE-2024-49203 at the start of Phase 3. Orders is the higher-risk consumer — the largest search surface in the system. |
| **D23** | `OrdersConverterServiceImplTest`'s six outer-class tests never execute (`Tests run: 0`), pre-existing and present in the original baseline. The order domain's `convertToPojo` read path is less covered than the per-class baseline suggests. Settle with D18. |

---

## 8. New register items from this phase

| # | Finding | Ruling |
|---|---|---|
| **D23** | `OrdersConverterServiceImplTest`'s outer-class tests never run | PRESERVE; owner to enable as its own change |
| **D24** | `cascade = ALL` on `Customer.person` and `Salesperson.person` contradicts BR-CUSTOMER-014 and BR-SALESPERSON-018 | PRESERVE the code; the recorded rules are wrong |
| **D25** | `salespeople.person_id` is non-unique but queried through an `Optional` | PRESERVE; do not constrain the column |
| **D26** | `ClaimsAuthorizationHeaderParserServiceImpl` interpolates the **whole bearer token** into an exception message | PRESERVE in Phase 2; redact as its own reviewed change |

**D26 deserves the owner's attention.** Nothing emits that message today — verified, not
assumed: `JwtTokenVerifierFilter` logs only the request method and URI (the Phase 0 D2
fix), and `AccessController`'s `@ExceptionHandler` body is deliberately empty. But the
message is constructed on every verification failure, and the first handler that logs
`exc.getMessage()` leaks a live credential. It is the same defect class as D2, one layer
deeper.

Plus two specification-versus-code contradictions requiring the owner's correction:
**BR-CUSTOMER-014** and **BR-SALESPERSON-018** (see D24), and the two recorded deliberate
deviations from the baseline: **BR-USER-012** (registration is now transactional) and
**BR-USER-055** (bootstrap credentials are no longer committed).

---

## 9. Known CVE position, unchanged and surfaced

| Dependency | Version | Status |
|---|---|---|
| `com.querydsl:querydsl-jpa` / `querydsl-apt` | 5.0.0 | **CVE-2024-49203 (HIGH)**, HQL injection via `orderBy`. **No CVE-free release exists**, including 5.1.0. Inherited, not introduced — the monolith and catalogue already ship it, and G5 forbids a downgrade. Register item **D21**. Mitigation verified for Identity: all five sort specs are closed whitelists, so `orderBy` never receives a raw client string. |
| `org.mariadb.jdbc:mariadb-java-client` | 3.3.5 in both services | Upgrade over the parent-managed 3.3.3 (CVE-2026-55856/55857/55858, CVE-2026-61700) |
| `org.apache.commons:commons-lang3` | 3.18.0 in both services | Upgrade over the monolith's 3.17.0 (CVE-2025-48924) |

**The monolith still ships the vulnerable `mariadb-java-client` 3.3.3 and
`commons-lang3` 3.17.0.** Recorded in Phase 1 as its own reviewed change and explicitly
not folded into Phase 2.
