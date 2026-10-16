# Anti-Corruption Layers

## The existing translation surface

The codebase already has a consistent, deliberate separation between its REST
model and its persistence model. Every domain except `company-profile` has:

| Layer | Responsibility |
|---|---|
| transport model (`api/models/*Pojo.java`) | the wire contract; carries the validation annotations |
| converter service | transport model ↔ entity, both directions |
| patch service | sparse change map → a copy of an existing entity |
| predicate service | query parameters → a filter expression |
| sort specification | a whitelist of sortable keys |

This is the system's anti-corruption layer and it is **the most valuable asset
for a rewrite**: the REST contract and the table schema are genuinely decoupled,
so either can be re-expressed without disturbing the other.

Three qualifications:

1. **The patch surface is wider than the API.** Five domains have fully
   implemented patch services with no route exposing them (`customer`,
   `salesperson`, `user` accounts, `user` roles, and the billing pair). The
   translation layer is more complete than the surface that uses it.
2. **Three domains re-use another domain's transport model as their own
   contract** — see "Contract leakage" below.
3. **`company-profile` has no converter at all**; its entity-attribute-value rows
   are mapped to a flat response inside the service with a name switch.

---

## Inbound coupling: how domains reach each other today

| Consumer → provider | Mechanism | Style | Survives a split? |
|---|---|---|---|
| `product` → `product-category` | `ProductCategoryTreeResolverService` interface | **service** | yes, unchanged |
| `product` → `image` | `ImagesCrudService.getExisting` | **service** | yes, unchanged |
| `product-list` → `product` | `ProductsCrudService.getExisting` (called from a controller) | **service** | yes, after moving the call behind a service |
| `order` → `customer` | `CustomersCrudService.getExisting` / create | **service** | yes, unchanged |
| `user` → `customer` | `CustomersCrudService.create` | **service** | yes, unchanged |
| `order` → `product` | `ProductsRepository.findByBarcode` | **repository** | **no — contract needed** |
| `order` → `billing` | `BillingTypesRepository.findByName`, `BillingCompaniesRepository` | **repository** | **no — contract needed** |
| `order` → `shipper` | `ShippersRepository.findByName` | **repository** | **no — contract needed** |
| `user` → `person` | `PeopleRepository`, `PeopleConverterService`, `PeoplePatchService` | **repository + internals** | **no — contract needed** |
| `customer` → `person` | cascading one-to-one association | **ORM cascade** | **no — distributed write** |
| `salesperson` → `person` | cascading many-to-one association | **ORM cascade** | **no — distributed write** |
| every domain → `user` | authority **string literals** in annotations | **convention** | **no — shared vocabulary needed** |

Five of the twelve edges are already clean. Seven need work.

---

## Contracts a split would need

### 1. Product lookup (for `order` and `product-list`)

```text
findByBarcode(barcode) -> { barcode, name, currentPrice }   or empty
```

Required behaviour to preserve:

- a blank barcode is a client error (`Product barcode must be valid`);
- an unresolvable barcode is a client error
  (`Unexisting product in sell details`);
- the **current price** is returned and the order line's unit value is set from
  it, never from the client (`BR-ORDER-009`) — this is the system's only
  price-integrity control;
- the product **name** is returned so the line description can be generated and
  denormalised (`BR-ORDER-010`), which is what makes order history price-stable
  and survivable across product deletion.

For `product-list` the same lookup suffices, but note its callers silently skip
anything that does not resolve (`BR-PRODLIST-015`).

### 2. Billing classification (for `order`)

```text
resolveBillingType(name) -> { name, requiresCompany: boolean }   or empty
```

Preserve: an unknown name is a client error
(`Specified billing type does not exist`), and the two literal names `Bill` and
`Enterprise Invoice` select the branch (`BR-BILLING-002`). The
`requiresCompany` flag is the clean way to express the branch that currently
lives in the order converter — moving that decision into `billing` is the
recommended improvement.

### 3. Billing-company registration (for `order`)

```text
resolveOrCreateCompany(idNumber, name) -> { id, idNumber, name }
```

Preserve: resolution is by identification number only; an existing company is
reused and **the submitted name is discarded** (`BR-BILLING-010`); a blank
identification number is a client error
(`Billing company has no id number`); a duplicate company name with a new
identification number currently fails on a unique constraint during checkout —
decide deliberately whether to keep that.

### 4. Carrier lookup (for `order`)

```text
resolveShipper(name) -> { id, name }   or empty
```

