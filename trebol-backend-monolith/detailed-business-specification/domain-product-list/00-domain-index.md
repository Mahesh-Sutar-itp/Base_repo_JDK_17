# Product Lists — Domain Index

Domain slug: `product-list` · Complexity: medium-high · 21 source files · 2 owned tables ·
2 entities · **9 HTTP endpoints** (5 list + 4 contents) · 30 business rules

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "product-list"`. `depends_on_domains: ["product"]`,
`depended_on_by_domains: []`.

Produced for **Phase 1 (Catalogue extraction)**. Only `domain-person` and
`domain-image` existed at porting depth; the definition requires each phase to produce
the specs for the domains it touches.

---

## 1. Domain summary

**Purpose.** Named, curated collections of products — the mechanism behind storefront
groupings such as "featured" or "on sale". A list is a `code`, a `name` and a `disabled`
flag; membership is a row in a join table pointing at a product.

**Core invariant, as implemented.** `code` and `name` are each globally unique, but the
service layer resolves existence by **`name`** (BR-PRODLIST-003) — the opposite choice
from `product` (barcode) and `product-category` (code). A duplicate **code** therefore
slips past the service guard and fails at the database.

**Boundaries.** This is the **only** catalogue domain with a declared outbound
dependency: it reads `product` in six distinct places (§3.2). Nothing depends on it.
Nothing outside the catalogue is referenced at all.

**Key entities.** `ProductList` → `product_lists`; `ProductListItem` →
`product_list_items` (the membership join to `products`, owned here).

**Key services.** Two repositories, **two** converters, one CRUD service with two
overrides, one patch service, **two** predicate services, **two** sort specs, and
**two** controllers — one conventional, one that bypasses the generic framework entirely.

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **The contents controller has no `@Transactional` anywhere**, yet `updateContents` deletes before it inserts. `deleteByListId` is a bulk JPQL delete carrying its own `@Transactional` on the repository method, so it **commits on its own**; the re-inserts then run as separate transactions. A failure mid-loop — a blank barcode throws — leaves the list **emptied with no rollback**. | **Highest** | BR-PRODLIST-016, `02` §7.3 |
| W2 | **A contents `DELETE` with only `listCode` empties the whole list.** The predicate service ignores unrecognised parameters, so a *mistyped* product filter degrades to "delete everything in this list". The Phase-0 D1 guard (`NO_RECOGNIZED_FILTERING_PARAMS`) lives in `DataCrudGenericController`, which **this controller does not extend**, so it offers no protection here. | **Highest** | BR-PRODLIST-017, BR-PRODLIST-030 |
| W3 | **Duplicate membership is prevented only in application code** — a read-then-write `exists(Example.of(...))` check with **no database unique constraint** on `(product_list_id, product_id)` and no surrounding transaction. Concurrent adds duplicate. | High | BR-PRODLIST-014 |
| W4 | **An unresolvable product barcode is silently skipped** and the request still returns 201 / 204. A replace whose every element is unresolvable **empties the list** and returns 204. | High | BR-PRODLIST-015 |
| W5 | **Existence is resolved by `name`, not `code`**, while `code` is the identifier every other endpoint and the whole contents API address lists by. A duplicate `code` passes the service guard and fails at the database as a 500. | High | BR-PRODLIST-003 |
| W6 | **`PUT` on a list resets `disabled` to `false`.** The converter never maps `disabled` and the transport model has no such field, so a full replacement always writes the primitive default. | Medium | BR-PRODLIST-008, BR-PRODLIST-010 |
| W7 | **The `disabled` flag is never read by any code path** — not by the list controller, not by the contents resolver, not by any predicate. It is mandatory, writable only by accident, and inert. | Medium | BR-PRODLIST-009 |
| W8 | **`ProductListItem`'s two associations are `@ManyToOne` with no `fetch` attribute**, i.e. **EAGER by default**. Every membership row load pulls a full `ProductList` and a full `Product`. | Medium | BR-PRODLIST-020 |
| W9 | **`convertToPojo` issues a `COUNT` query per list row**, so `readMany` is N+1 by construction. Sorting by `totalCount` adds an aggregate subquery per row on top. | Medium | BR-PRODLIST-007, `02` §4.1 |
| W10 | **The contents read's `totalCount` ignores the applied product filters** — it always counts the whole list, so a filtered page reports a total that does not match its own result set. | Medium | `02` §7.1 |
| W11 | **The `PUT` collection body is not `@Valid`**, while the `POST` single body is. Element-level validation is therefore skipped on the bulk path. | Medium | BR-PRODLIST-018 |
| W12 | **`deleteByProductId` has no caller.** Product deletion relies on the database's `ON DELETE CASCADE` instead (BR-PRODLIST-023); the application-level cleanup is dead code. | Low | BR-PRODLIST-024 |
| W13 | **`delete` executes `findAll(filters)` twice** — once to delete children, once to delete parents. | Low | BR-PRODLIST-021 |

---

## 3. File coverage

### 3.1 All 21 manifest files

| # | Source file | Role | Covered in |
|---|---|---|---|
| 1 | `jpa/entities/ProductList.java` | entity | `01` §1 — ✅ Full |
| 2 | `jpa/entities/ProductListItem.java` | entity (membership) | `01` §2 — ✅ Full |
| 3 | `api/models/ProductListPojo.java` | transport model | `01` §3 — ✅ Full |
| 4 | `jpa/repositories/ProductListsRepository.java` | repository | `02` §2.1 — ✅ Full |
| 5 | `jpa/repositories/ProductListItemsRepository.java` | repository | `02` §2.2 — ✅ Full |
| 6 | `jpa/services/conversion/ProductListsConverterService.java` | contract | `02` §1 — ✅ Full |
| 7 | `jpa/services/conversion/ProductListItemsConverterService.java` | contract | `02` §1 — ✅ Full |
| 8 | `jpa/services/conversion/impl/ProductListConverterServiceImpl.java` | service | `02` §4.1 — ✅ Full |
| 9 | `jpa/services/conversion/impl/ProductListItemsConverterServiceImpl.java` | service | `02` §4.2 — ✅ Full |
| 10 | `jpa/services/crud/ProductListCrudService.java` | contract | `02` §1 — ✅ Full |
| 11 | `jpa/services/crud/impl/ProductListsCrudServiceImpl.java` | service | `02` §3 — ✅ Full |
| 12 | `jpa/services/patch/ProductListsPatchService.java` | contract | `02` §1 — ✅ Full |
| 13 | `jpa/services/patch/impl/ProductListPatchServiceImpl.java` | service | `02` §5 — ✅ Full |
| 14 | `jpa/services/predicates/ProductListsPredicateService.java` | contract | `02` §1 — ✅ Full |
| 15 | `jpa/services/predicates/ProductListItemsPredicateService.java` | contract | `02` §1 — ✅ Full |
| 16 | `jpa/services/predicates/impl/ProductListsPredicateServiceImpl.java` | service | `02` §6.1 — ✅ Full |
| 17 | `jpa/services/predicates/impl/ProductListItemsPredicateServiceImpl.java` | service | `02` §6.2 — ✅ Full |
| 18 | `jpa/sortspecs/ProductListsSortSpec.java` | helper | `02` §8.1 — ✅ Full |
| 19 | `jpa/sortspecs/ProductListItemsSortSpec.java` | helper | `02` §8.2 — ✅ Full |
| 20 | `api/controllers/DataProductListsController.java` | controller | `02` §9 — ✅ Full |
| 21 | `api/controllers/DataProductListContentsController.java` | controller | `02` §7, §10 — ✅ Full |

Paths relative to `src/main/java/org/trebol/`. **Coverage: 21/21 (100%).**

**Naming inconsistency to preserve:** the class names mix singular and plural without a
rule — `ProductListCrudService` but `ProductListsCrudServiceImpl`,
`ProductListConverterServiceImpl` but `ProductListsConverterService`,
`ProductListPatchServiceImpl` but `ProductListsPatchService`. All port verbatim;
harmonising them is a forbidden rename.

### 3.2 Outbound coupling census — the extraction-critical result

Imports were checked across all 21 files. **Nothing outside the catalogue is
referenced**: no `Order*`/`Sell*`, no `Person`, `Customer`, `Salesperson`, `User`,
`UserRole`, `BillingType`, `PaymentType`, `Address` or `Shipper`, and nothing from
`org.trebol.payment.*` or `org.trebol.mailing.*`.

Outbound coupling is to **`product` only**, in six places:

| Reader | What is read | Via |
|---|---|---|
| `DataProductListContentsController.addToContents` | `Product` by barcode | `ProductsCrudService.getExisting(ProductPojo)` |
| `DataProductListContentsController.updateContents` | `Product` by barcode, per element | the same contract |
| `DataProductListContentsController` | `Product`, `ProductPojo` | direct entity and model use |
| `ProductListItemsConverterServiceImpl.convertToPojo` | full `Product` → `ProductPojo` | `ProductsConverterService.convertToPojo` |
| `ProductListItemsPredicateServiceImpl.parseMap` | `product.name`, `product.barcode` | QueryDSL path traversal through `QProductListItem.product` |
| `ProductListItemsSortSpec` | `QProduct.name`, `QProduct.barcode` | static order spec |
| `ProductListItem` entity | `@ManyToOne(optional = false) Product product` | an **eager** association and a real FK |

**Consequence for Phase 1:** `product-list` must be extracted in the same service as
`product`. The coupling is not merely a service call — it is a QueryDSL path traversal, a
static sort spec and a non-nullable FK, none of which survive a process boundary. No
contract seam is needed, because both domains land in `catalogue-service`.

Non-domain packages imported: `api/services/PaginationService`,
`jpa/services/SortSpecParserService`, `common/exceptions/BadInputException`,
`jpa/DBEntity`, `jpa/Repository`, `security/authorities/AuthorityCodes`, plus the
`CrudGenericService` and `DataCrudGenericController` bases.

### 3.3 Shared-kernel files read but not owned

`jpa/services/crud/CrudGenericService.java`, `api/DataCrudGenericController.java`,
`api/DataGenericController.java`, `api/DataCrudController.java`, `api/DataController.java`,
the five generic service contracts, `jpa/Repository.java`, `jpa/DBEntity.java`,
`api/services/impl/PaginationServiceImpl.java`,
`jpa/services/impl/SortSpecParserServiceImpl.java`, `api/models/DataPagePojo.java`,
`security/authorities/AuthorityCodes.java`.

---

## 4. Entities

| Entity | Table | Ownership | Columns | Relationships |
|---|---|---|---|---|
| `ProductList` | `product_lists` (two separate single-column unique constraints) | owned | 4 | 1 — `@OneToMany(mappedBy = "list")`, LAZY, **no cascade, no orphanRemoval** |
| `ProductListItem` | `product_list_items` (**no unique constraint at all**) | owned | 1 + 2 FKs | 2 — `@ManyToOne(optional = false)` to `ProductList` and to `Product`, both **EAGER by default** |

No inheritance, no enumerations, no state field, no domain-specific exception type, and
**no lifecycle callbacks on either entity**.

**There is no membership transport model.** Contents are carried as `ProductPojo`, the
*product* domain's model — `ProductListItemsConverterService extends
ConverterService<ProductPojo, ProductListItem>`. The membership row's own id is never
exposed (BR-PRODLIST-013).

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, the coupling census |
| `01-product-list-entity-and-transport.md` | behavioural spec: both entities and the transport model |
| `02-product-list-services.md` | behavioural spec: repositories, converters, CRUD, patch, both predicate services, both sort specs, both controllers, and the full contents path |
| `errors.md` | every error condition with its literal message; the deliberate non-errors |
| `permissions.md` | the five authorities, including the single shared contents authority |

The authoritative rule set already exists at
`business-rules-extraction/detailed-domain-bre/domain-product-list/` (30 rules). This
spec adds the porting depth it lacks: verbatim literals, signatures, step sequences and
defect positions.

---

## 6. Rule traceability

All 30 `BR-PRODLIST-*` rules are referenced by id across this document, `01`, `02`,
`errors.md` and `permissions.md`. No rule is unreferenced, which is what keeps exit
criterion X4 verifiable after the port.

---

## 7. Phase 1 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 21 files, **identical fully-qualified names**, including the inconsistent singular/plural naming. |
| Literals | `"The specified list has no name"`, `"listCode query param is required"`, `"Requested item(s) not found"`, `"This method is deprecated"`, `"This method signature has been deprecated"`, `"Not implemented"`, the inherited kernel messages, the five sort keys across two specs, the ten filter parameter names across two predicate services and the five authority constants all port verbatim (G2). |
| Tables | `product_lists` and `product_list_items` move with DDL, constraint names and column types unchanged. `product_list_items` FKs point at `product_lists` and `products`, both in the catalogue — **no FK leaves the catalogue**. The `ON DELETE CASCADE` on the product FK must be preserved, because BR-PRODLIST-023 depends on it. |
| Tests | All 6 test classes plus `org/trebol/testing/TestConstants` (G8). **No product-list-specific test helper exists.** |
| Behaviour | No warning in §2 is fixed during the port (G1). W1 in particular is tempting to fix by adding `@Transactional`; that is a behavioural change and requires a ruling it does not have. |
