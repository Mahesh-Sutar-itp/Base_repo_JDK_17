# Defect register — rulings

**Status:** accepted · **Reference:** transformation definition §6 · **Phases:** 0, 1, 2, 3

Every item below is **current observable behaviour**. Porting silently either
preserves a defect or breaks a client contract, so each item carries an explicit
ruling (guardrail G1 — silent fixes are forbidden).

Items D19 and D20 are **new**, found while establishing the Phase 0 baseline; D21
and D22 were found during Phase 1, and D23 to D26 during Phase 2. All are registered here
rather than folded quietly into an existing fix.

## Summary

| Ruling | Items |
|---|---|
| **FIX — before extraction (Phase 0)** | D1, D2, D6, D7, D9, D14, D17 |
| **FIX — at the owning phase** | D3, D4, D5, D11 (Phase 3), D18 (before Phase 3), D20 (Phase 1, first extraction), transactional registration (Phase 2) |
| **PRESERVE** | D8, D16, D19 (scope), D23, D24, D25, D26, the two D14 remainders, and BR-USER-032/033 route-discovery substring matching |
| **PRESERVE with recorded limitation / deferred** | D10, D12, D13, D15, D21 (inherited CVE, mitigation verified), D22 |

### Intentional observable-behaviour changes landing in Phase 0

**D1, D6, D7 and D14 change what a client sees.** Called out explicitly, as
required. Where an existing test asserts the defective behaviour, the test is
updated and the update is named in the commit message — never adjusted quietly.

| # | Before | After |
|---|---|---|
| D1 | `DELETE` with only unrecognised parameters deletes **every row in the table**, HTTP 204 | HTTP 400 `BadInputException`, nothing deleted |
| D6 | Profile update on an account that already has a profile **always** throws `UnsupportedOperationException` → HTTP 500 | Profile is patched and saved → HTTP 204 |
| D7 | `code` or `url` duplicate on image create → `DataIntegrityViolationException` → HTTP 500 | HTTP 409, consistent with the `filename` duplicate path |
| D14 | Guest login persists a `person` + `customer` row **before** authenticating, so a failed guest login can still create rows | Authentication happens first; no row is created when authentication fails |

---

## Phase 0 — fix before extraction

### D1 — a mistyped `DELETE` parameter deletes an entire table
**Rule:** BR-IMAGE-013 · **Severity:** highest in the system · **Ruling: FIX**

`DataCrudGenericController.delete` guards only that the request-parameter map is
non-empty. Every `PredicateService` implementation has `default: break;` for an
unrecognised parameter and returns the `BooleanBuilder` it built — which, with no
recognised selector, is a **match-everything predicate**. Affects **every
write-capable domain**, not just `image`.

Fix: after building the predicate, reject the request when no selector was
recognised (`BooleanBuilder.hasValue()` is false) with `BadInputException`, before
`crudService.delete` is reached. New message literal introduced — a new message,
not a changed one, so no existing contract literal moves (G2).

### D2 — malformed-token handling logs the full token value
**Rule:** BR-USER-020 · **Ruling: FIX**

A credential leak. Must not be replicated into three services' logs (ADR-002
item 8). The token value is removed from the log arguments; the request method and
URI are retained. **No client-observable change** — the response is already an
empty-bodied unauthorized.

### D6 — profile update always fails when a profile already exists
**Rule:** BR-PERSON-027 · **Ruling: FIX**

`ProfileServiceImpl.updateProfileForUserWithName` calls the
`@Deprecated(forRemoval = true)` `patchExistingEntity(PersonPojo, Person)`
overload, whose implementation body is an unconditional
`throw new UnsupportedOperationException`. The `Map<String, Object>` overload is
fully implemented and works. A **live, totally broken user-facing flow**.

Fix: call the working `Map` overload, building the map from non-null `PersonPojo`
fields only — so an omitted field is not wiped. `ObjectMapper.convertValue` is
deliberately **not** used, because it emits explicit nulls and the map
implementation assigns `phone1`/`phone2` unconditionally. The deprecated overload
is **not** resurrected.

### D7 — duplicate detection covers 1 of 3 unique columns
**Rule:** BR-IMAGE-002 · **Ruling: FIX**

`Image` declares `code`, `filename` and `url` all `unique = true`, but
`ImagesCrudServiceImpl.getExisting` only queries by `filename`. Collisions on the
other two escape the `EntityExistsException` → 409 path and surface as 500s under
normal use.

Fix: extend `getExisting` to try id, then filename, then code, then url, blank-safe,
first match wins.

### D9 — payment session identifier is a Java hash code
**Rule:** BR-ORDER-034 · **Ruling: FIX**

