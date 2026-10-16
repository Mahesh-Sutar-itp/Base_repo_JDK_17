# Transformation Definition — Trebol Monolith to Microservices

**Status:** draft for review · **Authored from:** the Mode A decomposition
(`business-rules-extraction/`) and the Mode B specifications
(`detailed-business-specification/`) of this repository.

**Target architecture approved by the project owner:** three services, not twelve.

---

## 1. Objective

Decompose `trebol-backend-monolith` — a Spring Boot e-commerce application of 294
source files, 17,800 LOC, 22 tables and 67 HTTP routes — into **three
independently deployable services** plus a shared contract artefact, preserving
observable behaviour except where this definition explicitly rules otherwise.

### 1.1 Non-goals

| Not in scope | Why |
|---|---|
| Changing the external HTTP contract | Storefront and operator clients depend on the 67 catalogued routes. Route-compatible migration only, except where §6 rules otherwise. |
| Rewriting in another language | The target is the same platform. Language-neutral specs exist, but a language change would multiply risk against an already-defect-laden baseline. |
| Adding the modelled-but-unintegrated capabilities | Shipping rates, electronic invoicing, inventory and image hosting are modelled and inert. Building them is new product work, not conversion. |
| Twelve services, one per bounded context | Rejected: ~1,500 LOC per service, and four domains are too thin to deploy alone. See §2.3. |

### 1.2 Precondition on this definition

**This definition must not be executed before §5 (prerequisite refactors) is
complete inside the monolith.** Every attempt to extract a service before the
four lookup contracts and the authority catalogue exist will fail at compile
time, because four domains are coupled through each other's *repository*
interfaces, not through services.

---

## 2. Target architecture

### 2.1 The three services

| Service | Absorbs domains | ~LOC | Owned tables |
|---|---|---|---|
| **Identity** | `person`, `user`, `customer`, `salesperson` | ~5,500 | `people`, `app_users`, `app_user_roles`, `app_permissions`, `app_user_role_permissions`, `customers`, `salespeople` |
| **Catalogue** | `product`, `product-category`, `product-list`, `image` | ~4,000 | `products`, `product_categories`, `images`, `product_images`, `product_lists`, `product_list_items` |
| **Orders** | `order`, `billing`, `shipper` | ~6,000 | `orders`, `order_details`, `order_statuses`, `addresses`, `payment_types`, `billing_types`, `billing_companies`, `shippers` |

`company-profile` (249 LOC, table `app_params`, one public read route, **no write
path of any kind**) is **not** a service. Fold its single read into whichever
service fronts the storefront — Catalogue by default.

Every one of the 22 tables has exactly one owning service, inherited directly
from the Mode A ownership map where every table already has exactly one owning
domain.

### 2.2 Why these groupings

| Grouping | Evidence |
|---|---|
| Identity absorbs four domains | All four project onto the shared `Person` entity. `customer` has **no model of its own** — `CustomersConverterServiceImpl.convertToPojo` returns the person model. `salesperson` is a person plus a foreign key. The `user` aggregate's own invariant is that effective authorities are derived transactionally across its four tables, so they cannot be split either. |
| Catalogue absorbs `image` | `ProductsCrudServiceImpl.makeProductImageRelationships` **creates `images` rows through the product's own association cascade**, bypassing the image domain's create path entirely. The coupling is structural. |
| Orders absorbs `billing` and `shipper` | `order` is the only consumer of both. `shipper` is a unique name and nothing else; `billing` types are a lookup. `OrdersConverterServiceImpl` already reads and **creates** billing companies directly. |

### 2.3 The alternative that should be ruled out explicitly

At 17,800 LOC with 22 tables, a **modular monolith** — these same bounded
contexts enforced as module boundaries in one deployable — delivers the ownership
and testability benefits without distributed transactions. It keeps checkout
atomic and avoids the saga work in §5.3.

**This definition recommends the project owner consciously reject the modular
monolith before proceeding.** The prerequisite refactors in §5 are valuable
either way and are identical for both targets, so the decision can be deferred
until §5 is complete — at which point the cost of the remaining split work is
much better understood.

### 2.4 Shared contract artefact

Not a service. A versioned, generated library consumed by all three:

- the **authority code catalogue** (54 codes) — see §5.2;
- the four **cross-service lookup contracts** — see §5.1;
- shared DTO definitions for the entities crossing boundaries.

---

## 3. Inputs

