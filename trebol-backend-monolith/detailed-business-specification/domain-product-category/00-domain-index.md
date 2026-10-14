# Product Category Tree — Domain Index

Domain slug: `product-category` · Complexity: medium · 14 source files · 903 LOC ·
1 owned table · 1 entity · **5 HTTP endpoints** · 20 business rules

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "product-category"`.

Produced for **Phase 1 (Catalogue extraction)** of the microservices conversion. The
definition records that only `domain-person` and `domain-image` existed at porting
depth and that each phase must produce the specs for the domains it touches.

---

## 1. Domain summary

**Purpose.** A single-parent category tree used to classify products and to drive
branch-aware product search (cross-domain feature CDF-05). A category is a `code`, a
`name` and an optional `parent`.

**Core invariant, as implemented.** `code` is globally unique and is the **only** key
the service layer resolves by (BR-PRODCAT-003). `name` is unique only among siblings,
enforced by a composite table constraint (BR-PRODCAT-001, BR-PRODCAT-002).

**Boundaries.** A pure leaf: `depends_on_domains` is empty and no product-category
class references any other domain — verified by import census, §3.2. The only inbound
coupling is from `product`.

**Key entity.** `ProductCategory` → table `product_categories`. Self-referencing via
`parent_product_category_id`; there is **no inverse `children` collection anywhere**.

**Key services.** One repository, one converter, one patch service, one CRUD service,
one predicate service, one sort-spec helper, one tree-resolver service, and a
write-capable controller exposing all five CRUD operations. Three of the four
domain-specific service contracts are empty type bindings; behaviour lives in the
shared-kernel generic bases plus three overrides (`getExisting`,
`flushPartialChanges`, the converter's parent resolution).

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **Nothing prevents a cycle.** The converter resolves a parent by code with no check that the parent is not the category itself and no ancestor walk. `PUT` blindly overwrites the parent. A cycle is therefore creatable, is never detected, and is bounded only by the recursion-depth setting — which masks the corruption instead of reporting it. | **Highest** | BR-PRODCAT-009, BR-PRODCAT-010 |
| W2 | **Parent resolution is fail-silent.** An unknown, blank or null parent code makes the category a **root**, with no error. The caller believes it created a child. | High | BR-PRODCAT-008 |
| W3 | **An unresolvable `rootCode` yields no filter, not no results.** `getBranchIdsFromRootCode` returns an empty list for an unknown code, and the predicate applies the `in` clause only when the list is non-empty — so the filter silently disappears and the query **matches everything**. The sibling `rootId` path behaves differently: an empty list is applied and matches nothing. | High | BR-PRODCAT-011 |
| W4 | **A patch can never re-parent or detach.** `flushPartialChanges` restores the original parent after patching, and the patch service handles no `parent` key at all. Re-parenting is possible only through full `PUT` replacement, which is also the only path that can write a cycle (W1). | Medium | BR-PRODCAT-006, BR-PRODCAT-007, BR-PRODCAT-008 |
| W5 | **Identity filters short-circuit.** `id` and `code` return immediately from the parameter loop, discarding every other supplied filter — on writes as well as reads, because writes are addressed by query parameters. | Medium | BR-PRODCAT-013 |
| W6 | **Equality includes the parent and recurses.** Lombok `@EqualsAndHashCode` has no exclusions, so comparing two categories compares their parents transitively. Combined with W1 a cycle can produce a `StackOverflowError` on an equality check. `toString` was hand-written specifically to avoid this; `equals` was not. | Medium | §1.5 of `01` |
| W7 | **`getBranchesFromRoot(ProductCategory)` has no production caller.** It is the dead API the manifest refers to: the orphaning operation that would make category deletion safe is never invoked, so deletion is instead blocked by the database FK. | Low | BR-PRODCAT-016 |

Delete-path hazard **D1 is already fixed** in Phase 0: `DataCrudGenericController.delete`
now rejects a request whose filtering parameters contain no recognised selector instead
of applying a value-less predicate as match-all. The fix ports with the code.

---

## 3. File coverage

All 14 files in the manifest's `source_files[]` are documented, **plus the repository**,
which the manifest omits.

| # | Source file | Role | Covered in |
|---|---|---|---|
| 1 | `jpa/entities/ProductCategory.java` | entity | `01` §1 — ✅ Full |
| 2 | `api/models/ProductCategoryPojo.java` | transport model | `01` §2 — ✅ Full |
| 3 | `jpa/repositories/ProductsCategoriesRepository.java` | repository (**not in manifest**) | `02` §2 — ✅ Full |
| 4 | `jpa/services/conversion/ProductCategoriesConverterService.java` | contract | `02` §1 — ✅ Full |
| 5 | `jpa/services/conversion/impl/ProductCategoriesConverterServiceImpl.java` | service | `02` §4 — ✅ Full |
| 6 | `jpa/services/crud/ProductCategoriesCrudService.java` | contract | `02` §1 — ✅ Full |
| 7 | `jpa/services/crud/impl/ProductCategoriesCrudServiceImpl.java` | service | `02` §3 — ✅ Full |
| 8 | `jpa/services/patch/ProductCategoriesPatchService.java` | contract | `02` §1 — ✅ Full |
| 9 | `jpa/services/patch/impl/ProductCategoriesPatchServiceImpl.java` | service | `02` §5 — ✅ Full |
| 10 | `jpa/services/predicates/ProductCategoriesPredicateService.java` | contract | `02` §6.1 — ✅ Full |
| 11 | `jpa/services/predicates/impl/ProductCategoriesPredicateServiceImpl.java` | service | `02` §6.2 — ✅ Full |
| 12 | `jpa/services/ProductCategoryTreeResolverService.java` | contract | `02` §8.1 — ✅ Full |
| 13 | `jpa/services/impl/ProductCategoryTreeResolverServiceImpl.java` | service | `02` §8.2 — ✅ Full |
| 14 | `jpa/sortspecs/ProductCategoriesSortSpec.java` | helper | `02` §7 — ✅ Full |
| 15 | `api/controllers/DataProductCategoriesController.java` | controller | `02` §9 — ✅ Full |

Paths relative to `src/main/java/org/trebol/`. **Coverage: 15/15 (100%).**

### 3.1 Shared-kernel files read but not owned

| File | Why read | Behaviour specified in |
|---|---|---|
| `jpa/services/crud/CrudGenericService.java` | supplies all CRUD behaviour except `getExisting` and `flushPartialChanges` | `02` §3.2–§3.7 |
| `api/DataCrudGenericController.java` | the write-capable base, including the two literal guard messages and the Phase-0 D1 delete guard | `02` §9 |
| `api/DataGenericController.java`, `api/DataCrudController.java`, `api/DataController.java` | the read pipeline and the controller contracts | `02` §9 |
| `jpa/services/{CrudService,ConverterService,PatchService,PredicateService,SortSpecParserService}.java` | the generic contracts this domain binds | `02` §1 |
| `jpa/Repository.java`, `jpa/DBEntity.java` | generic persistence contract and entity marker | `02` §2, `01` §1 |
| `api/services/impl/PaginationServiceImpl.java`, `jpa/services/impl/SortSpecParserServiceImpl.java` | page and sort resolution | `02` §7 |
| `config/ApiProperties.java` | the recursion-depth bound | `02` §8.2 |
| `security/authorities/AuthorityCodes.java` | the authority constants (Phase 0, Gate B) | `permissions.md` |

### 3.2 Outbound coupling census — the extraction-critical result

Every import block of all 15 files was examined. **Zero references to `order`,
`person`, `user`, `billing`, `salesperson`, `shipper`, `customer` or `image` code.**
The only in-project non-kernel imports are this domain's own types plus
`org.trebol.common.exceptions.BadInputException`, `org.trebol.config.ApiProperties`,
`org.trebol.api.models.DataPagePojo` and `org.trebol.security.authorities.AuthorityCodes`.

Inbound (owned by `product`, documented there): `ProductPojo`,
`ProductsConverterServiceImpl`, `ProductsPredicateServiceImpl` (the CDF-05
branch-aware search, via `ProductCategoryTreeResolverService`), and the `Product`
entity's FK, which is what blocks category deletion.

**Consequence for Phase 1:** this domain moves into `catalogue-service` with no
contract seam required in either direction.

---

## 4. Entities

| Entity | Table | Ownership | Columns | Relationships |
|---|---|---|---|---|
| `ProductCategory` | `product_categories` | owned by this domain | 4 | 1 — self-referencing `@ManyToOne` parent, LAZY, no cascade, nullable. No inverse collection. |

No abstract base types, no inheritance, no enumerations, no state field, no
domain-specific exception type, **no lifecycle callbacks**.

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, inventory |
| `01-product-category-entity-and-transport.md` | behavioural spec: entity and transport model |
| `02-product-category-services.md` | behavioural spec: repository, CRUD, converter, patch, predicate, sort helper, tree resolver, controller |
| `errors.md` | every error condition with its literal message, and the deliberate non-errors |
| `permissions.md` | the four authorities, what each grants, and the unenforced read authority |

Diagram and `specs/` sub-suites are not reproduced: the authoritative rule set already
exists at `business-rules-extraction/detailed-domain-bre/domain-product-category/`
(20 rules, validations, workflows, features, integrations) and the ER layout is in
`business-rules-extraction/database-documentation/00-global-schema.sql`. This spec adds
the porting depth those documents lack — verbatim literals, signatures, step sequences
and defect positions.

---

## 6. Rule traceability

All 20 rules of
`business-rules-extraction/detailed-domain-bre/domain-product-category/business-rules.md`
are referenced by id across `01`, `02`, `errors.md` and `permissions.md`:
BR-PRODCAT-001 … BR-PRODCAT-020. No rule is unreferenced, which is what keeps exit
criterion X4 verifiable after the port.

---

## 7. Phase 1 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 15 files port with **identical fully-qualified names**. Renaming is forbidden by the API-compatibility guardrail and identical FQCNs make the port a verifiable copy. |
| Literals | Every message, authority constant, sort key and filter parameter name ports verbatim (G2). |
| Table | `product_categories` moves to the catalogue service's schema with its DDL, column types and constraint names unchanged. It has no FK pointing out of the catalogue. |
| Tests | All 6 test classes plus `ProductCategoriesTestHelper` and the `DataCrudGenericControllerTest` base port with the code (G8). |
| Behaviour | No defect in §2 is fixed during the port. W1–W7 each already carry a register ruling; silent fixes are forbidden by G1. |