`String.valueOf(transaction.hashCode())` is not stable across processes and is not
unique. It breaks outright with more than one instance — and a split means more
instances. The value is not persisted anywhere today, so no schema change is
needed.

Fix: a `UUID`-derived identifier, within Webpay's session-id length limit.

### D14 — guest login writes business rows from inside an authentication filter
**Rules:** BR-USER-026, HS-02 · **Ruling: FIX (partial in Phase 0)**

`JwtGuestAuthenticationFilter.attemptAuthentication` parses the request body with
an ad-hoc `new ObjectMapper()`, persists a real `person` and `customer` row, and
only **then** authenticates. The configured guest name is used as **both username
and password**.

Phase 0 fix - scope recorded honestly:
- authenticate **first**, provision **after**, so a failed guest login creates no
  rows;
- move the write out of the filter into a `@Transactional` application service
  (`GuestCustomerProvisioningService`), preserving the existing
  `EntityExistsException`-swallowing semantics and the log message literal;
- inject the Spring `ObjectMapper` bean instead of constructing one per request.

**Not** fixed in Phase 0, and deliberately so:
- guest-name-as-password;
- the endpoint being a filter at all - the definition requires `/public/guest` to be
  **ported as a filter** in Phase 2, so removing the filter is out of scope here;
- bean validation of the submitted profile. Adding `@Validated` would raise
  `ConstraintViolationException`, which this filter does not catch, turning a
  rejected guest profile from HTTP 401 into HTTP 500 - a behavioural regression. The
  existing `BadInputException` path already rejects insufficient data. Revisit in
  Phase 2 together with the filter port.

### D17 — no uniqueness constraint on the role-permission pair
**Source:** user domain overview · **Ruling: FIX**

`app_user_role_permissions` has no unique constraint on
`(user_role_id, permission_id)`, so the same authority can be granted to a role
twice; de-duplication happens in memory at login. Fix: a `@UniqueConstraint` on
the entity — which is what actually takes effect, since runtime DDL is generated
from annotations — mirrored into `schema-mariadb.sql` and the extracted global
schema for parity. Verified safe: all 100 seeded grant pairs are distinct. The
in-memory de-duplication at login is left untouched.

---

## Fix at the owning phase

### D3 — payment commit failure recorded as a decline
**Rule:** BR-ORDER-038 · **Ruling: FIX in Phase 3**

An infrastructure failure (SDK exception) is swallowed and returned as a non-zero
response code, i.e. indistinguishable from a genuine decline. These need different
operator responses. Blocked on D18 — the adapter cannot be changed safely without
real unit coverage.

### D4 — payment callbacks are public, unauthenticated and non-idempotent
**Rules:** BR-ORDER-041, BR-ORDER-043 · **Ruling: FIX in Phase 3**

No signature verification, no provider-origin check, no idempotency key. Replay
protection is incidental: the lookup filters on status `Payment Started`. **The
largest security gap in the system**, and it drives state transitions on real
orders. Exit criterion X9 depends on this. Fix: verify provider origin, require an
idempotency key, make the transition idempotent by order + token.

### D5 — `PATCH /data/orders` bypasses the order state machine
**Rule:** BR-ORDER-028 · **Ruling: FIX (restrict) in Phase 3**

A `status` key moves an order to **any** status with no transition validation. It
invalidates the state machine the Orders service is built around. Fix: route status
changes through the validated transition path; reject invalid transitions. This is
a deliberate contract narrowing for operator clients and is called out as such.

### D11 — mail sent after commit, synchronously, with no retry
**Rule:** BR-ORDER-055 · **Ruling: FIX via outbox in Phase 3**

A mail failure surfaces as a 500 with the status **already changed** and the mail
lost. Fix: transactional outbox. Noted risk: **no event infrastructure exists
anywhere in the codebase**, so this is built from nothing. Fallback, if the outbox
is descoped: keep mail synchronous, record the ruling as PRESERVE and make the
failure non-fatal to the status transition.

### D18 — the payment adapter has no genuine unit-test coverage
**Ruling: FIX as its own reviewed change, before Phase 3**

`WebpayplusPaymentServiceImplTest` mocks only `WebpayplusPaymentProperties`, not
the Transbank SDK, so it makes a **real HTTP call** to the sandbox and fails with
HTTP 403 off-network. It is an integration test presented as a unit test.
Consequence: the most critical and least safely changeable component in the system
is uncovered — and Phase 3 must port it with D3, D4 and D9 applied.

Ruling: rewrite it to mock the SDK, as a separate reviewed commit, **not** folded
into a refactor. G3 permits modifying tests only for compatibility, so this
improvement needs this explicit ruling. It also accounts for all three known
baseline failures (`Tests run: 363, Failures: 1, Errors: 2`), which are
environmental, not code defects.

