# Billing — Domain Index

Domain slug: `billing` · Complexity: low (surface) / medium (boundary) · 16 source files ·
4 owned tables · 4 entities · **1 HTTP endpoint** · 19 business rules

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "billing"`.

Produced for **Phase 3 (Orders extraction)**. The definition requires `order` and `billing`
specs partitioned by subsystem and produced before extraction; this is the billing half.

---

## 1. Domain summary

**Purpose.** Everything about *how an order is invoiced and where it is sent*: the billing
type (individual or enterprise), the billing company for an enterprise invoice, the postal
addresses used for billing and shipping, and the payment type.

**It is almost entirely invisible over HTTP.** Four entities, four tables — and **one single
read endpoint**, `GET /data/billing_types`. Billing companies and addresses have **no
controller at all** (BR-BILLING-014): they exist only as things the checkout path creates on
your behalf.

**Core invariants, as implemented.** Billing-type names are unique (BR-BILLING-001); company
id number *and* company name are each globally unique (BR-BILLING-009); the company id number
is the de-duplication key (BR-BILLING-010); and the billing type on an order is **immutable
after creation** (BR-BILLING-004) because `Order.billingType` is mapped `updatable = false`.

**Boundaries.** Outbound coupling is **zero** — verified by import census, §3.2. Billing is a
leaf domain: all coupling runs *inward*, from the order domain through `BillingContract`.

**Key entities.** `BillingType` → `billing_types`; `BillingCompany` → `billing_companies`;
`Address` → `addresses`; `PaymentType` → `payment_types`.

**Note on `PaymentType`.** It is *physically owned by this domain* — `BillingContract` and its
implementation import `PaymentType` and `PaymentTypesRepository`, and the table has no other
owner. The manifest does not give payment types a domain of their own, so they fold in here.

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **`AddressesRepository.findByFields` compares nullable columns with `=`, so it can never match them.** Three of the six columns — `secondLine`, `postalCode`, `notes` — are nullable, and `= NULL` is never true in SQL. **Any address with a null in those columns is invisible to the only query that looks addresses up**, so checkout silently creates a duplicate row instead of reusing it. The `UK_ADDR` unique constraint spans all six columns, so the duplicate is accepted. | **High** | `02` §2.3, BR-BILLING-015 |
| W2 | **`BillingContract.resolveOrCreateBillingCompany` is a read *or a write*.** It returns an **unsaved** entity when no company matches, which is then cascaded on order save. In-process today; **a remote write inside checkout** once Orders is separate. ADR-001 records it as an open consequence of the approved grouping. | **High** | `02` §4 |
| W3 | **`BillingCompanyPojo` carries no validation annotations whatsoever** (BR-BILLING-013) — not even `@NotBlank` on the id number that is its business key. The blank check exists only in the service layer, and the id number is **never format-validated** (BR-BILLING-012). | Medium | `01` §5.2 |
| W4 | **`GET /data/billing_types` is deliberately unauthenticated** and there is **no billing authority of any kind** in the 54-code catalogue (BR-BILLING-016, BR-BILLING-019). Billing data therefore **cannot be governed by role** — not because the check was forgotten, but because no code exists to check. | Medium | `permissions.md` |
| W5 | **Billing types are read-only over HTTP, and billing companies and addresses are not exposed at all.** There is no POST/PUT/PATCH/DELETE anywhere in this domain. Every write happens as a side effect of checkout. | Medium | BR-BILLING-014, `02` §5 |
| W6 | **`Address.postalCode` is the only address field with no `@Size`**, so the entity permits up to the column's `varchar(255)` while every sibling is bounded at 50 or 100. | Low | `01` §3 |
| W7 | **All four copy constructors copy `id`.** A patched copy therefore carries the same primary key, so `saveAndFlush` is an update rather than an insert. Load-bearing for the patch path. | Medium | `01` §1.3 |
| W8 | **Deleting a billing type referenced by an order is blocked by the database, not by application logic** (BR-BILLING-017), and surfaces as a `DataIntegrityViolationException` → **HTTP 500**, not a 409. | Low | `errors.md` §3 |
| W9 | **No tax calculation and no document generation happen here** (BR-BILLING-018). Tax is computed in the order domain's `updateTotals` at a hardcoded 19%; nothing in this domain produces an invoice document. The domain is named for a capability it does not implement. | Low | BR-BILLING-018 |
| W10 | **Two distinct deprecation literals coexist** — converters throw `"This method is deprecated"`, patch services `"This method has been deprecated"`. Do not normalise them (G2). | Low | `errors.md` §1 |

No warning in this table is fixed during the port (G1).

---

## 3. File coverage

### 3.1 All 16 billing files

| # | Source file | Role | Covered in |
|---|---|---|---|
| 1 | `jpa/entities/BillingType.java` | entity | `01` §1 |
| 2 | `jpa/entities/BillingCompany.java` | entity | `01` §2 |
| 3 | `jpa/entities/Address.java` | entity | `01` §3 |
| 4 | `api/models/BillingTypePojo.java` | transport | `01` §5.1 |
| 5 | `api/models/BillingCompanyPojo.java` | transport | `01` §5.2 |
| 6 | `api/models/AddressPojo.java` | transport | `01` §5.3 |
| 7 | `jpa/repositories/BillingTypesRepository.java` | repository | `02` §2.1 |
| 8 | `jpa/repositories/BillingCompaniesRepository.java` | repository | `02` §2.2 |
| 9 | `jpa/repositories/AddressesRepository.java` | repository | `02` §2.3 |
| 10 | `jpa/repositories/PaymentTypesRepository.java` | repository | `02` §2.4 |
| 11 | `jpa/services/crud/BillingTypesCrudService.java` + `impl/` | CRUD | `02` §3.1 |
| 12 | `jpa/services/crud/BillingCompaniesCrudService.java` + `impl/` | CRUD | `02` §3.2 |
| 13 | `jpa/services/conversion/{BillingTypes,BillingCompanies,Addresses}ConverterService.java` + impls | conversion | `02` §6 |
| 14 | `jpa/services/patch/{BillingTypes,BillingCompanies}PatchService.java` + impls | patch | `02` §7 |
| 15 | `jpa/services/predicates/{BillingTypes,BillingCompanies}PredicateService.java` + impls, `jpa/sortspecs/BillingTypesSortSpec.java` | search | `02` §8 |
| 16 | `api/controllers/DataBillingTypesController.java` | controller | `02` §5 |

Plus the published seam `jpa/services/contracts/BillingContract.java` +
`impl/BillingContractImpl.java` — `02` §4.

**Coverage: 16/16 (100%).** Paths relative to `src/main/java/org/trebol/`.

**Deliberately absent, and worth stating because their absence is behaviour:** there is no
`AddressesCrudService`, no `AddressesPatchService`, no `AddressesPredicateService`, no
`AddressesSortSpec`, no `BillingCompaniesSortSpec`, and no controller for either addresses or
billing companies. `Address` is reachable **only** through `AddressesConverterService` and
`AddressesRepository.findByFields`.

### 3.2 Outbound coupling census

Every import block of all 16 files (and the shipper domain's 10, checked together) was
examined for `order|product|image|user|customer|salesperson|person`. **Four hits across both
domains, all false positives:** `com.querydsl.core.types.OrderSpecifier` in
`DataBillingTypesController`, `BillingTypesSortSpec`, and the two shipper equivalents.

**Zero real outbound dependencies.** Billing is a leaf domain with no seam to cut.

### 3.3 Inbound coupling — the Gate A seam

| Consumer | Shape | Status |
|---|---|---|
| `OrdersConverterServiceImpl` → `BillingContract` | `findBillingTypeByName`, `findPaymentTypeByName`, `resolveOrCreateBillingCompany` | **closed in Phase 0 (Gate A)** |
| `OrdersPatchServiceImpl` → `BillingContract` | `findPaymentTypeByName` | **closed in Phase 0 (Gate A)** |
| `OrdersConverterServiceImpl` → `AddressesRepository` | **direct repository injection**, used for `findByFields` | **intra-service** — addresses are billing-owned and billing lands in the *same* Orders service, so no contract is needed (`02` §2.3) |

All three are inside the future Orders service, so Phase 3 cuts nothing here.

### 3.4 Shared-kernel files read but not owned

`CrudGenericService`, `DataGenericController` (the **read-only** base this domain's one
controller extends), `DataController`, the five generic service contracts,
`jpa/Repository.java`, `jpa/DBEntity.java`, `PaginationServiceImpl`,
`SortSpecParserServiceImpl`, `BadInputException`, `config/ApiProperties.java`, and
`AuthorityCodes` from the shared-contracts module — the last of which this domain
**never uses**, because it has no authorities (W4).

---

## 4. Entities

| Entity | Table | Owned | Columns | Relationships |
|---|---|---|---|---|
| `BillingType` | `billing_types` (`UK_BILL_TYP_NAME`) | yes | 2 | **none** |
| `BillingCompany` | `billing_companies` (`UK_BILL_CY_ID_NO`, `UK_BILL_CY_NAME`) | yes | 3 | **none** |
| `Address` | `addresses` (`UK_ADDR` over all 6 columns, 3 indexes) | yes | 7 | **none** |
| `PaymentType` | `payment_types` (`UK_PAYMT_NAME`) | yes | 2 | **none** |

`serialVersionUID`: `Address` **1L**, `BillingCompany` **2L**, `BillingType` **3L**.

**All four entities have zero relationships and zero lifecycle callbacks.** That is unusual
in this codebase and it is why the domain is simple to extract: nothing here points outward,
and the six foreign keys that reference these tables are all declared on `orders`.

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, coupling census, the Phase 3 checklist |
| `01-billing-entity-and-transport.md` | behavioural spec: all four entities and all three transport models |
| `02-billing-services.md` | behavioural spec: repositories, both CRUD services, the published `BillingContract`, the controller, converters, patch, search — plus the G8 test inventory |
| `errors.md` | every error condition with its literal message; the deliberate non-errors |
| `permissions.md` | the **absence** of billing authorities, and what that means |

---

## 6. Rule traceability

All 19 `BR-BILLING-*` rules are referenced by id across this document, `01`, `02`,
`errors.md` and `permissions.md`.

**Document-order defect found while writing this spec.** The source rule file lists the rules
**out of order**: `…BR-BILLING-016`, then **`BR-BILLING-019`**, then `BR-BILLING-017`,
`BR-BILLING-018`. The true count is **19** with no gaps and no duplicates — `BR-BILLING-019`
is a real, distinct rule ("billing data has no authority codes, so it cannot be governed by
role"), not a mis-numbered duplicate. Recorded so the count is not mistaken for 18.

**Action required of the owner:** reorder the source document. No rule content is wrong.

---

## 7. Phase 3 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 16 files plus the `BillingContract` pair, **identical fully-qualified names**. |
| Literals | `"Billing type has no name"`, `"Billing company has no id number"`, `"This method is deprecated"`, `"This method has been deprecated"`, the inherited kernel messages, the 5 + 3 filter parameter names and the single `name` sort key — verbatim (G2). |
| Tables | `billing_types`, `billing_companies`, `addresses`, `payment_types` move with DDL, index names, unique-key names, engine, charset and collation unchanged — **including `UK_ADDR`'s six-column span** and the `AUTO_INCREMENT=3` / `=2` values that reflect the seeded rows. |
| Foreign keys | **None leave these tables** — all five are pure parents. The six keys that reference them (`FK_ORDER_BILL_TYP_ID`, `FK_ORDER_PAYMT_TYP_ID`, `FK_ORDER_BILL_CY_ID`, `FK_ORDER_ADDR_BILL`, `FK_ORDER_ADDR_SHIP`, `FK_ORDER_SHIPPER_ID`) are declared on `orders`, which moves into the same service. **Nothing to cut.** |
| Seed data | The 2 billing types and 1 payment type, with their identity-restart statements (BR-BILLING-002). |
| Tests | `BillingTypesCrudServiceImplTest`, `BillingCompaniesCrudServiceImplTest`, the three converter tests, both patch tests, both predicate tests, `DataBillingTypesControllerTest` and `BillingContractImplTest` (7 tests), plus the `BillingTestHelper` fixture (G8). |
| Behaviour | No warning in §2 is fixed during the port (G1). **W1 in particular is not fixed** — correcting the null comparison would change which addresses are reused and is a behavioural change needing its own ruling. |
