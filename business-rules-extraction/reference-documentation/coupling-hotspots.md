# Coupling Hotspots

Scope limited to coupling that **affects boundary decisions or extraction
order**. Ordinary intra-domain coupling and framework inheritance are not listed
here.

Each hotspot states what it is, why it matters for a split, and what must be
built to resolve it.

---

## HS-01 — The order converter reaches four domains through their repositories

**Severity: highest.** This single class is the largest body of extraction work
in the system.

`jpa/services/conversion/impl/OrdersConverterServiceImpl.java` resolves, inside
one transaction:

| Target domain | Repository called | Resolved by |
|---|---|---|
| `product` | `ProductsRepository.findByBarcode` | barcode, per order line |
| `billing` | `BillingTypesRepository.findByName` | billing-type name |
| `billing` | `BillingCompaniesRepository` (read **and create**) | company identification number |
| `shipper` | `ShippersRepository.findByName` | carrier name |
| `order` (own) | `AddressesRepository.findByFields` | all six address fields |

Only the customer resolution goes through a service interface.

**Why it matters:** four compile-time dependencies on other domains' persistence
interfaces, all exercised in the checkout critical path. A split cannot proceed
until each becomes a contract.

**Resolution:** introduce the four lookup contracts specified in
`../domain-decomposition-overview/anti-corruption-layers.md`, replacing the
repository calls **inside the monolith first**. The order domain's 70 rules are
the regression suite for that refactor.

---

## HS-02 — The identity kernel depends outward on two business domains

**Severity: high.** It inverts the expected dependency direction.

| Component | Writes into | Mechanism |
|---|---|---|
| `api/services/impl/RegistrationServiceImpl.java` | `person`, `customer` | person repository and converter directly; customer through its service |
| `api/services/impl/ProfileServiceImpl.java` | `person` | person crud service **and** person patch service directly |
| `security/JwtGuestAuthenticationFilter.java` | `customer` | customer crud service (clean), but **from inside an authentication filter** |

**Why it matters:** extracting identity means either an identity service that
calls business services during login and registration — coupling authentication
availability to business domains — or moving those writes out of the identity
flows entirely.

**Aggravating factor:** registration is **not transactional** across its three
writes (`BR-USER-012`), so a partial registration is already possible in the
monolith. A split makes it the normal failure mode.

**Resolution:** introduce the person-management contract; decide whether guest
customer creation belongs in authentication at all; add a saga or outbox for
registration regardless of whether a split happens.

---

## HS-03 — Authority codes are unchecked string literals in every domain

**Severity: high, and entirely invisible to the compiler.**

Fifty-three of the 54 authority codes exist **only** as string literals inside
controller annotations across all eleven consuming domains. Only `checkout` is
declared as a constant (`config/Constants.java`).

**Why it matters:** renaming or mistyping a code silently disables an access
check — no build failure, no startup failure, no test failure unless a test
happens to assert that exact string. Fourteen of the 54 codes are already granted
and never enforced (`../cross-cutting-documentation/common-permissions.md`),
which shows the drift is real.

**Resolution:** publish the catalogue as a generated, versioned artefact and
replace every literal with a constant reference.

---

## HS-04 — `Constants` is a cross-domain vocabulary shared by three domains

`config/Constants.java` holds literals that three different domains compare
against:

| Constant group | Compared by |
|---|---|
| eight order status names | `order` (`OrdersProcessServiceImpl`, the converter) |
| two billing-type names | `order`'s converter — deciding **`billing`**'s branch |
| two payment token header names | `order`'s checkout controller |
| the checkout authority | `order`'s checkout controller |
| three token constants | `user`'s filters and parser |
| the person key prefix | `customer` (and `salesperson`, which hardcodes the literal instead) |

**Why it matters:** a single file is a shared-kernel dependency for three
domains, and the status-name comparison means **renaming a seeded status row
breaks transitions at runtime** (`BR-ORDER-020`).

**Resolution:** split the file by domain. Move the billing-type branch decision
into `billing` so `order` stops comparing names. Prefer code-based status
comparison, which the source itself recommends in a comment.

---

## HS-05 — A controller in `product-list` calls another domain's service directly

`api/controllers/DataProductListContentsController.java` injects and calls
`ProductsCrudService` from the **controller layer**, bypassing its own domain's
service layer entirely. It also queries two repositories directly and extends
neither generic controller base class.

**Why it matters:** the dependency itself is clean (a service interface), but the
layering is not, so the call cannot be intercepted, instrumented or replaced
without touching the controller.

**Resolution:** move the lookup behind a product-list service before extracting
anything.

---

## HS-06 — Three domains publish another domain's transport model as their contract

| Domain | Model published | Rule |
|---|---|---|
| `customer` | `PersonPojo` | `BR-CUSTOMER-008` |
| `salesperson` | `PersonPojo` | `BR-SALESPERSON-008` |
| `product-list` (contents) | `ProductPojo` | `BR-PRODLIST-013` |