| Input | Location | Use |
|---|---|---|
| Domain manifest | `business-rules-extraction/domain-decomposition-overview/domain-manifest.json` | Table ownership, dependencies, execution order, 9 shared entities. Machine-readable. |
| Global schema | `business-rules-extraction/database-documentation/00-global-schema.sql` | The 22 `CREATE TABLE` statements to split, with 11 recorded ORM/DDL divergences. |
| Seed data | `business-rules-extraction/database-documentation/00-seed-data.json` | Lookup rows each service must seed, including the 54 authority codes and 11 order statuses. |
| Coupling hotspots | `business-rules-extraction/reference-documentation/coupling-hotspots.md` | HS-01…HS-03, which define most of §5. |
| Integrations map | `business-rules-extraction/reference-documentation/external-integrations-map.md` | The two outbound integrations and their failure semantics. |
| Auth model | `business-rules-extraction/cross-cutting-documentation/authentication-authorization.md` | Token mechanics, the enforcement model, §5.2. |
| Per-domain rules | `business-rules-extraction/detailed-domain-bre/domain-*/business-rules.md` | 327 rules — the regression suite. |
| Deep specifications | `detailed-business-specification/domain-{person,image}/` | Method-level behaviour for two domains, including call-site censuses. |

**Known input gap.** Mode B deep specifications exist for `person` and `image`
only. The other ten domains are covered at Mode A depth (features, rules,
workflows, validations, integrations) which has proven sufficient for planning
but **not** for porting. Each phase below therefore begins with a per-domain
Mode B pass for the domains it touches.

---

## 4. Guardrails

Binding on every phase.

| # | Guardrail |
|---|---|
| G1 | **No behavioural change except by an explicit §6 ruling.** Every one of the 327 catalogued rules is either preserved or has a recorded decision. Silent "fixes" are forbidden — several defects are load-bearing for clients. |
| G2 | **Preserve every literal string that clients observe.** Error messages, authority codes, order status names, parameter names. The specs record them verbatim; they are contract. |
| G3 | **Preserve all tests.** The repository's tests may be modified for compatibility but never deleted or disabled. They plus the 327 rules are the only regression evidence. |
| G4 | **No new custom package repositories.** Only Maven Central or already-configured sources. |
| G5 | **No dependency downgrades**, and no dependency with known CVEs. Check before pinning. |
| G6 | **Refactor inside the monolith before extracting.** Every phase lands in the monolith, green, before any service is split out. This is what makes the work reversible. |
| G7 | **Secrets must not move into new committed configuration.** The baseline keeps the JWT signing key, database credentials and bootstrap passwords in committed files; the conversion must not replicate that into three services. Introduce external configuration at the first extraction. |

---

## 5. Prerequisite refactors — mandatory gates

These happen **in the monolith**. No service extraction may begin until §5.1 and
§5.2 are complete and the build is green.

### 5.1 Gate A — replace cross-domain repository reads with contracts

**Source finding:** HS-01, severity highest.
`jpa/services/conversion/impl/OrdersConverterServiceImpl.java` resolves, inside a
single checkout call, through **four other domains' repository interfaces**:

| Target domain | Repository call | Resolves by |
|---|---|---|
| `product` | `ProductsRepository.findByBarcode` | barcode, **per order line** |
| `billing` | `BillingTypesRepository.findByName` | billing-type name |
| `billing` | `BillingCompaniesRepository` — **read and create** | company identification number |
| `shipper` | `ShippersRepository.findByName` | carrier name |

Only customer resolution goes through a service interface. `OrdersPatchServiceImpl`
adds two more (`paymentTypesRepository`, `shippersRepository`).

**Required work.** Define four lookup contracts — product-by-barcode,
billing-type-by-name, billing-company-resolve-or-create, shipper-by-name — owned
by the target service, and replace every repository call with a contract call.
The order domain's **70 business rules are the regression suite**.

**Exit:** no class in the `order` domain imports another domain's repository.

**Note on the billing-company contract.** It is resolve-**or-create**, not a read.
Across a service boundary that becomes a remote write inside checkout, with all
the failure semantics that implies. Consider whether billing companies should be
pre-registered instead — a behavioural change requiring a §6 ruling.

### 5.2 Gate B — publish the authority catalogue as a generated artefact

**Source finding:** HS-03, severity high and invisible to the compiler.
Fifty-three of the 54 authority codes exist **only as string literals** duplicated
across eleven domains' controllers. Only `checkout` is a constant. No compiler
checks them, so renaming one silently disables an access check (BR-USER-031).

**Required work.** Generate a versioned constant catalogue from
`00-seed-data.json`, replace every literal, and fail the build on an unknown
code.