### D20 (new) — secrets in committed configuration
**Ruling: FIX at the first extraction (Phase 1)**

The baseline commits the JWT signing key, database credentials and bootstrap
passwords in configuration files. Guardrail G7 and exit criterion X10 forbid
replicating that into three services. Ruling: externalise configuration at the
first extraction, not at the end. The monolith's own committed values are left
untouched in Phase 0 — rotating them is an operational task, not a code change,
and doing it here would change deployment behaviour outside any gate.

---

## Preserve

### D8 — `transportValue` never computed, excluded from the payment amount
**Rules:** BR-ORDER-016, BR-ORDER-035 · **Ruling: PRESERVE**

Never computed or set, and excluded from the total sent to the payment provider.
Today it is **consistently zero**, so the behaviour is self-consistent and clients
depend on the current totals. If shipping is ever charged this becomes a revenue
defect — but implementing shipping rates is explicitly a **non-goal** (new product
work). The field stays, the behaviour stays, and the gap is recorded.

### D16 — order totals extract 19% tax from a tax-inclusive value, truncating
**Source:** W-ORDER-02 · **Ruling: PRESERVE EXACTLY**

Monetary arithmetic. Any change — including "correcting" the rounding — alters
recorded financial data and breaks reconciliation against orders already stored.
The truncation is part of the contract. Port the arithmetic character for
character.

### D19 (new) — the D1 match-all predicate also reaches `update` and `partialUpdate`
**Ruling: PRESERVE in Phase 0; FIX candidate for the owning phase**

`DataCrudGenericController.update` and `partialUpdate` build their predicate the
same way as `delete`, so an unrecognised-only parameter map is also a match-all
there. The register scopes D1 to `DELETE`, and a mass **update** is less
destructive than a mass **delete** but still a defect.

Ruling: fix `DELETE` only in Phase 0, strictly within the register's scope, and
register this exposure rather than silently widening the fix (G1). Recommendation
for the owning phase: apply the same recognised-selector guard to both.

### BR-USER-032 / BR-USER-033 — route-discovery substring matching
**Ruling: PRESERVE in Phase 0; revisit in Phase 2**

The route-discovery feature derives a route by stripping everything from the colon
onward and matches actions by **substring**, so asking about `product` also returns
`product_lists:*` and `product_categories:*` actions. The definition flags this
under Gate B as "also fix here", but it is **not** one of the seven
fix-before-extraction items, and `AuthorizedApiServiceImplTest` asserts the current
behaviour — which means clients may depend on it.

Ruling: preserve in Phase 0 to keep the gate scope tight and G1 intact. Revisit
with Identity in Phase 2, where the authority catalogue makes an exact-match
implementation cheap and testable.

---

## Preserve with recorded limitation

### D10 — four order statuses seeded but reached by no code
**Source:** order workflows · **Ruling: PRESERVE the seed data; do not implement**

`Delivery On Route`, `Delivery Failed`, `Delivery Cancelled` and `Returned` are
seeded and unreachable. Implementing delivery tracking and returns is **new product
work** (non-goal). Dropping the rows would change seeded reference data that
operator clients may already display.

Ruling: keep the four rows, and in Phase 3 mark them explicitly as unreachable in
the state machine so they are **not ported silently as if live**. The state machine
must reject transitions into them until the capability exists.

### D12 — `images:create` not checked on the internal image write path
**Rule:** BR-IMAGE-024 · **Ruling: PRESERVE, deferred**

`ProductsCrudServiceImpl.makeProductImageRelationships` creates `images` rows
through the product's association cascade, so product authority implicitly grants
image creation. Both domains land in **Catalogue**, so this never becomes a
cross-service authorization hole. Deferred to Phase 1 as an internal
consistency question, not a gate.

### D13 — a blank image filename inside a product payload is silently dropped
**Rule:** BR-IMAGE-024 · **Ruling: PRESERVE in Phase 0; define the contract in Phase 1**

The product succeeds with fewer images and the client is never told. Partial
success needs a defined contract, but changing it is a client-visible contract
change and belongs with the Catalogue port, where the product payload contract is
being specified at porting depth anyway.

### D15 — no password change or reset; all account-status flags hardcoded permissive
**Rules:** BR-USER-045, BR-USER-023 · **Ruling: PRESERVE — out of scope, flagged**

No disabled, locked or expired state and no lockout exist. This is **not**
conversion work — it is missing product capability — so it is not built here.
Flagged prominently: an Identity service shipped without password reset is a hard
sell, and the hardcoded permissive flags mean there is no mechanism to disable a
compromised account. Recommended as the first post-conversion backlog item for
Identity.

