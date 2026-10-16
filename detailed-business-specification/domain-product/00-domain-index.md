# Product Catalogue — Domain Index

Domain slug: `product` · Complexity: high · 16 source files · 1 owned table group ·
2 entities · **5 HTTP endpoints** · 32 business rules
Absorbed appendix: `company-profile` · 5 source files · 1 entity · **1 HTTP endpoint** ·
13 business rules

Manifest entries: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "product"` and where `slug == "company-profile"`.

Produced for **Phase 1 (Catalogue extraction)**. Only `domain-person` and
`domain-image` existed at porting depth; the definition requires each phase to produce
the specs for the domains it touches.

---

## 1. Domain summary

**Purpose.** The sellable-goods catalogue: a product is a `name`, a `barcode`, a
`price`, an optional `description`, two stock counters, an optional category, and a set
of linked images.

**Core invariant, as implemented.** `barcode` and `name` are each globally unique, and
`barcode` is the **only** key the service layer resolves by (BR-PRODUCT-001,
BR-PRODUCT-002). The barcode is also the key the Gate A `ProductLookupContract`
exposes to the order domain, which makes it the single most important identifier in the
whole conversion.

**Boundaries.** Outbound coupling exists in exactly two directions, **both inside the
catalogue**: to `product-category` (classification and branch-aware search) and to
`image` (link rebuild on write). There is **zero** outbound reference to `order`,
`person`, `user`, `billing`, `salesperson`, `shipper` or `customer` — verified by import
census, §3.2. Coupling to `order` is purely inbound.

**Key entities.** `Product` → `products`; `ProductImage` → `product_images` (the join
table to `images`, owned here, not by the `image` domain).

**Key services.** Two repositories plus a third read cross-domain, one converter with a
domain-specific extra method, one patch service, one CRUD service with **four
overrides**, one predicate service, one sort spec, and a write-capable controller
exposing all five CRUD operations.

**Absorbed appendix.** `company-profile` — the `Param` entity over `app_params`, one
repository, one service and one anonymous read endpoint. It is not a service and holds
no write path; it folds in here (§6).

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **The image write path bypasses the entire `image` domain's guards.** `makeProductImageRelationships` calls `getExisting`, then builds a transient `Image` inline via `orElseGet` instead of calling `ImagesCrudService.create` — skipping the duplicate guard, the 409 path and bean validation of `ImagePojo`. | High | BR-PRODUCT-014, `02` §6 |
| W2 | **An unresolvable image is silently dropped and the request still succeeds.** A blank filename makes `getExisting` throw `BadInputException("Invalid filename")`; it is caught, logged at **DEBUG**, and the product create/update returns 201/204 as if nothing happened. | High | BR-PRODUCT-013 |
| W3 | **`PUT` deletes all image links unconditionally**, via `deleteByProductId`, *before* rebuilding — even when the payload carries no `images` at all. A replacement that omits images therefore **strips every image** from the product. | High | BR-PRODUCT-011 |
| W4 | **`PUT` is a full replacement with no field preservation.** `update(input, id)` builds a brand-new entity and sets the id, so any field absent from the body is written as null or primitive 0. Combined with the transport model carrying no `id`, read-modify-write is lossy. | High | BR-PRODUCT-018 |
| W5 | **An unknown category code is silently ignored** — the product is created uncategorised with HTTP 201 and no error. Same fail-silent shape as the category domain's parent resolution. | Medium | BR-PRODUCT-010 |
| W6 | **Price and stock have no lower bound and no decimals.** `price` is a primitive `int`; the pojo declares only `@NotNull`. Negative prices and negative stock are accepted. | Medium | BR-PRODUCT-007, BR-PRODUCT-008 |
| W7 | **Stock is recorded but never acted upon.** Nothing decrements it and nothing checks it; an out-of-stock product can be ordered and paid for. | Medium | BR-PRODUCT-021, BR-PRODUCT-022 |
| W8 | **`deepReadAll(Predicate, Pageable)` ignores its own filter.** The `@Query` never references the predicate, and the `JOIN FETCH` is an inner join that would silently drop uncategorised products. The method has no caller — a loaded gun. | Medium | BR-PRODUCT-032 |
| W9 | **The patch service casts `price`, `currentStock` and `criticalStock` unconditionally.** A null value is an NPE on unboxing and a non-integer a `ClassCastException`, both 500s. The three string fields are blank-guarded; these three are not. | Medium | BR-PRODUCT-023 |
| W10 | **No unique constraint on `(product_id, image_id)`**, so the same image can be linked twice. The read side hides it with `distinct()`. | Low | BR-PRODUCT-015 |
| W11 | **Deleting a product does not delete its image links**, so the FK blocks the delete. The orphaning query that would make it safe (`orphanizeByCategories`) exists but has no caller. | Low | BR-PRODUCT-028 |
| W12 | **`CompanyDetailsPojo` is annotated `@JsonInclude(ALWAYS)`**, so unset profile fields are emitted as explicit nulls. **BR-COMPANY-006 asserts they are omitted.** The code and the recorded rule disagree; see §6.3. | Low | BR-COMPANY-006 |

Delete-path hazard **D1 is already fixed** in Phase 0 and the fix ports with the code.
Image duplicate detection **D7 is already fixed**: `ImagesCrudServiceImpl.getExisting`
now checks filename, then `code`, then `url`, first match wins — which changes what W1's
`orElseGet` resolves and is the one place where Phase 0 altered this path's behaviour.

---

## 3. File coverage

### 3.1 `product` — all 16 manifest files

| # | Source file | Role | Covered in |
|---|---|---|---|
| 1 | `jpa/entities/Product.java` | entity | `01` §1 — ✅ Full |
| 2 | `jpa/entities/ProductImage.java` | entity (join) | `01` §2 — ✅ Full |
| 3 | `api/models/ProductPojo.java` | transport model | `01` §3 — ✅ Full |
| 4 | `jpa/repositories/ProductsRepository.java` | repository | `02` §2.1 — ✅ Full |
| 5 | `jpa/repositories/ProductImagesRepository.java` | repository | `02` §2.2 — ✅ Full |
| 6 | `jpa/services/conversion/ProductsConverterService.java` | contract | `02` §1, §5 — ✅ Full |
| 7 | `jpa/services/conversion/impl/ProductsConverterServiceImpl.java` | service | `02` §5 — ✅ Full |
| 8 | `jpa/services/crud/ProductsCrudService.java` | contract | `02` §1 — ✅ Full |
| 9 | `jpa/services/crud/impl/ProductsCrudServiceImpl.java` | service | `02` §3, §4, §6 — ✅ Full |
| 10 | `jpa/services/patch/ProductsPatchService.java` | contract | `02` §1 — ✅ Full |
| 11 | `jpa/services/patch/impl/ProductsPatchServiceImpl.java` | service | `02` §7 — ✅ Full |
| 12 | `jpa/services/predicates/ProductsPredicateService.java` | contract | `02` §1 — ✅ Full |
| 13 | `jpa/services/predicates/impl/ProductsPredicateServiceImpl.java` | service | `02` §8 — ✅ Full |
| 14 | `jpa/sortspecs/ProductsSortSpec.java` | helper | `02` §9 — ✅ Full |
| 15 | `api/controllers/DataProductsController.java` | controller | `02` §10 — ✅ Full |
| 16 | `jpa/repositories/ProductsCategoriesRepository.java` | repository **owned by `product-category`**, read here | `02` §2.3 — ✅ Full |

Paths relative to `src/main/java/org/trebol/`. **Coverage: 16/16 (100%).**

### 3.2 Outbound coupling census — the extraction-critical result

Every import block of the 12 product-domain files that have imports was examined for
`order|person|user|billing|salesperson|shipper|customer|sell`. **Two hits, both false
positives:** `com.querydsl.core.types.OrderSpecifier` in `DataProductsController` and in
`ProductsSortSpec`.

Zero imports of `Order*`, `Person`, `User`, `UserRole`, `BillingCompany`, `BillingType`,
`Salesperson`, `Shipper` or `Customer`, and zero imports of their repositories or
services.

Outbound coupling is exactly two domains, **both of which move into the same service**:

| Target domain | What is used | From |
|---|---|---|
| `product-category` | `ProductCategory`, `ProductCategoryPojo`, `ProductsCategoriesRepository`, `ProductCategoriesConverterService`, `ProductCategoryTreeResolverService` | entity, pojo, converter, predicate |
| `image` | `Image`, `ImagePojo`, `ImagesConverterService`, `ImagesCrudService` | `ProductImage`, converter, CRUD service |

**Consequence for Phase 1:** `product`, `product-category` and `image` are a single
cohesive unit and must be extracted together. Neither direction needs a contract seam
*inside* the catalogue. The only cross-service seam is the existing Gate A
`ProductLookupContract`, consumed by the order domain.

### 3.3 Inbound coupling — the Gate A seam and the three Phase 3 leaks

| Consumer | Shape | Phase |
|---|---|---|
| `ProductLookupContractImpl` → `OrdersConverterServiceImpl` | **the published contract**, barcode-keyed, per order line | done (Gate A) |
| `OrderDetail.product` | a real `@ManyToOne` to `Product`, with the only cross-boundary FK in the schema (`order_details.product_id`, `ON DELETE SET NULL`) | **Phase 3** |
| `OrderDetailPojo` and `ReceiptDetailPojo` | both embed `ProductPojo` | **Phase 3** |
| `ReceiptServiceImpl` | reads the `Product` entity directly, bypassing the contract | **Phase 3** |

These three leaks are **out of scope for Phase 1** — the order domain owns those files.
The FK is already nullable and `order_details` already stores a description and
unit-value snapshot, so it can be cut when Orders is extracted.

### 3.4 Shared-kernel files read but not owned

`jpa/services/crud/CrudGenericService.java` (all CRUD behaviour not overridden here),
`api/DataCrudGenericController.java` (the write-capable base and the Phase-0 D1 guard),
`api/DataGenericController.java`, `api/DataCrudController.java`, `api/DataController.java`,
the five generic service contracts, `jpa/Repository.java`, `jpa/DBEntity.java`,
`api/services/impl/PaginationServiceImpl.java`,
`jpa/services/impl/SortSpecParserServiceImpl.java`, `common/exceptions/BadInputException.java`,
`config/ApiProperties.java`, `security/authorities/AuthorityCodes.java`.

---

## 4. Entities

| Entity | Table | Ownership | Columns | Relationships |
|---|---|---|---|---|
| `Product` | `products` (index on `product_name`) | owned | 7 | 1 — LAZY `@ManyToOne` to `ProductCategory`, no cascade, nullable |
| `ProductImage` | `product_images` | owned | 1 + 2 FKs | 2 — LAZY `@ManyToOne` to `Image` and to `Product`, both `optional = false, updatable = false`, no cascade |
| `Param` (appendix) | `app_params` | owned | 4 | none |

No inheritance, no enumerations, no state field, no domain-specific exception type, and
**no lifecycle callbacks on any of the three**.

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, the coupling census, the company-profile appendix |
| `01-product-entity-and-transport.md` | behavioural spec: both entities and the transport model |
| `02-product-services.md` | behavioural spec: repositories, CRUD with its four overrides, converter, patch, predicate, sort spec, controller, and the image write path |
| `errors.md` | every error condition with its literal message; the deliberate non-errors |
| `permissions.md` | the four product authorities plus the anonymous company read |

The authoritative rule sets already exist at
`business-rules-extraction/detailed-domain-bre/domain-product/` and
`…/domain-company-profile/`. This spec adds the porting depth they lack: verbatim
literals, signatures, step sequences and defect positions.

---

## 6. Absorbed manifest domain — `company-profile`

**It is not a service.** One table, one anonymous read, no write path anywhere
(BR-COMPANY-008). The definition folds it into Catalogue; this appendix is its
porting-depth record.

### 6.1 Files (all 5, plus the controller the manifest omits)

| Source file | Role |
|---|---|
| `jpa/entities/Param.java` | entity over `app_params` |
| `jpa/repositories/ParamsRepository.java` | repository, one query method |
| `api/models/CompanyDetailsPojo.java` | transport model |
| `api/services/CompanyService.java` | contract, one method |
| `api/services/impl/CompanyServiceImpl.java` | service |
| `api/controllers/PublicAboutController.java` | controller (**not in the manifest's `source_files[]`**, and the only consumer) |

### 6.2 Behaviour

**`Param`** — `@Table(name = "app_params", uniqueConstraints = {@UniqueConstraint(columnNames = {"param_category", "param_name"})})`,
`serialVersionUID = 6L`. Columns: `param_id` (`@Id @GeneratedValue(IDENTITY)`,
`nullable = false`); `param_category` `@Size(min = 1, max = 25) nullable = false`;
`param_name` `@Size(min = 1, max = 50) nullable = false`; `param_value`
`@Size(min = 1, max = 500) nullable = false`. **No relationships, no lifecycle
callbacks.** The copy constructor copies **all four** fields including `id` — unlike
`Product` and `ProductCategory`, nothing is omitted. Lombok equality includes `id`.
Identity is the pair `(category, name)` (BR-COMPANY-001); values are opaque strings and
cannot be empty (BR-COMPANY-009, BR-COMPANY-010).

**`ParamsRepository`** — `extends Repository<Param>`, one method, the JPQL verbatim:
```java
@Query("SELECT p FROM Param p WHERE p.category = :category")
Iterable<Param> findParamsByCategory(@Param("category") String category);
```

**`CompanyServiceImpl`** (`@Service`, injects `ParamsRepository`) — `readDetails()`:
`findParamsByCategory("company")`, then an empty `CompanyDetailsPojo`, then
`switch (p.getName())` over exactly four cases — `"name"`, `"description"`,
`"bannerImageURL"`, `"logoImageURL"` — with `default: break`. **No caching, no
`@Transactional`, no exception path.** An unknown parameter name is silently ignored
(BR-COMPANY-004); a missing row simply leaves the field null and never fails the request
(BR-COMPANY-005). The profile is re-queried on every call (BR-COMPANY-011). Only the
`company` category is ever read (BR-COMPANY-002, BR-COMPANY-003), and the table is not
an extension point for other domains (BR-COMPANY-013).

**`PublicAboutController`** — `@RestController @RequestMapping("/public/about") @Tag(name = "About")`;
`@GetMapping public CompanyDetailsPojo readCompanyDetails()`. **No `@PreAuthorize`, no
parameters, no declared exceptions — fully anonymous** (BR-COMPANY-007).

### 6.3 The one documented discrepancy

`CompanyDetailsPojo` is `@Data @JsonInclude(ALWAYS)` with four unvalidated string fields
and no id. `ALWAYS` means an unset field serialises as an explicit `null` key.
**BR-COMPANY-006 states the opposite** — that unset fields are omitted. The **code is
authoritative** for the port: `ALWAYS` is preserved verbatim under G2, because the
emitted JSON shape is client-observable contract. The rule text is the thing that is
wrong, and the mismatch is raised as a new defect-register item rather than fixed
silently.

### 6.4 Seed rows read at runtime

From `src/main/resources/data.sql`:
`(01,'company','name','Trébol Demo')`,
`(02,'company','description','This is a demostration of integrated eCommerce system Trébol')`,
`(03,'company','bannerImageURL','https://fakeimg.pl/400x150')`,
`(04,'company','logoImageURL','https://fakeimg.pl/250')`.
The script also executes `ALTER TABLE app_params ALTER COLUMN param_id RESTART WITH 5;`
— the identity generator **must** be advanced past the seeded rows or the first insert
collides (BR-COMPANY-012). This statement ports into the catalogue service's `data.sql`.

---

## 7. Rule traceability

All 32 `BR-PRODUCT-*` and all 13 `BR-COMPANY-*` rules are referenced by id across this
document, `01`, `02`, `errors.md` and `permissions.md`. No rule is unreferenced, which is
what keeps exit criterion X4 verifiable after the port.

---

## 8. Phase 1 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 16 product files, all 6 company-profile files, **identical fully-qualified names**. |
| Literals | `"Invalid product barcode"`, `"This method is deprecated"`, `"This method signature has been deprecated"`, the four inherited kernel messages, the four sort keys, the seven filter parameter names and the four authority constants all port verbatim (G2). |
| Tables | `products`, `product_images`, `app_params` move with DDL, column types and constraint names unchanged. `product_images` FKs point only at `products` and `images`, both in the catalogue. The `order_details.product_id` FK points **inward** and is cut in Phase 3. |
| Seed data | The four `company` rows **and** the identity-restart statement. |
| Tests | `DataProductsControllerTest`, `ProductsConverterServiceImplTest`, `ProductsCrudServiceImplTest`, `ProductsPatchServiceImplTest`, `ProductsPredicateServiceImplTest`, `CompanyServiceImplTest`, `PublicAboutControllerTest`, plus helpers `ProductsTestHelper`, `ImagesTestHelper`, `ProductCategoriesTestHelper` and `TestConstants` (G8). |
| Behaviour | No warning in §2 is fixed during the port (G1). W12 is recorded, not corrected. |
