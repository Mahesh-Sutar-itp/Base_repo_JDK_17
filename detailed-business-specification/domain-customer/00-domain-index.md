# Customer — Domain Index

Domain slug: `customer` · Complexity: low (surface) / high (boundary) · 13 source files ·
1 owned table · 1 entity · **4 HTTP endpoints** · 22 business rules

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "customer"`.

Produced for **Phase 2 (Identity extraction)**. Only `domain-person` and `domain-image`
existed at porting depth; the definition requires each phase to produce the specs for the
domains it touches.

---

## 1. Domain summary

**Purpose.** A `customer` is a **pure join row**: a surrogate id and a mandatory
one-to-one link to a `person`. It carries **no attributes of its own**
(BR-CUSTOMER-004). Its entire reason to exist is to mark a person as someone who can be
the buying party on an order.

**Core invariant, as implemented.** One customer per person, enforced **in the database**
by `UNIQUE KEY UK_CUSTOM_PERSON_ID (person_id)` (BR-CUSTOMER-001), and
`person_id_number` is itself globally unique on `people`. The business key used by every
service-layer lookup is **`PersonPojo.idNumber`**, never the surrogate id
(BR-CUSTOMER-005).

**Boundaries.** Outbound coupling is **zero** — verified by import census, §3.2. The
only types this domain reaches for are `Person`/`PersonPojo` (the person domain, which
moves into Identity with it), `BadInputException`, `Utils`/`Constants` and the generic
base classes. All coupling is **inbound**, and that is what made this domain the hard
part of Phase 2 (§3.3).

**Key entity.** `Customer` → `customers`.

**Key services.** One repository with a single declared query, one CRUD service with
**exactly one override** (`getExisting`), one converter that delegates wholly to the
person domain, one patch service that is **unreachable by any route**, one predicate
service, one sort spec, and a write-capable controller exposing four CRUD operations.

**Transport model.** There is **no `CustomerPojo`**. Every generic parameter is
`PersonPojo` (BR-CUSTOMER-008), so the API surface of this domain is literally the person
model. See §2 W1 for why that is a boundary hazard and not just a shortcut.

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **The API never exposes any id — neither `customer_id` nor `person_id`.** `PersonPojo` has no id field at all. Every write is therefore keyed by `idNumber` or by a filter map, and a client cannot address a customer by its surrogate key. Read-modify-write is only possible through `idNumber`. | High | BR-CUSTOMER-005, BR-CUSTOMER-008, `01` §2 |
| W2 | **`PUT` is a full overwrite that silently orphans a `people` row.** `update(input, id)` builds a brand-new `Customer` whose nested `Person` has `id == null`, then sets only the **outer** id. With `cascade = ALL` this **INSERTs a new `people` row** and repoints `person_id`. The previous person row is left behind, and nothing deletes it. | High | BR-CUSTOMER-013, `02` §3.3 |
| W3 | **`cascade = CascadeType.ALL` on `Customer.person` means a customer delete cascades to the person.** This directly contradicts BR-CUSTOMER-014 ("deleting a customer does not delete its person"), which is recorded as holding only because the `delete` path goes through `repository.deleteAll(...)` on customers and the FK from `orders` usually blocks it first (BR-CUSTOMER-015). **Do not "clean up" the cascade during the port** — see `errors.md` §5. | High | BR-CUSTOMER-014, `01` §1.3 |
| W4 | **The nested-person patch capability is dead code.** `CustomersPatchServiceImpl.patchExistingEntity(Map, Customer)` is fully implemented and tested, but **no controller exposes PATCH** for `/data/customers`, so no request can ever reach it (BR-CUSTOMER-010, BR-CUSTOMER-011). It must still be ported — it is reachable via `CrudGenericService.partialUpdate`, which the generic base exposes but this controller does not map. | Medium | BR-CUSTOMER-010, BR-CUSTOMER-011, `02` §5 |
| W5 | **`DataCustomersController.delete` is missing `@RequestParam` on its `Map` parameter**, while `DataSalespeopleController.delete` has it. The binding still works by Spring's `Map` convention, but the two sibling controllers are not symmetric. Port the asymmetry verbatim (G2) — do not tidy it. | Medium | `02` §6 |
| W6 | **`Customer.person` is EAGER.** `@OneToOne` does not declare `fetch`, and the JPA default for `@OneToOne` is EAGER, so every customer read joins `people` whether or not the caller needs it (BR-CUSTOMER-022). This is load-bearing for the converter, which dereferences `getPerson()` unconditionally. | Medium | BR-CUSTOMER-022 |
| W7 | **The copy constructor copies `id`.** `new Customer(existing)` keeps the id **and** deep-copies the person including `person.id` — unlike `Product`/`ProductCategory`, which omit it. The patch path depends on this: `flushPartialChanges` compares the copy to the original with Lombok `equals`, which **includes** `id` and `person`. | Medium | `01` §1.4 |
| W8 | **`Utils.copyMapWithUnprefixedEntries` replaces every occurrence of the prefix, not just the leading one** (BR-CUSTOMER-012), so a patch key such as `person.person.email` is mangled. Shared-kernel behaviour; preserved. | Low | BR-CUSTOMER-012 |
| W9 | **Guest checkout creates a permanent customer row** (BR-CUSTOMER-017) and a repeat guest silently reuses it (BR-CUSTOMER-018). After the Phase 0 D14 fix this write happens **after** authentication, in `GuestCustomerProvisioningServiceImpl`, which swallows `EntityExistsException` to stay idempotent. | Low | BR-CUSTOMER-017, BR-CUSTOMER-018, `02` §8 |
| W10 | **No `"id"` sort key exists**, and `"name"` sorts by **`lastName`**, not by first name or a concatenation. Both sort specs in Identity share this shape. | Low | `02` §7 |

No warning in this table is fixed during the port (G1).

---

## 3. File coverage

### 3.1 `customer` — all files

| # | Source file | Role | Covered in |
|---|---|---|---|
| 1 | `jpa/entities/Customer.java` | entity | `01` §1 — ✅ Full |
| 2 | `jpa/repositories/CustomersRepository.java` | repository | `02` §2 — ✅ Full |
| 3 | `jpa/services/crud/CustomersCrudService.java` | contract | `02` §1 — ✅ Full |
| 4 | `jpa/services/crud/impl/CustomersCrudServiceImpl.java` | service | `02` §3 — ✅ Full |
| 5 | `jpa/services/conversion/CustomersConverterService.java` | contract | `02` §1 — ✅ Full |
| 6 | `jpa/services/conversion/impl/CustomersConverterServiceImpl.java` | service | `02` §4 — ✅ Full |
| 7 | `jpa/services/patch/CustomersPatchService.java` | contract | `02` §1 — ✅ Full |
| 8 | `jpa/services/patch/impl/CustomersPatchServiceImpl.java` | service | `02` §5 — ✅ Full |
| 9 | `jpa/services/predicates/CustomersPredicateService.java` | contract | `02` §1 — ✅ Full |
| 10 | `jpa/services/predicates/impl/CustomersPredicateServiceImpl.java` | service | `02` §7 — ✅ Full |
| 11 | `jpa/sortspecs/CustomersSortSpec.java` | helper | `02` §7 — ✅ Full |
| 12 | `api/controllers/DataCustomersController.java` | controller | `02` §6 — ✅ Full |
| 13 | `api/services/GuestCustomerProvisioningService.java` + `api/services/impl/GuestCustomerProvisioningServiceImpl.java` | service (Phase 0 / D14) | `02` §8 — ✅ Full |

Paths relative to `src/main/java/org/trebol/`. **Coverage: 13/13 (100%).**

`api/models/PersonPojo.java` and `jpa/entities/Person.java` are the person domain's and
are specified at porting depth in `detailed-business-specification/domain-person/`. They
are **cited, not re-specified** here; `01` §2 records only what this domain depends on.

### 3.2 Outbound coupling census — the extraction-critical result

Every import block of all 13 files was examined for
`order|billing|shipper|product|image|user`. **Four hits, all false positives:**
`com.querydsl.core.types.OrderSpecifier` in `CustomersSortSpec` and in
`DataCustomersController` (and the two salesperson equivalents).

Zero imports of `Order*`, `BillingCompany`, `BillingType`, `Shipper`, `Product`, `Image`,
`User` or `UserRole`, and zero imports of their repositories or services.

**Consequence for Phase 2:** `customer` has no outbound seam to cut. It depends only on
`person`, which the approved grouping puts in the same service.

### 3.3 Inbound coupling — four consumers, three resolved, one deliberate

| Consumer | Shape | Status |
|---|---|---|
| `CustomerProvisioningContractImpl` → `OrdersConverterServiceImpl` | **the published contract** (`resolveOrCreateCustomer`, `describeCustomer`), keyed by `PersonPojo` | **closed in Phase 2** — this is the HS-02 fix |
| `JwtGuestAuthenticationFilter` → `GuestCustomerProvisioningService` | an authentication filter triggering a business write | **resolved in Phase 0 (D14)** — write moved behind a transactional service and now runs *after* authentication. Both sides land in Identity, so the coupling is intra-service |
| `RegistrationServiceImpl` → `CustomersRepository` | **direct repository injection** | **deliberate, and correct after Phase 2**: both classes are Identity's, and co-locating them is exactly what lets registration be a single transaction (ADR-001 §Decision.1). This is intra-service coupling, not a boundary violation |
| `Order.customer` | a real `@ManyToOne` to `Customer`, backed by FK `FK_ORDER_CUSTOM_ID` | **Phase 3** — one of the two inbound FKs the definition cuts when Orders is extracted |

`BR-CUSTOMER-016` ("every order must reference a customer") is therefore an **order-domain**
rule that constrains Phase 3, not a customer-domain rule to be enforced here.

### 3.4 Shared-kernel files read but not owned

`jpa/services/crud/CrudGenericService.java` (every CRUD behaviour not overridden here),
`api/DataCrudGenericController.java` (the write-capable base and the Phase-0 D1 guard),
`api/DataGenericController.java`, `api/DataCrudController.java`, `api/DataController.java`,
the five generic service contracts, `jpa/Repository.java`, `jpa/DBEntity.java`,
`api/services/impl/PaginationServiceImpl.java`,
`jpa/services/impl/SortSpecParserServiceImpl.java`, `common/exceptions/BadInputException.java`,
`config/Constants.java` (`PERSON_DATA_MAP_KEYS_PREFIX`), `common/Utils.java`,
`config/ApiProperties.java`, and `security/authorities/AuthorityCodes.java` — which lives
in the **shared-contracts module**, not in monolith source.

---

## 4. Entity

| Entity | Table | Ownership | Columns | Relationships |
|---|---|---|---|---|
| `Customer` | `customers` (`UNIQUE KEY UK_CUSTOM_PERSON_ID`) | owned | 1 + 1 FK | 1 — **EAGER** `@OneToOne` to `Person`, `optional = false`, `cascade = ALL` |

`serialVersionUID = 4L`. No inheritance, no enumeration, no state field, no
domain-specific exception type, and **no lifecycle callbacks**.

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, the coupling census, the Phase 2 checklist |
| `01-customer-entity-and-transport.md` | behavioural spec: the entity, and `PersonPojo` as this domain's transport model |
| `02-customer-services.md` | behavioural spec: repository, CRUD with its single override, converter, patch, predicate, sort spec, controller, guest provisioning — plus the G8 test inventory |
| `errors.md` | every error condition with its literal message; the deliberate non-errors |
| `permissions.md` | the four customer authorities and the class-level `isAuthenticated()` |

The authoritative rule set already exists at
`business-rules-extraction/detailed-domain-bre/domain-customer/business-rules.md`. This
spec adds the porting depth it lacks: verbatim literals, signatures, step sequences and
defect positions.

---

## 6. Rule traceability

All 22 `BR-CUSTOMER-*` rules (001–022, contiguous) are referenced by id across this
document, `01`, `02`, `errors.md` and `permissions.md`. No rule is unreferenced, which is
what keeps exit criterion X4 verifiable after the port.

**One contradiction found while writing this spec**, and recorded rather than fixed:
**BR-CUSTOMER-014** states that deleting a customer does not delete its person, while
`Customer.person` declares `cascade = CascadeType.ALL`, which would cascade the delete.
See §2 W3 and `errors.md` §5.3. The code is authoritative for the port (G1, G2);
the rule text needs the owner's correction.

---

## 7. Phase 2 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 13 files, **identical fully-qualified names**. |
| Literals | `"Customer does not have an ID card"`, `"This method is deprecated"`, `"This method has been deprecated"`, the inherited `"The item already exists"` / `"Requested item(s) not found"` / `"Cannot update more than one item at a time"` / `"This method signature has been deprecated"`, the guest log line `"Guest with idNumber={} is already registered in the database"`, the 11 filter parameter names, the 7 sort keys and the four authority constants all port verbatim (G2). |
| Tables | `customers` moves with DDL, column types and constraint names unchanged — **including `UNIQUE KEY UK_CUSTOM_PERSON_ID`**, which is the only database-level expression of BR-CUSTOMER-001. Its FK points at `people`, inside Identity. The `orders.customer_id` FK points **inward** and is cut in Phase 3. |
| Seed data | None. `customers` is not seeded; the 4 demonstration accounts are **not** carried into committed configuration (G7) — see `PHASE-2-EXIT.md`. |
| Tests | `DataCustomersControllerTest` (6), `CustomersConverterServiceImplTest` (2), `CustomersCrudServiceImplTest` (1), `CustomersPatchServiceImplTest` (1), `CustomersPredicateServiceImplTest` (1), `GuestCustomerProvisioningServiceImplTest` (3), `CustomerProvisioningContractImplTest` (4), plus the `CustomersTestHelper` fixture and `TestConstants` (G8). |
| Behaviour | No warning in §2 is fixed during the port (G1). W3 is recorded, not corrected. |