---

## Phase 1 additions and resolutions (Catalogue extraction)

### D12 — `images:create` not checked on the internal image write path
**Phase 1 resolution: PRESERVE. The contract is now specified, not changed.**

Phase 0 deferred this to Phase 1 "as an internal consistency question, not a gate".
Phase 1 ruling: the behaviour ports **unchanged**. A principal holding only
`products:create` or `products:update` can still insert `images` rows as a side
effect of a product write, because `makeProductImageRelationships` builds a
transient `Image` inline instead of calling `ImagesCrudService.create`.

What changed is that the hole is now **documented where it is implemented** rather
than inferred: `detailed-business-specification/domain-product/permissions.md` §3
states it explicitly, and `02-product-services.md` §6 records the exact step
sequence and what each step bypasses. Fixing it alters the observable outcome of a
product write — a payload that currently succeeds with 201 would begin failing with
403 — so it is a client-visible contract change requiring owner approval, and it is
**not** folded into an extraction. Recommended as a post-conversion backlog item
for Catalogue.

Note the Phase-0 **D7** fix narrowed the blast radius: `getExisting` now resolves by
filename, then `code`, then `url`, so a payload reusing an existing code or url
attaches the existing image instead of inserting a colliding row. The
create-by-side-effect path itself is untouched.

### D13 — a blank image filename inside a product payload is silently dropped
**Phase 1 resolution: PRESERVE. The contract is now defined in writing.**

Phase 0 deferred "define the contract in Phase 1". The defined contract, as ported,
is: **the product write succeeds and the unresolvable image is dropped without any
client-visible signal.** `getExisting` throws `BadInputException("Invalid filename")`,
`makeProductImageRelationships` catches it, logs at **DEBUG** — below the default
level, so production leaves no trace — and the request returns 201 or 204.

This is recorded as `domain-product/errors.md` E12 and `00-domain-index.md` W2, and
is called out there as the single most important non-error in the domain: *the image
subsystem fails open*. Changing it to a 400, or to a partial-success response body,
is a client-visible contract change and is listed as a post-conversion backlog item
rather than being introduced during the port (invariant G1).

### D21 (new) — QueryDSL 5.0.0 carries CVE-2024-49203 with no fixed release
**Ruling: ACCEPT as inherited, with verified mitigation. Re-review each phase.**

`querydsl-jpa` and `querydsl-apt` 5.0.0 carry **CVE-2024-49203 (HIGH)** — HQL
injection through `orderBy` — and the vulnerability database reports **no CVE-free
release**, including 5.1.0. The monolith already ships 5.0.0 (the version is managed
by the Spring Boot parent), so the Catalogue service **inherits** rather than
introduces it. Downgrading is forbidden (G5) and there is nothing to upgrade to.

**Mitigation, verified rather than assumed.** Every sorted read resolves its
`OrderSpecifier` through `SortSpecParserService` against a static whitelist map, so
`orderBy` never receives a raw client string. The whitelists were read and are
closed sets:

| Sort spec | Accepted keys |
|---|---|
| `ProductsSortSpec` | `name`, `barcode`, `price`, `category` |
| `ProductCategoriesSortSpec` | `name`, `code` |
| `ProductListsSortSpec` | `name`, `code`, `totalCount` |
| `ProductListItemsSortSpec` | `name`, `barcode` |
| `ImagesSortSpec` | as declared in that domain's spec |

A `sortBy` value outside the map is rejected by the parser service before any
QueryDSL call. The catalogue-service pom declares `querydsl.version` **explicitly**
at 5.0.0 with this finding recorded inline, rather than inheriting the property
silently — the pre-Gate-A scaffold referenced the property without declaring it at
all.

**Review trigger:** re-check for a fixed release at the start of Phase 2 and Phase
3. Orders is the higher-risk consumer, since its search surface is larger.

### D22 (new) — `CompanyDetailsPojo` contradicts BR-COMPANY-006
**Ruling: PRESERVE the code. The recorded rule is the thing that is wrong.**

`CompanyDetailsPojo` is annotated `@JsonInclude(ALWAYS)`, so an unset company-profile
field is serialised as an explicit `null` key. **BR-COMPANY-006 asserts the opposite**
— that unset fields are omitted from the response.

The code is authoritative for the port, because the emitted JSON shape is
client-observable contract (G2) and a storefront may already distinguish "present and
null" from "absent". The annotation is therefore carried over verbatim into the
Catalogue service, and the discrepancy is recorded in
`domain-product/00-domain-index.md` §6.3 and `errors.md` §6.