**Why it matters:** extracting any of the three exports the other domain's schema
as part of its public contract. A later change to `PersonPojo` then becomes a
breaking change for unrelated consumers.

**Resolution:** introduce proper transport models — a planned **wire-contract
change**, not an internal refactor.

---

## HS-07 — Two ORM cascades write another domain's table

| Owner of the cascade | Writes | Cascade |
|---|---|---|
| `customer` | `people` | one-to-one, cascade all |
| `salesperson` | `people` | many-to-one, cascade all |

**Why it matters:** a customer and its person are written **atomically** today
(`BR-CUSTOMER-003`). After a split that becomes a distributed create, and the
atomicity guarantee is lost.

A third, latent cascade exists: `Order.salesperson` carries persist and merge,
so if order attribution were ever populated, order writes would create
salesperson rows **bypassing that domain's validation**
(`BR-SALESPERSON-016`).

**Resolution:** either co-deploy `person`, `customer` and `salesperson` (2,076
lines combined) or accept a saga. Remove the order-to-salesperson cascade.

---

## HS-08 — Database-enforced rules that cross proposed boundaries

Four referential behaviours are implemented by the database and will simply cease
to exist once data is split:

| Rule | Tables | Consequence of a split |
|---|---|---|
| `ON DELETE CASCADE` | `product_list_items` → `products` | product deletion stops shrinking lists; the uncalled `deleteByProductId` must be driven by a notification that does not exist |
| `ON DELETE SET NULL` | `order_details` → `products` | historical lines stop being nulled; may be acceptable since name and price are denormalised |
| deletion blocked by reference | `product_images` → `images`, `products` → `product_categories`, `orders` → `customers`, `orders` → `shippers`, `app_users`/`app_user_role_permissions` → `app_user_roles` | each becomes an explicit cross-service pre-delete check |
| one person per account | `app_users.person_id` — **no constraint exists**; the rule is application-only | becomes a cross-service race (`BR-USER-004`) |

The full table is in `../domain-decomposition-overview/data-ownership.md`.

---

## HS-09 — Shared generic base classes define behaviour every domain's rules depend on

`jpa/services/crud/CrudGenericService.java` and
`api/DataCrudGenericController.java` supply the already-exists conflict, the
single-row update guards, the no-op-write short-circuit, the
`Missing request params` rejection, the `No element was found to update`
not-found and the do-nothing-on-empty-filter delete.

**Why it matters:** this is **shared behaviour, not just shared code**. Every
domain's rule set cites these literals and semantics. Duplicating the classes per
service is fine; silently changing the behaviour is not.

**Resolution:** duplicate rather than share the library (to avoid coupling every
release), but treat the four literal messages and the guard semantics as part of
the public API contract.

---

## HS-10 — The error envelope is a shared public contract

`api/ExceptionsControllerAdvice.java` and `api/models/AppError.java` produce the
four-code envelope that every client depends on.

**Why it matters:** after a split, every service must emit byte-compatible
envelopes or clients break. Note also that the advice **discards** the specific
internal message, so changing that behaviour is itself a contract change.

---

## Coupling scorecard by domain

| Domain | Outbound domain deps | Inbound domain deps | Repository-level edges | Extraction difficulty |
|---|---|---|---|---|
| `company-profile` | 0 | 0 | 0 | trivial |
| `image` | 0 | 1 (service) | 0 | low |
| `product-category` | 0 | 1 (service) + 2 dead | 0 | low |
| `shipper` | 0 | 1 | 1 inbound | low |
| `product-list` | 1 (service, from a controller) | 0 | 0 | low |
| `product` | 2 (both service) | 2 | 1 inbound | low–medium |
| `billing` | 0 | 1 | 2 inbound | medium |
| `salesperson` | 1 (cascade) | 1 (dead) | 0 | medium |
| `customer` | 1 (cascade) | 2 (both service) | 0 | medium |
| `person` | 0 | 3 (2 cascades + 1 internals) | 1 inbound | medium |
| `user` | 2 | 11 (implicit, literals) | 1 outbound | high |
| `order` | 5 | 0 | **4 outbound** | high |

The pattern is consistent: difficulty tracks **repository-level edges and
cascades**, not size. `order` is the largest domain and `user` the second, and
they are also the two with the worst coupling — hence their position at the end
of `../domain-decomposition-overview/execution-order.md`.

## Resolution order (recommended)

1. HS-01 — the four order lookup contracts (highest value, independently
   verifiable).
2. HS-04 — split `Constants`; move the billing branch into `billing`.
3. HS-03 — generate authority constants.
4. HS-05 — move the contents lookup behind a service.
5. HS-02 — the person-management contract; decide on guest customer creation.
6. HS-08 — replace the two cascades with explicit behaviour.
7. HS-06 — introduce proper transport models (a wire-contract change).
8. HS-07 — decide co-deployment versus saga for the person cascades.

Steps 1–4 are safe refactors inside the monolith and deliver most of the
decoupling benefit before any physical separation is attempted.