**Exit:** zero authority string literals in controller code.

**Also fix here:** the route-discovery feature derives a route by stripping
everything from the colon onward and matches actions by **substring**, so asking
about `product` also returns `product_lists:*` and `product_categories:*`
actions (BR-USER-032, BR-USER-033). Decide per §6 whether to preserve.

### 5.3 Gate C — decide the checkout and registration consistency model

**This gate is a decision, not code.** It must be settled before Orders or
Identity is extracted.

#### Correction to earlier analysis

An earlier draft of this plan asserted that registration and checkout were each a
single transaction. **That is wrong, and verified so:** neither
`RegistrationServiceImpl` nor `CheckoutServiceImpl` carries `@Transactional` on
the class or on the method. The individual CRUD services are transactional
(`CrudGenericService` is annotated), so **each write is its own transaction**.

The consequence is important and favourable: **partial registration and partial
checkout are already possible in the monolith today** (BR-USER-012). Extraction
does not *introduce* the inconsistency — it makes an existing latent failure mode
routine.

#### Registration — three independent writes

`RegistrationServiceImpl.register` performs, in order and in separate
transactions: insert `people`, insert `app_users`, insert `customers`. A failure
after the first leaves an orphaned person; after the second, a user with no
customer record. **`person` has no delete path anywhere in the system**
(BR-PERSON-013), so compensation is currently impossible.

#### Checkout — order creation then a synchronous payment call

`CheckoutServiceImpl.requestTransactionStart` creates the order (resolving
customer, products, billing, shipper, addresses — see §5.1), then calls Transbank
synchronously for a payment page, then writes the token and the `Payment Started`
status. **No stock check occurs at any step.**

#### Required decision, one of

| Option | Implication |
|---|---|
| **Keep co-located** | Identity stays one service with person+user+customer in one database, so registration can become genuinely transactional — an *improvement* on today. Orders likewise owns everything checkout writes except product and customer lookups. **This is what the approved three-service grouping already achieves**, and is the reason the grouping was chosen. |
| **Saga with compensation** | Requires a delete path for `person` (does not exist), an order-cancellation path (exists only as statuses that **no code reaches** — see §6), and an outbox. Substantial new infrastructure. |
| **Accept partial state** | Formalises today's behaviour. Requires a reconciliation job; none exists anywhere in the system. |

**Recommendation: keep co-located.** The approved grouping deliberately places
every registration write inside Identity and every checkout write inside Orders,
so no distributed transaction is needed for either flow. The only cross-service
calls in checkout are *reads* (product by barcode, customer resolution) plus the
billing-company resolve-or-create noted in §5.1.

### 5.4 Gate D — decide the authentication propagation model

**Source finding:** the baseline auth model is incompatible with a conventional
gateway, and this must be resolved before any service is extracted.

| Baseline property | Consequence for a split |
|---|---|
| Stateless HMAC-SHA bearer token; claims are subject, `authorities`, issued-at, expiration. **No issuer, audience or token identifier.** | Each service can verify independently with a shared key, but there is no `jti` for revocation and no audience to scope a token to a service. |
| **Authorities are resolved at login and embedded in the token**, never re-resolved per request (BR-USER-022). | Good for a split — services need no call to Identity to authorize. But a revoked grant stays effective until expiry, across all three services. |
| **Enforcement is entirely method-level. `SecurityConfig` declares no request-matcher rules at all** — verified: the class contains only `csrf disable`. | **Any endpoint without an authority annotation is publicly reachable** (BR-USER-025). A gateway therefore **cannot** derive access rules from paths. Every service must keep method-level enforcement, and the gateway must not be relied upon for authorization. |
| Expired token → the chain continues **anonymously**, no error (BR-USER-019). | A caller cannot distinguish "expired" from "never sent". Preserve or fix per §6. |
| Login and guest are **servlet filters, not controllers** (`/public/login`, `/public/guest`). | They are invisible to any controller-derived route inventory. They belong to Identity and must be ported as filters or redesigned. |
| No logout, no revocation list, no refresh. CSRF disabled. | Nothing to distribute, but nothing to revoke either. |
| Malformed token → unauthorized with an empty body, and **the full token value is written to the log** (BR-USER-020). | A credential-leak defect that must not be replicated into three services' logs. **Fix, do not preserve** — recommended ruling in §6. |

**Required decision.** Confirm: shared signing key distributed to all three
services, each verifying locally and enforcing method-level authorities from the
token claim; gateway performs routing and TLS only, **no authorization**. Add
issuer and audience claims, and a token identifier if revocation is wanted.