Found while writing the porting-depth specification, which is exactly what that
exercise is for. **Action required of the owner:** correct BR-COMPANY-006 in
`business-rules-extraction/detailed-domain-bre/domain-company-profile/business-rules.md`,
or rule that the response shape should change — which would be a client-visible
change needing its own ruling.

### Follow-up recorded, not actioned — the monolith's own dependency versions
The Catalogue service pins `mariadb-java-client` to **3.3.5** (the parent-managed
3.3.3 carries CVE-2026-55856, CVE-2026-55857, CVE-2026-55858 and CVE-2026-61700, all
fixed in 3.3.5) and `commons-lang3` to **3.18.0** (3.17.0 carries CVE-2025-48924).
Both are upgrades; no version is downgraded anywhere (G5).

The **monolith still ships the vulnerable versions.** Bumping them is a change to the
monolith's own dependency tree with its own regression risk across 414 tests, and is
out of Phase 1 scope — but it is recorded here so the CVE position is not silently
split between two modules. It should be actioned as its own reviewed change, not
folded into Phase 2.

---

## Phase 2 additions and resolutions (Identity extraction)

### HS-02 — resolved inside the monolith, before any extraction (G6)
**Coupling hotspot, not a defect · Status: CLOSED in the monolith**

Gate A's verification grep covers `import org.trebol.jpa.repositories` only, so it
never saw this: `OrdersConverterServiceImpl` reached the identity domain at
**service** level, not repository level — `CustomersCrudService`,
`CustomersConverterService` and `SalespeopleConverterService` — and one of those
calls is the identity kernel's **outward write** that HS-02 names
(`getExisting(...)`, else convert a new `Customer` that is cascaded on order save).

Closed by publishing two contracts from the order domain's point of view, mirroring
the Gate A pattern:

| Contract | Methods | Direction |
|---|---|---|
| `CustomerProvisioningContract` | `resolveOrCreateCustomer`, `describeCustomer` | read **and write** |
| `SalespersonLookupContract` | `describeSalesperson` | read only |

Both implementations are pure delegation to the services that were previously
called directly, so the resolve-or-create semantics, the `BadInputException` paths
and their literal messages are unchanged (G1, G2). The consistency implication of
`resolveOrCreateCustomer` becoming a **remote write inside checkout** is the same one
already recorded for `BillingContract.resolveOrCreateBillingCompany` in ADR-001.

**Deliberately out of scope here:** the `Order.customer` and `Order.salesperson`
entity associations, and the two inbound foreign keys `FK_ORDER_CUSTOM_ID` and
`FK_ORDER_SPERSON`. Those are cut in Phase 3, by the definition's own phasing.

### Registration is now transactional — a recorded improvement, not a silent fix
**Rules:** BR-PERSON-013, BR-USER-012 · **Ruling: FIX in Phase 2 (ADR-001 §Decision.1)**

`RegistrationServiceImpl.register(...)` performs three writes — `people`,
`app_users`, `customers` — and had no `@Transactional`. A failure after the first
`saveAndFlush` left an **orphaned `people` row that no code path in this system can
delete**. All three writes are co-located in Identity, which is what makes a single
transaction possible.

`@Transactional(rollbackFor = {BadInputException.class, EntityExistsException.class})`
added, covered by `RegistrationServiceImplTransactionalTest` — a `@SpringBootTest`
that forces the third write to fail and asserts neither the person nor the user row
survives. It exercises the boundary rather than asserting the annotation is present.
This closes exit criterion **X8** together with ADR-001.

The pre-write duplicate checks and both `EntityExistsException` message literals are
untouched (G2). No existing assertion in `RegistrationServiceImplTest` asserted the
non-atomic intermediate state, so none needed adjusting — all 5 still pass unchanged.

### D14 remainder (a) — guest name used as both username and password
**Ruling: PRESERVE, flagged. Not conversion work.**

The configured guest name is also the stored credential that `DaoAuthenticationProvider`
matches, and it is **seeded**. Replacing it is a credential/product decision with a
client-visible effect — the guest checkout flow stops working for any client that
sends the documented value — not a conversion concern. Carried into identity-service
verbatim and recorded here so it is not mistaken for an oversight. First Identity
backlog item alongside D15.

### D14 remainder (b) — bean validation of the submitted guest profile
**Ruling: PRESERVE. Re-verified against the ported filter, as promised in Phase 0.**

The Phase 0 reasoning holds after the port: adding `@Validated` raises
`ConstraintViolationException`, which the filter does not catch, so a rejected guest
profile turns from **HTTP 401 into HTTP 500** — a behavioural regression (G1). The
existing `BadInputException` path already rejects insufficient data. `/public/guest`
remains a filter, as the definition requires.

### BR-USER-032 / BR-USER-033 — route-discovery substring matching
**Ruling: PRESERVE. Revisited in Phase 2 as promised; the answer is still preserve.**