Preserve the asymmetry exactly: an unknown carrier **rejects** order creation
(`Specified shipper does not exist`) but is **silently ignored** on an order
patch (`BR-SHIPPER-015`).

### 5. Person management (for `user`, `customer`, `salesperson`)

```text
findByIdNumber(idNumber)            -> person   or empty
createPerson(profile)               -> person
patchPerson(id, sparseChanges)      -> person
isClaimedByAnotherAccount(idNumber) -> boolean
```

Preserve: the non-blank-only semantics for names, identification number and
e-mail versus the **unconditional** semantics for the two phone fields
(`BR-PERSON-007`, `BR-PERSON-008`); the `person.` key-prefix convention used by
customer and salesperson payloads, including the fact that the stripping utility
over-replaces (`BR-CUSTOMER-012`).

This is the hardest contract because of the cascades. Today a customer and its
person are written atomically in one transaction (`BR-CUSTOMER-003`); after a
split that becomes a two-step distributed operation, so one of the following must
be chosen:

- keep `person` co-deployed with `customer` and `salesperson` (recommended for a
  first step — they are three small domains totalling 2,076 lines);
- or introduce a saga with compensation, accepting that a partial create is
  possible.

Note that registration is **already** non-transactional across three writes
(`BR-USER-012`), so this risk exists in the monolith today.

### 6. Authority vocabulary (for every domain)

```text
a versioned, generated catalogue of authority code constants
```

Today every controller carries authority codes as **string literals** that no
compiler checks, so a renamed code silently disables an access check
(`BR-USER-031`). The only code declared as a constant is the standalone
`checkout` authority. A split must publish the catalogue as a shared artefact and
generate the constants from it.

Also preserve: authorities are frozen inside the issued token, so a revoked grant
remains effective for up to the configured token lifetime (`BR-USER-022`). A
split must decide between continuing to trust token claims (fast, stale) and
re-resolving grants per request (correct, chattier).

---

## Contract leakage: shared transport models

Three domains have **no transport model of their own** and publish another
domain's model as their API contract:

| Domain | Model it publishes | Consequence |
|---|---|---|
| `customer` | `PersonPojo` | the customer API *is* the person API; the customer identifier is never exposed (`BR-CUSTOMER-008`) |
| `salesperson` | `PersonPojo` | same (`BR-SALESPERSON-008`) |
| `product-list` (contents) | `ProductPojo` | the contents API returns products, not membership rows (`BR-PRODLIST-013`) |

Extracting any of these three as-is means publishing the other domain's schema as
part of its contract, so a later change to `PersonPojo` or `ProductPojo` becomes
a breaking change for unrelated consumers. Introducing proper models is a
**wire-contract change** and must be planned as such.

---

## Database-level coupling that no anti-corruption layer covers

Four referential rules are enforced by the database and will simply stop existing
once the data is split. Each must become explicit application behaviour:

| Rule | Where | Replacement |
|---|---|---|
| `ON DELETE CASCADE` on list membership | `product_list_items.product_id` | the unused `deleteByProductId` operation, driven by a product-deleted notification — which does not exist, because the system publishes no events |
| `ON DELETE SET NULL` on order lines | `order_details.product_id` | explicit null-out, or drop the reference and rely on the denormalised description |
| deletion blocked by a reference | `product_images` → `images`, `products` → `product_categories`, `orders` → `customers`, `orders` → `shippers` | explicit pre-delete reference checks across services |
| one person per account | `app_users.person_id` — **no constraint exists**, the rule is application-only | cross-service check with a documented race policy |

See `data-ownership.md` for the full cross-boundary foreign-key table.

---

## Recommended sequencing of anti-corruption work

1. **Introduce the four lookup contracts** (product, billing classification,
   billing company, carrier) **inside the monolith first**, replacing the
   repository calls in the order converter. This is the single highest-value
   refactor and it is independently verifiable: the order domain's 70 rules must
   continue to hold.
2. **Move the billing branch decision into `billing`** so that `order` asks for a
   resolved billing instruction instead of comparing name literals.
3. **Move the product-list contents lookup behind a service** so no controller
   calls another domain directly.
4. **Publish the authority catalogue** as generated constants and replace every
   literal.
5. **Replace the two database cascades** with explicit application behaviour.
6. **Introduce proper transport models** for `customer`, `salesperson` and
   product-list contents — a planned wire-contract change.
7. Only then consider physical separation, starting with `company-profile`
   (isolated), then the reference-data domains, and ending with `user` and
   `order`.
