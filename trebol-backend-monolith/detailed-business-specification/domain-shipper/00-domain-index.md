# Shipper — Domain Index

Domain slug: `shipper` · Complexity: low · 10 source files · 1 owned table · 1 entity ·
**5 HTTP endpoints** · 15 business rules

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "shipper"`.

Produced for **Phase 3 (Orders extraction)**.

---

## 1. Domain summary

**Purpose.** The list of carriers an order can be shipped by. That is all.

**It is the simplest domain in the system.** One entity, one table, **two columns**, and the
only business attribute is a name (BR-SHIPPER-002). There is no address, no rate, no service
level, no coverage area and no contact — the shipping-rate capability the project models
elsewhere is explicitly a non-goal of this conversion.

**Core invariant, as implemented.** Carrier names are unique (BR-SHIPPER-001), enforced by
`unique = true` on the column and `UK_SHIPPER_NAME` in the DDL. The name is the business key
that `getExisting` resolves by.

**Boundaries.** Outbound coupling is **zero** — verified by import census, §3.2. Shipper is a
leaf domain; all coupling runs inward, from the order domain through `ShipperLookupContract`.

**Key entity.** `Shipper` → `shippers`.

**An order may have no carrier at all** (BR-SHIPPER-014). `Order.shipper` is nullable, and
shipping information is only resolved when the order carries a shipper name — so this domain
is genuinely optional to the checkout path.

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **`ShippersCrudServiceImpl.getExisting` throws `BadInputException("Billing type has no name")`** — a copy-paste defect. The message names the **wrong domain entirely**: a client submitting a blank carrier name is told something about billing types. It is client-observable, so it ports **verbatim** (G2). | **High** | `02` §3, `errors.md` §1 |
| W2 | **`GET /data/shippers` is deliberately unauthenticated**, and the seeded `shippers:read` authority is **never referenced by any `@PreAuthorize`** (BR-SHIPPER-008, BR-SHIPPER-009). The code exists in the catalogue and in `data.sql`; nothing enforces it. | Medium | `permissions.md` |
| W3 | **An unknown carrier name fails on create but passes silently on patch** (BR-SHIPPER-015). The order converter rejects an unresolvable shipper with `BadInputException("Specified shipper does not exist")`; `OrdersPatchServiceImpl` resolves it with `.ifPresent(...)` and **silently ignores** an unknown name. Two paths, two behaviours, for the same bad input. | Medium | BR-SHIPPER-015, `errors.md` §4 |
| W4 | **`Shipper.name` has no `@Size` at all** — the only string column in these domains with no entity-level bound. Length is limited only by the database's `varchar(255)` (BR-SHIPPER-005). | Low | `01` §1 |
| W5 | **`PATCH /data/shippers` takes `@RequestBody Map<String,Object>` with no `@Valid`**, so the patch body is entirely unvalidated — unlike `POST` and `PUT`, which both carry `@Valid @RequestBody ShipperPojo`. | Medium | `02` §5 |
| W6 | **The copy constructor copies `id`**, so a patched copy carries the same primary key and `saveAndFlush` is an update rather than an insert. Load-bearing for the patch path. | Medium | `01` §1.2 |
| W7 | **Deleting a carrier referenced by an order is blocked by the database**, not by application logic (BR-SHIPPER-013), and surfaces as a `DataIntegrityViolationException` → **HTTP 500**, not a 409. | Low | `errors.md` §3 |
| W8 | **An empty delete filter deletes nothing** (BR-SHIPPER-012) — `DataCrudGenericController.delete` only parses a predicate when parameters are present, so a parameter-less `DELETE` is a **silent no-op**. Distinct from the Phase-0 **D1** case, where parameters are present but all unrecognised and the request is now rejected with 400. | Low | `errors.md` §5 |
| W9 | **Two distinct deprecation literals coexist** — the converter throws `"This method is deprecated"`, the patch service `"This method has been deprecated"`. Do not normalise them (G2). | Low | `errors.md` §1 |
| W10 | **`POST` calls `crudService.create(input)` directly rather than `super.create(...)`** — the same deviation as the customer, salesperson and user controllers. Port as-is. | Low | `02` §5 |

No warning in this table is fixed during the port (G1). **W1 in particular is preserved**:
the wrong-domain message is a client-visible literal, and correcting it is a contract change
needing its own ruling.

---

## 3. File coverage

### 3.1 All 10 shipper files

| # | Source file | Role | Covered in |
|---|---|---|---|
| 1 | `jpa/entities/Shipper.java` | entity | `01` §1 |
| 2 | `api/models/ShipperPojo.java` | transport | `01` §2 |
| 3 | `jpa/repositories/ShippersRepository.java` | repository | `02` §2 |
| 4 | `jpa/services/crud/ShippersCrudService.java` | contract | `02` §1 |
| 5 | `jpa/services/crud/impl/ShippersCrudServiceImpl.java` | CRUD | `02` §3 |
| 6 | `jpa/services/conversion/ShippersConverterService.java` + `impl/` | conversion | `02` §4 |
| 7 | `jpa/services/patch/ShippersPatchService.java` + `impl/` | patch | `02` §6 |
| 8 | `jpa/services/predicates/ShippersPredicateService.java` + `impl/` | search | `02` §7.1 |
| 9 | `jpa/sortspecs/ShippersSortSpec.java` | helper | `02` §7.2 |
| 10 | `api/controllers/DataShippersController.java` | controller | `02` §5 |

Plus the published seam `jpa/services/contracts/ShipperLookupContract.java` +
`impl/ShipperLookupContractImpl.java` — `02` §8.

**Coverage: 10/10 (100%).** Paths relative to `src/main/java/org/trebol/`.

### 3.2 Outbound coupling census

Every import block of all 10 files was examined for
`order|product|image|user|customer|salesperson|person`. **Two hits, both false positives:**
`com.querydsl.core.types.OrderSpecifier` in `DataShippersController` and `ShippersSortSpec`.

**Zero real outbound dependencies.** No seam to cut.

### 3.3 Inbound coupling — the Gate A seam

| Consumer | Shape | Status |
|---|---|---|
| `OrdersConverterServiceImpl` → `ShipperLookupContract` | `findShipperByName`, read-only | **closed in Phase 0 (Gate A)** |
| `OrdersPatchServiceImpl` → `ShipperLookupContract` | `findShipperByName`, read-only | **closed in Phase 0 (Gate A)** |
| `Order.shipper` | `@ManyToOne`, **nullable**, `updatable = false`, FK `FK_ORDER_SHIPPER_ID` | **intra-service** — shipper travels into the same Orders service, so nothing is cut |

**The contract is read-only**, which is the one way this domain is simpler than billing: there
is no resolve-or-create here, so no distributed-write question arises.

### 3.4 Shared-kernel files read but not owned

`CrudGenericService`, `DataCrudGenericController` (including the Phase-0 **D1** delete guard),
`DataGenericController`, `DataCrudController`, `DataController`, the five generic service
contracts, `jpa/Repository.java`, `jpa/DBEntity.java`, `PaginationServiceImpl`,
`SortSpecParserServiceImpl`, `BadInputException`, `config/ApiProperties.java`, and
`AuthorityCodes` from the shared-contracts module.

---

## 4. Entity

| Entity | Table | Owned | Columns | Relationships |
|---|---|---|---|---|
| `Shipper` | `shippers` (`UK_SHIPPER_NAME`) | yes | 2 | **none** |

`serialVersionUID = 18L`. No relationships, no lifecycle callbacks, no `@Version`.

`shippers` is a **pure parent**: it has no outbound foreign key, and the single key that
references it (`FK_ORDER_SHIPPER_ID`) is declared on `orders`, which moves into the same
service. **Nothing to cut in Phase 3.**

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, coupling census, the Phase 3 checklist |
| `01-shipper-entity-and-transport.md` | behavioural spec: the entity and its transport model |
| `02-shipper-services.md` | behavioural spec: repository, CRUD, converter, patch, search, controller, the published contract — plus the G8 test inventory |
| `errors.md` | every error condition with its literal message; the deliberate non-errors |
| `permissions.md` | the three enforced authorities, the one that is seeded but never enforced, and the anonymous read |

---

## 6. Rule traceability

All 15 `BR-SHIPPER-*` rules (001–015, contiguous, in document order) are referenced by id
across this document, `01`, `02`, `errors.md` and `permissions.md`.

Unlike the billing rule file, this one is **correctly ordered with no gaps** — worth noting
only because its sibling is not.

---

## 7. Phase 3 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 10 files plus the `ShipperLookupContract` pair, **identical fully-qualified names**. |
| Literals | **`"Billing type has no name"` — the wrong-domain message from W1, ported verbatim** — plus `"This method is deprecated"`, `"This method has been deprecated"`, the inherited kernel messages, the 3 filter parameter names and the single `name` sort key (G2). |
| Tables | `shippers` moves with DDL, unique-key name, engine, charset and collation unchanged — including `shipper_name varchar(255)` and the absence of an entity-level `@Size` (W4). |
| Foreign keys | **None leave this table.** `FK_ORDER_SHIPPER_ID` is declared on `orders` and points inward. Nothing to cut. |
| Seed data | None — `shippers` is not seeded. The four `shippers:*` permission rows are seeded by the **identity** service's `data.sql`, not this domain's. |
| Tests | `DataShippersControllerTest`, `ShippersCrudServiceImplTest`, `ShippersConverterServiceImplTest`, `ShippersPatchServiceImplTest`, `ShippersPredicateServiceImplTest` — **and a new `ShipperLookupContractImplTest`**, which does not exist today (see below). |
| Behaviour | No warning in §2 is fixed during the port (G1). W1 and W3 are the two most likely to be "corrected" by accident. |

### 7.1 The missing test

**There is no `ShipperLookupContractImplTest`.** `BillingContractImplTest` has 7 tests and
`CustomerProvisioningContractImplTest` has 4, but the shipper contract — one of the three
Gate A seams — has **no behavioural pin at all**. Step 3.8 adds one.

That gap matters more after extraction than before: once Orders is a separate service, the
contract is the only thing standing between the checkout path and a carrier lookup, and an
untested seam is the one most likely to drift.