Re-examined now that the authority catalogue exists and would make exact matching
cheap. It is still a **client-visible contract change**: `AuthorizedApiServiceImplTest`
asserts the current substring behaviour, so a client asking about `product` today
receives `product_lists:*` and `product_categories:*` actions too and may rely on it.
Changing it needs its own ruling and its own release note; it is not something to fold
into an extraction (G1, G2).

Recorded as a post-conversion backlog item. The implementation is a one-line change
once ruled: match the catalogue's `AuthorityCodes` entry exactly instead of by
`contains`, keeping the colon-stripping that derives the route.

### D21 — QueryDSL CVE-2024-49203 review trigger, actioned
**Re-checked at the start of Phase 2, as the Phase 1 ruling required. Result: unchanged.**

`querydsl-jpa` and `querydsl-apt` 5.0.0 still carry **CVE-2024-49203 (HIGH)** and the
vulnerability database still reports **no CVE-free release**. Downgrading is forbidden
(G5) and there is nothing to upgrade to, so the ruling stands: **ACCEPT as inherited.**

Mitigation verified for Identity rather than assumed — every identity sort spec is a
closed whitelist, read directly from source:

| Sort spec | Accepted keys |
|---|---|
| `UsersSortSpec` | `name`, `role` |
| `UserRolesSortSpec` | `name` |
| `PeopleSortSpec` | `name`, `firstName`, `lastName`, `idNumber`, `email`, `phone1`, `phone2` |
| `CustomersSortSpec` | `name`, `firstName`, `lastName`, `idNumber`, `email`, `phone1`, `phone2` |
| `SalespeopleSortSpec` | `name`, `firstName`, `lastName`, `idNumber`, `email`, `phone1`, `phone2` |

A `sortBy` value outside the map is rejected by `SortSpecParserService` before any
QueryDSL call, so `orderBy` never receives a raw client string. The identity-service
pom declares `querydsl.version` explicitly at 5.0.0 with this finding recorded inline.

**Review trigger:** re-check again at the start of Phase 3. Orders remains the
higher-risk consumer, since its search surface is the largest in the system.

### D23 (new) — `OrdersConverterServiceImplTest`'s outer-class tests never execute
**Ruling: PRESERVE the tests as written; record the defect. Fix is not Phase 2 work.**

Surefire reports `Tests run: 0` for `OrdersConverterServiceImplTest` itself while its
two `@Nested` classes report 16 and 2. The class declares **six** outer-level `@Test`
methods — including the `convertToPojo` customer and salesperson assertions — so six
tests are silently not running.

This is **pre-existing, not a Phase 2 regression**: the same `Tests run: 0` line is in
`microservices-conversion/baseline/test-baseline.txt` (line 2), captured before any
conversion work. It was found while retargeting the mocks in that class onto the new
HS-02 contracts, and is recorded rather than quietly fixed because making six dormant
tests execute can surface genuine failures, which is its own change with its own
review (G3 permits modifying tests for compatibility only).

**Consequence to carry into Phase 3:** the order domain's `convertToPojo` read path is
less covered than the per-class baseline suggests. Settle this together with D18,
which is the same category of problem — a test that does not test what it appears to.

**Action required of the owner:** diagnose and enable the six methods as its own
reviewed change, before the Orders port relies on them.

### D24 (new) — `cascade = ALL` on the person link contradicts BR-CUSTOMER-014 and BR-SALESPERSON-018
**Ruling: PRESERVE the code. Both recorded rules are the thing that is wrong.**

`Customer.person` and `Salesperson.person` both declare
`cascade = CascadeType.ALL`, so deleting either row **would cascade the delete to the
`people` row**. **BR-CUSTOMER-014 and BR-SALESPERSON-018 both assert the opposite** — that
the person survives.

Both rules appear to hold today, but **for the wrong reason**: the delete is normally
blocked first by the inbound order foreign keys (`FK_ORDER_CUSTOM_ID`,
`FK_ORDER_SPERSON`), which surface as a `DataIntegrityViolationException` → **HTTP 500**,
not a 409 (BR-CUSTOMER-015, BR-SALESPERSON-019). Delete a customer or salesperson that no
order references and the cascade is reachable.