---

## 6. Defect decision register — rulings required before porting

Every item below is current observable behaviour. Porting silently either
preserves a defect or changes a contract. **Each needs an explicit ruling.** The
recommendation column is advisory.

| # | Finding | Rule | Recommendation |
|---|---|---|---|
| D1 | A **mistyped** `DELETE` query parameter deletes an entire table. The controller guards only on a non-empty parameter map, while the predicate service returns a match-everything predicate when nothing is recognised. Affects every write-capable domain, not just `image`. | BR-IMAGE-013 | **Fix before extraction.** Require ≥1 recognised selector. Highest severity in the system. |
| D2 | Malformed-token handling **logs the full token value**. | BR-USER-020 | **Fix.** Do not replicate a credential leak into three services. |
| D3 | Payment **commit failure is swallowed and returned as a non-zero response code**, i.e. an infrastructure failure is recorded as a genuine decline. | BR-ORDER-038 | **Fix.** Distinguish decline from failure; they need different operator responses. |
| D4 | Payment callbacks are **public, unauthenticated, with no signature verification, no provider-origin check and no idempotency key**. Replay protection is incidental — the lookup filters on status `Payment Started`. | BR-ORDER-041, BR-ORDER-043 | **Fix.** Largest security gap in the system, and it drives state transitions on real orders. |
| D5 | `PATCH /data/orders` with a `status` key moves an order to **any** status with no transition validation, bypassing the state machine. | BR-ORDER-028 | **Fix or restrict.** It invalidates the state machine the Orders service is built around. |
| D6 | Profile update **always fails** for any account that already has a profile — the `user` domain calls a patch overload that throws unconditionally. | BR-PERSON-027 | **Fix.** A live, totally broken user-facing flow. |
| D7 | Duplicate detection checks one of three unique columns in `image`; `code`/`url` collisions surface as **500 instead of 409**. | BR-IMAGE-002 | **Fix.** Guaranteed 500s under normal use. |
| D8 | `transportValue` is never computed or set, and is **excluded** from the order total sent to the payment provider. | BR-ORDER-016, BR-ORDER-035 | **Ruling needed.** If shipping is ever charged, this is a revenue defect. Today it is consistently zero. |
| D9 | Payment session identifier is a **Java hash code**, not stable across processes. | BR-ORDER-034 | **Fix.** Breaks outright with more than one instance — and a split means more instances. |
| D10 | Four order statuses — `Delivery On Route`, `Delivery Failed`, `Delivery Cancelled`, `Returned` — are **seeded and reached by no code path**. | order workflows | **Ruling needed.** Implement delivery tracking and returns, or drop the statuses. Do not port dead states silently. |
| D11 | Mail is sent **after** the status transition commits, synchronously, with no retry; a mail failure surfaces as a 500 with the status already changed and the mail lost. | BR-ORDER-055 | **Fix via an outbox** when Orders is extracted. Currently the only candidate for event infrastructure. |
| D12 | `images:create` is **not checked** on the only internal image write path — product authority implicitly grants image creation. | BR-IMAGE-024 | **Ruling needed.** Both services end up in Catalogue, so this can be deferred. |
| D13 | A blank image filename inside a product payload is **silently dropped**; the product succeeds with fewer images and the client is never told. | BR-IMAGE-024 | **Ruling needed.** Partial success must get a defined contract. |
| D14 | Guest login uses the configured guest name as **both username and password**, and persists a real `person` and `customer` row **from inside an authentication filter**. | BR-USER-026, HS-02 | **Fix.** Business writes do not belong in an auth filter; it couples login availability to business tables. |
| D15 | No password change or reset capability exists anywhere. All four account-status flags are hardcoded permissive — no disabled, locked or expired state, no lockout. | BR-USER-045, BR-USER-023 | **Ruling needed.** Not conversion work, but an Identity service without password reset is a hard sell. |
| D16 | Order totals extract 19% tax from a **tax-inclusive** unit value, truncating to integers. | W-ORDER-02 | **Preserve exactly.** Monetary arithmetic; any change alters recorded financial data. |
| D17 | `app_user_role_permissions` has **no uniqueness constraint** on the pair, so the same authority can be granted to a role twice; de-duplication happens in memory at login. | user overview | **Fix the constraint** during the Identity schema split. |

---

## 7. Phased execution plan

Order derived from the Mode A execution order, with the dependency-heavy domains
last.

