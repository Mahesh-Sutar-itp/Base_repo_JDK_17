# Salesperson — Domain Index

Domain slug: `salesperson` · Complexity: low (surface) / medium (boundary) · 11 source files ·
1 owned table · 1 entity · **4 HTTP endpoints** · 23 business rules

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "salesperson"`.

Produced for **Phase 2 (Identity extraction)**. Only `domain-person` and `domain-image`
existed at porting depth; the definition requires each phase to produce the specs for the
domains it touches.

---

## 1. Domain summary

**Purpose.** A `salesperson` is a **pure join row**: a surrogate id and a mandatory link
to a `person`. It carries **no attributes of its own** (BR-SALESPERSON-004). It marks a
person as someone who can be credited as the selling party on an order.

**It is not a login account** (BR-SALESPERSON-020). There is no relationship whatsoever
between `salespeople` and `app_users`; a salesperson cannot authenticate, and a user is
not a salesperson. The two tables are unrelated inside the same service.

**Core invariant, as implemented — and it is the *absence* of one.**
`salespeople.person_id` has **no unique constraint**, and the entity uses `@ManyToOne`,
so **several salesperson rows may legitimately share one person** (BR-SALESPERSON-002).
This is the single most important difference from `customer`, whose `@OneToOne` plus
`UK_CUSTOM_PERSON_ID` forbid exactly that. See §2 W1.

**Boundaries.** Outbound coupling is **zero** — verified by import census, §3.2. All
coupling is inbound, from the order domain, and is cut in Phase 3.

**Key entity.** `Salesperson` → `salespeople`.

**Key services.** One repository with a single declared query, one CRUD service with
**exactly one override** (`getExisting`), one converter delegating wholly to the person
domain, one patch service **unreachable by any route**, one predicate service, one sort
spec, and a write-capable controller exposing four CRUD operations.

**Transport model.** There is **no `SalespersonPojo`**. Every generic parameter is
`PersonPojo` (BR-SALESPERSON-008).

This domain is a near-twin of `customer`. The specification is written standalone, but
every place the two **differ** is marked **⚠ DIFFERS** — those are the points where an
engineer unifying the two would introduce a behavioural change.

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **⚠ DIFFERS — `salespeople.person_id` is NOT unique, and that is load-bearing.** The entity declares `@ManyToOne` and the DDL has only `FK_SPERSON_PERSON_ID`, no unique key (BR-SALESPERSON-002). Yet `SalespeopleRepository.findByPersonIdNumber` returns `Optional` — so two salespeople sharing a person makes that query **throw a non-unique-result exception**. It is protected only by `people.person_id_number` being globally unique. An engineer "harmonising" the three identity tables would either add a unique key here (a behavioural change, rejecting currently-valid data) or drop it from `customers` (silently breaking BR-CUSTOMER-001). **Change neither.** | High | BR-SALESPERSON-002, `01` §1.5 |
| W2 | **An order carrying salesperson data can create or modify the `salespeople` row.** `Order.salesperson` is a `@ManyToOne` with `cascade`, so order-side writes reach this table (BR-SALESPERSON-016) — even though **no route ever sets an order's salesperson** (BR-SALESPERSON-015). The write path exists and is reachable only through the order domain. After Phase 2 this is a **cross-service write**, and it is the reason `SalespersonLookupContract` is read-only while the order side still holds the association until Phase 3. | High | BR-SALESPERSON-015, BR-SALESPERSON-016, §3.3 |
| W3 | **`PUT` is a full overwrite that silently orphans a `people` row.** Identical mechanism to the customer domain: the rebuilt nested `Person` has `id == null`, only the outer id is set, and `cascade = ALL` **inserts a new `people` row** (BR-SALESPERSON-013). The old row is orphaned and nothing deletes it. | High | BR-SALESPERSON-013, `02` §3.3 |
| W4 | **`cascade = CascadeType.ALL` means a salesperson delete cascades to the person**, contradicting BR-SALESPERSON-018. In practice the FK from `orders` blocks the delete first (BR-SALESPERSON-019), as a 500 rather than a 409. Recorded, not fixed — `errors.md` §5.4. | High | BR-SALESPERSON-018, BR-SALESPERSON-019 |
| W5 | **The patch service is dead code.** Fully implemented and tested, but no controller maps PATCH for `/data/salespeople` (BR-SALESPERSON-010, BR-SALESPERSON-011). Port it regardless. | Medium | BR-SALESPERSON-011, `02` §5 |
| W6 | **⚠ DIFFERS — the patch service hardcodes `"person."`** as a string literal instead of using `Constants.PERSON_DATA_MAP_KEYS_PREFIX`, which the customer equivalent uses (BR-SALESPERSON-012). Identical behaviour, divergent source. Port the divergence verbatim; do not unify. | Medium | BR-SALESPERSON-012 |
| W7 | **⚠ DIFFERS — `personPath` is `private static` here**, `public static` in `CustomersPredicateServiceImpl`. Nothing outside reads it, but the modifier is a visible API difference on a public class. | Low | `02` §7.1 |
| W8 | **⚠ DIFFERS — the constructor parameter is named `sortService`**, versus `sortSpecParserService` in the customer controller. Cosmetic, and listed only so a reviewer diffing the two files is not surprised into "fixing" it. | Low | `02` §6 |
| W9 | **`Salesperson.person` is EAGER** — `@ManyToOne` defaults to EAGER and `fetch` is not declared (BR-SALESPERSON-023). The converter dereferences `getPerson()` unconditionally and depends on it. | Medium | BR-SALESPERSON-023 |
| W10 | **The copy constructor copies `id`** and deep-copies the person including `person.id`. `flushPartialChanges` compares with Lombok `equals`, which includes both fields, so a no-op patch skips the write. | Medium | `01` §1.4 |
| W11 | **Orders cannot be sorted by salesperson** — `OrdersSortSpec` offers customer and shipper keys but no salesperson key (BR-SALESPERSON-017). An order-domain gap, recorded here because it is the kind of thing a "while we're at it" change would add. It stays absent. | Low | BR-SALESPERSON-017 |
| W12 | **No `"id"` sort key**, and `"name"` sorts by **`lastName`** — same shape as the customer sort spec. | Low | `02` §7.2 |

No warning in this table is fixed during the port (G1).

---

## 3. File coverage

### 3.1 `salesperson` — all files

| # | Source file | Role | Covered in |
|---|---|---|---|
| 1 | `jpa/entities/Salesperson.java` | entity | `01` §1 — ✅ Full |
| 2 | `jpa/repositories/SalespeopleRepository.java` | repository | `02` §2 — ✅ Full |
| 3 | `jpa/services/crud/SalespeopleCrudService.java` | contract | `02` §1 — ✅ Full |
| 4 | `jpa/services/crud/impl/SalespeopleCrudServiceImpl.java` | service | `02` §3 — ✅ Full |
| 5 | `jpa/services/conversion/SalespeopleConverterService.java` | contract | `02` §1 — ✅ Full |
| 6 | `jpa/services/conversion/impl/SalespeopleConverterServiceImpl.java` | service | `02` §4 — ✅ Full |
| 7 | `jpa/services/patch/SalespeoplePatchService.java` | contract | `02` §1 — ✅ Full |
| 8 | `jpa/services/patch/impl/SalespeoplePatchServiceImpl.java` | service | `02` §5 — ✅ Full |
| 9 | `jpa/services/predicates/SalespeoplePredicateService.java` + `impl/SalespeoplePredicateServiceImpl.java` | predicate | `02` §7.1 — ✅ Full |
| 10 | `jpa/sortspecs/SalespeopleSortSpec.java` | helper | `02` §7.2 — ✅ Full |
| 11 | `api/controllers/DataSalespeopleController.java` | controller | `02` §6 — ✅ Full |

Paths relative to `src/main/java/org/trebol/`. **Coverage: 11/11 (100%).**

The Phase 2 seam `jpa/services/contracts/SalespersonLookupContract` +
`impl/SalespersonLookupContractImpl` is this domain's published boundary and is specified
in `02` §8.

`Person` and `PersonPojo` belong to the person domain and are cited, not re-specified.

### 3.2 Outbound coupling census

Every import block of all 11 files was examined for
`order|billing|shipper|product|image|user|customer`. **Two hits, both false positives:**
`com.querydsl.core.types.OrderSpecifier` in `SalespeopleSortSpec` and in
`DataSalespeopleController`.

Zero imports of `Order*`, `BillingCompany`, `BillingType`, `Shipper`, `Product`, `Image`,
`User`, `UserRole` or `Customer`. **This domain has no outbound seam to cut** — notably,
it does not reference `customer` at all despite being its structural twin.

### 3.3 Inbound coupling — one consumer, one seam, two Phase 3 items

| Consumer | Shape | Status |
|---|---|---|
| `SalespersonLookupContractImpl` → `OrdersConverterServiceImpl` | **the published contract** (`describeSalesperson`), read-only | **closed in Phase 2** — part of the HS-02 fix |
| `Order.salesperson` | a real `@ManyToOne` to `Salesperson` with cascade, backed by FK `FK_ORDER_SPERSON`, **nullable** | **Phase 3** — the second of the two inbound FKs the definition cuts |
| `OrderPojo.salesperson` | embeds `PersonPojo` | **Phase 3** |

BR-SALESPERSON-014 (an order may have no salesperson) is what makes the Phase 3 cut
cheap: the FK is already nullable, and in practice always null because
BR-SALESPERSON-015 holds — no route sets it.

**The write reachable through `Order.salesperson`'s cascade (W2) is the one genuine
distributed-write risk this domain hands to Phase 3.** Because no route exercises it, the
Orders extraction can cut the association without a compensating contract method — but
that must be a recorded decision, not an assumption.

### 3.4 Shared-kernel files read but not owned

Identical to the customer domain's list: `CrudGenericService`,
`DataCrudGenericController` (and the Phase-0 D1 guard), `DataGenericController`,
`DataCrudController`, `DataController`, the five generic service contracts,
`jpa/Repository.java`, `jpa/DBEntity.java`, `PaginationServiceImpl`,
`SortSpecParserServiceImpl`, `BadInputException`, `common/Utils.java`,
`config/ApiProperties.java`, and `AuthorityCodes` from the shared-contracts module.

**⚠ DIFFERS:** this domain does **not** read `config/Constants.java`, because it
hardcodes the person prefix (W6).

---

## 4. Entity

| Entity | Table | Ownership | Columns | Relationships |
|---|---|---|---|---|
| `Salesperson` | `salespeople` (**no unique key**) | owned | 1 + 1 FK | 1 — **EAGER** `@ManyToOne` to `Person`, `optional = false`, `cascade = ALL` |

`serialVersionUID = 13L`. No inheritance, no enumeration, no state field, no
domain-specific exception type, and **no lifecycle callbacks**.

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, the coupling census, the Phase 2 checklist |
| `01-salesperson-entity-and-transport.md` | behavioural spec: the entity, the uniqueness asymmetry, and `PersonPojo` as transport |
| `02-salesperson-services.md` | behavioural spec: repository, CRUD, converter, patch, predicate, sort spec, controller, the published contract — plus the G8 test inventory |
| `errors.md` | every error condition with its literal message; the deliberate non-errors |
| `permissions.md` | the four salesperson authorities and the class-level `isAuthenticated()` |

The authoritative rule set already exists at
`business-rules-extraction/detailed-domain-bre/domain-salesperson/business-rules.md`.

---

## 6. Rule traceability

All 23 `BR-SALESPERSON-*` rules (001–023, contiguous) are referenced by id across this
document, `01`, `02`, `errors.md` and `permissions.md`. No rule is unreferenced, which is
what keeps exit criterion X4 verifiable after the port.

**One contradiction found while writing this spec**, recorded rather than fixed:
**BR-SALESPERSON-018** states that deleting a salesperson does not delete its person,
while `Salesperson.person` declares `cascade = CascadeType.ALL`. Same shape as the
customer domain's BR-CUSTOMER-014 contradiction. See §2 W4 and `errors.md` §5.4.

---

## 7. Phase 2 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 11 files plus the contract pair, **identical fully-qualified names**. |
| Literals | `"Salesperson does not have an ID card"`, `"This method is deprecated"`, `"This method has been deprecated"`, the hardcoded `"person."` prefix, the inherited kernel messages, the 11 filter parameter names, the 7 sort keys and the four authority constants all port verbatim (G2). |
| Tables | `salespeople` moves with DDL and constraint names unchanged — **and with no unique key on `person_id`** (W1). Its FK points at `people`, inside Identity. The `orders.salesperson_id` FK points **inward** and is cut in Phase 3. |
| Seed data | None. `salespeople` is not seeded. |
| Tests | `DataSalespeopleControllerTest` (6), `SalespeopleConverterServiceImplTest` (2), `SalespeopleCrudServiceImplTest` (1), `SalespeoplePatchServiceImplTest` (1), `SalespeoplePredicateServiceImplTest` (1), `SalespersonLookupContractImplTest` (1), plus the `SalespeopleTestHelper` fixture and `TestConstants` (G8). |
| Behaviour | No warning in §2 is fixed during the port (G1). W1, W4 and W6 are the three most likely to be "tidied" by accident — they are not. |