Contrast `User.person`, which has **no cascade** — so BR-USER-048 ("deleting an account
does not delete its person") is genuinely true. Three sibling entities, two of them
cascading and one not: another of the identity asymmetries that must be ported rather than
harmonised.

The code is authoritative for the port (G1, G2); narrowing the cascade is a behavioural
change needing its own ruling. **Action required of the owner:** correct BR-CUSTOMER-014
and BR-SALESPERSON-018, or rule that the cascade should be narrowed. Recorded in
`domain-customer/errors.md` §5.4 and `domain-salesperson/errors.md` §5.4.

### D25 (new) — `salespeople.person_id` is non-unique but looked up through an `Optional`
**Ruling: PRESERVE. Record the latent fault; do not constrain the column.**

`salespeople.person_id` has **no unique key** and `Salesperson.person` is `@ManyToOne`, so
several salespeople sharing one person is **legal** by BR-SALESPERSON-002. Yet
`SalespeopleRepository.findByPersonIdNumber` returns `Optional<Salesperson>` over that
non-unique column — two such rows make Spring Data raise a non-unique-result exception →
HTTP 500, with no domain message.

It is unreachable today only because `people.person_id_number` carries
`UNIQUE KEY UK_PERSON_ID_NO`, so a second salesperson can be attached to the same person
row only by direct database manipulation or through the `Order.salesperson` cascade
(BR-SALESPERSON-016) — a path no route exercises (BR-SALESPERSON-015).

Adding a unique key would reject data the model explicitly permits; widening the return
type to a collection is an API change. Either needs its own ruling. **Carry the exact
schema asymmetry across:** `customers.person_id` unique, `salespeople.person_id` not,
`app_users.person_id` not and with no constraint at all. Recorded in
`domain-salesperson/01` §1.5 and `domain-user/01` §1.3.

### D26 (new) — the untrusted-token exception message interpolates the whole token
**Ruling: PRESERVE in Phase 2. Fix with whatever change first wants to log it.**

`ClaimsAuthorizationHeaderParserServiceImpl.parseToken` builds
`new IllegalStateException(String.format("Token %s can't be trusted", token))` — the
**entire live credential** inside an exception message.

**No token leaks today**, and that was verified rather than assumed: `JwtTokenVerifierFilter`
catches the exception and logs only the request method and URI (the Phase 0 **D2** fix),
and `AccessController`'s `@ExceptionHandler` has an empty body and discards it. The message
is constructed on every failure and never emitted.

This is the **same defect class as D2, one layer deeper** — D2 removed a token from a log
statement; this leaves one in the exception that statement used to log. Any future handler
that logs `exc.getMessage()`, or any framework default that renders an exception message,
leaks a live bearer token.

Ruled PRESERVE for Phase 2: changing the message is out of this phase's scope (G1) and the
literal ports verbatim (G2). **Action required of the owner:** redact the token from the
message as its own reviewed change — and in any case before any code is added that logs
this exception's message. Recorded in `domain-user/errors.md` §5 and `domain-user/02` §9.7.

---

## Phase 3 additions and corrections (Orders extraction)

### D21 — QueryDSL CVE-2024-49203 review trigger, actioned for Phase 3
**Re-checked at the start of Phase 3, as the Phase 1 and Phase 2 rulings required. Result: unchanged.**

`querydsl-jpa` and `querydsl-apt` 5.0.0 still carry **CVE-2024-49203 (HIGH)** and the
vulnerability database still reports **no CVE-free release**. Downgrading is forbidden
(G5) and there is nothing to upgrade to, so the ruling stands: **ACCEPT as inherited.**

**The Phase 1 ruling predicted that Orders would be the higher-risk consumer, and that is
confirmed.** `OrdersSortSpec` exposes **nine** sort keys - the largest sort surface in the
system, against a table with the most columns a client can influence. The mitigation still
holds and was verified rather than assumed: every one of the nine resolves through
`SortSpecParserService` against the static whitelist map, so `orderBy` never receives a raw
client string. A `sortBy` value outside the map is rejected before any QueryDSL call.

One of those nine keys - `customer` - does not survive the extraction; see the separate
ruling below.

**No further review trigger.** Phase 3 is the last phase that adds a QueryDSL consumer.

### Correction to the register and the transformation definition — there are 12 order statuses, not 11
**Not a defect. A factual correction, recorded because later work depends on the count.**

The transformation definition's summary and this register both say "11 order statuses".
`src/main/resources/data.sql` seeds **twelve**, ids 01-12, and
`ALTER TABLE order_statuses ALTER COLUMN order_status_id RESTART WITH 13` confirms it:

| id | code | name |
|---|---|---|
| 01 | -6 | `Returned` |
| 02 | -5 | `Delivery Failed` |
| 03 | -4 | `Delivery Cancelled` |
| 04 | -3 | `Rejected` |
| 05 | -2 | `Payment Failed` |
| 06 | -1 | `Payment Cancelled` |
| 07 | 01 | `Pending` |
| 08 | 02 | `Payment Started` |
| 09 | 03 | `Paid, Unconfirmed` |
| 10 | 04 | `Paid, Confirmed` |
| 11 | 05 | `Delivery On Route` |
| 12 | 06 | `Delivery Complete` |

`config/Constants.java` defines names for only **8** of them. The four it omits are
exactly the four that **D10** records as seeded-but-unreachable: `Returned`,
`Delivery Failed`, `Delivery Cancelled` and `Delivery On Route`. That is not a
coincidence - no code path names them because no code path reaches them.

Note the two names containing a comma - `Paid, Unconfirmed` and `Paid, Confirmed`. They
are client-observable literals (G2) and the comma must survive the port.

**Action required of the owner:** correct the "11 order statuses" figure in the
transformation definition.

### Correction — D9 is already fixed; Phase 3's payment work is D3 and D4 only
**Not a defect. A scope correction, verified in source.**

The definition assigns D9 - "payment session identifier is a Java hash code" - to Phase 3.
**It was fixed in Phase 0 and the fix is in this tree.**
`WebpayplusPaymentServiceImpl.newSessionId()` returns a UUID, its javadoc records the
former `transaction.hashCode()` and why it was wrong, and `WebpayplusPaymentSessionIdTest`
pins it with 3 tests.

Phase 3's payment-adapter work is therefore **D18 (first, as its own change), then D3 and
D4**. Nothing about D9 is reopened.

### D18 — FIXED. The payment adapter now has genuine unit coverage.
**Ruling as recorded in Phase 0: rewrite the test to mock the SDK, as its own reviewed change,
before Phase 3. IMPLEMENTED in Phase 3, step 3.1, ahead of D3 and D4.**

**Root cause, precisely.** `WebpayplusPaymentServiceImpl` constructed
`new WebpayPlus.Transaction(wpOptions)` **inline** in a private method, so nothing could
intercept it. The test mocked only `WebpayplusPaymentProperties`, which meant every test
made a **real HTTP call to the Transbank sandbox** and failed with HTTP 403 off-network.
One test - `ProductionModeWithoutConfig.will_not_fetch_transaction_result` - took roughly
**270 seconds** to fail, because it was waiting on a network timeout. That figure appears
in both the original and the post-Phase-0 baselines.

**The consequence was the serious part:** the payment adapter had **no genuine unit-test
coverage at all** - the most critical and least safely changeable component in the system,
and the one this phase must port with the D3 and D4 fixes applied. Four catch branches
were untestable.

**The fix.** A one-interface, one-method seam:

| Added | Role |
|---|---|
| `payment/impl/webpayplus/WebpayTransactionFactory` | `newTransaction()` - the only method |
| `payment/impl/webpayplus/impl/DefaultWebpayTransactionFactory` | `@Component` holding the **verbatim** body of the former `createWebpayTransaction()` |

`WebpayplusPaymentServiceImpl` takes the factory as a second constructor dependency.
**No behavioural change:** the same `WebpayOptions` are built from the same properties in
the same order, the same integration-versus-live branch is selected by the same
`Boolean.TRUE.equals(properties.isProduction())` test, and only the construction site
moves.

**No new dependency was added.** An injectable collaborator was chosen specifically so the
fix would not require `mockito-inline`'s `mockConstruction` or any other new test artefact.

**Coverage after the fix** - `Tests run: 12, Failures: 0, Errors: 0`, with **no network
access** and no test taking longer than a fraction of a second:

| Method | Branches now covered |
|---|---|
| `requestNewPaymentPageDetails` | happy path; the four SDK arguments pinned in order; `TransactionCreateException`; `IOException` |
| `requestPaymentResult` | response code 0; a non-zero decline passed through unchanged; `IOException`; `TransactionCommitException` |
| `getPaymentResultPageUrl` | happy path |

Both verbatim messages are asserted: `"Webpay could not create a new transaction"` and
`"Webpay failed to confirm the transaction"`.

`WebpayplusPaymentSessionIdTest` is **unchanged** - it exists precisely because of D18 and
still passes (3 tests).

**THE KNOWN-FAILURE BASELINE CHANGES HERE.** Every phase up to now diffed against "three
known environmental failures in `WebpayplusPaymentServiceImplTest`". Those three are
**gone**. From this point the pass condition is **zero failures and zero errors** in every
module, and `test-baseline-post-phase3.txt` must record that the known-failure set is now
empty.

**One test deliberately pins a defect rather than fixing it.**
`RequestPaymentResult.currently_swallows_a_commit_exception_and_returns_one` asserts the
**current, defective** D3 behaviour - that a `TransactionCommitException` is swallowed and
the magic value `1` returned. It exists so that the D3 fix in the next step has something
to invert, and so the inversion appears in a diff rather than silently. Its name says so.