### Phase 0 — Prerequisites (in the monolith)
1. Gate A (§5.1) — four lookup contracts replace cross-domain repository reads.
2. Gate B (§5.2) — generated authority catalogue.
3. Gate C and D decisions (§5.3, §5.4) recorded.
4. §6 rulings recorded, and every item marked "fix before extraction" landed:
   D1, D2, D6, D7, D9, D14, D17.
5. **Exit:** monolith builds and tests green; no cross-domain repository imports;
   no authority string literals.

### Phase 1 — Catalogue extraction
Chosen first: fewest inbound dependencies, one consumer relationship to sever,
and `image`'s boundary is already service-level and clean (no consumer reaches
its repository).
1. Mode B specifications for `product`, `product-category`, `product-list`.
2. Split the schema: 6 tables, with seed data for categories.
3. Stand up the service behind the product-by-barcode contract from Gate A.
4. Fold in the `company-profile` read.
5. **Exit:** Orders resolves products through the contract across the network;
   catalogue routes behaviour-identical.

### Phase 2 — Identity extraction
1. Mode B specifications for `user`, `customer`, `salesperson` (`person` is done).
2. Resolve HS-02 — the identity kernel currently writes **outward** into `person`
   and `customer` from `RegistrationServiceImpl`, `ProfileServiceImpl` and an
   authentication filter. After grouping, all three targets are inside Identity,
   so these become internal calls. Verify no residual outward write remains.
3. Split the schema: 7 tables, seeding the 54 authority codes and the roles.
4. Port `/public/login` and `/public/guest` **as filters**.
5. Distribute the signing key per the Gate D decision; add issuer and audience.
6. Make registration genuinely transactional now that all three writes are
   co-located — an improvement on the baseline, recorded as such.
7. **Exit:** all three services verify tokens locally; method-level enforcement
   retained everywhere; registration atomic.

### Phase 3 — Orders extraction
Last, because it reads from both other services.
1. Mode B specifications for `order` and `billing` — the largest remaining
   analysis task; partition the behavioural docs by subsystem (status machine,
   checkout, payment adapter, mail adapter, receipt, search/patch).
2. Split the schema: 8 tables, seeding the 11 order statuses, billing types and
   payment types.
3. Port the payment adapter with the D3, D4 and D9 fixes.
4. Port the mail adapter behind an outbox per D11.
5. Enforce the state machine and close the D5 patch bypass.
6. **Exit:** checkout completes end to end across three services; the state
   machine is authoritative; payment callbacks authenticated.

### Phase 4 — Decommission
1. Remove migrated code from the monolith.
2. Retire the shared schema.
3. Confirm all 67 routes are served, with the §6 rulings as the only intended
   differences.

---

## 8. Exit criteria

| # | Criterion |
|---|---|
| X1 | Three services deploy independently, each owning its tables exclusively; no service reads another's schema. |
| X2 | All 22 tables are assigned, with no cross-service foreign key. |
| X3 | All 67 routes are served and behaviour-identical, except where a §6 ruling records an intended change. |
| X4 | All 327 catalogued business rules are preserved or have a recorded ruling. |
| X5 | Every test from the baseline is present and passing. |
| X6 | No authority string literals; the catalogue is a generated, versioned artefact. |
| X7 | No cross-domain repository imports; all cross-service access goes through published contracts. |
| X8 | Registration and checkout have a defined and tested consistency model. |
| X9 | Payment callbacks are authenticated and idempotent. |
| X10 | No secret is in committed configuration in any service. |
| X11 | Every item in the §6 register has a recorded ruling, and every "fix" ruling is implemented and tested. |

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| **Gate A is larger than it looks.** The order converter is the single biggest body of extraction work in the system; it touches four domains in the checkout critical path. | Do it inside the monolith with the 70 order rules as the regression suite, before any split. |
| **No event infrastructure exists** — no broker, queue or stream anywhere in the codebase. The outbox for D11 is built from nothing. | Scope it in Phase 3, or keep mail synchronous and accept D11 unchanged with a recorded ruling. |
| **Defect-laden baseline.** 327 rules include many defects; "preserve behaviour" and "fix defects" conflict directly. | The §6 register makes every conflict an explicit decision rather than an accident. |
| **Only 2 of 12 domains have porting-depth specs.** | Each phase begins with Mode B for the domains it touches — already built into §7. |
| **Three services may be more than this codebase warrants.** | §2.3 — revisit after Phase 0, when the remaining cost is concrete. Phase 0 is valuable regardless. |
| **No secret manager, credentials in committed config.** | G7; address at the first extraction, not at the end. |
