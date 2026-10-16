# Image Registry — Domain Index

Domain slug: `image` · Complexity: small · 13 source files · 691 LOC · 1 owned table ·
1 entity · **5 HTTP endpoints** · 24 business rules · 9 user stories

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "image"`.

---

## 1. Domain summary

**Purpose.** A catalogue of image asset **metadata** — a business `code`, a `filename` and
an absolute `url`. The system stores no binary content, never uploads or serves an image,
and never dereferences a URL.

**Core invariant, as implemented.** A filename identifies at most one image, and filename is
the **only** field the service layer resolves by (BR-IMAGE-001). The database additionally
enforces uniqueness on `code` and `url`, which the service does not check — the domain's
defining incoherence (W1 below).

**Boundaries.** A pure leaf: `depends_on_domains` is empty, the entity declares no
association, and the only inbound coupling is from `product`.

**Key entity.** `Image` → table `images`. Declared **shared**; this domain is its owner.
Canonical field definition: `cross-cutting-documentation/shared-entities.md#image`.

**Key services.** One repository, one converter, one patch service, one CRUD service, one
predicate service, one sort-spec helper, and a **write-capable** controller exposing all
five CRUD operations. Three of the four domain-specific service *contracts* are empty type
bindings; the behaviour lives in the shared-kernel generic bases plus three domain overrides
(`getExisting`, the converter, the patch service).

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **Duplicate detection covers one of three unique fields.** All three business columns are independently unique, but `getExisting` resolves by filename only. A `code` or `url` collision therefore passes the guard and fails at the database — **HTTP 500 instead of 409**. On the patch path there is no clean 409 for *any* field, including filename. | High | BR-IMAGE-002, `specs/01-data-model.md` §1.2 |
| W2 | **A mistyped `DELETE` parameter deletes the entire registry.** The controller guards only on a non-empty parameter map, while the predicate matches **everything** when no parameter is recognised. So `?fileName=x` (note the typo) passes the guard, matches all rows, deletes them, and returns 204. An *empty* map is safe; a typo is catastrophic. | **Highest** | BR-IMAGE-013, BR-IMAGE-022 |
| W3 | **The `product` domain creates images bypassing every guard.** It calls `getExisting`, then builds and cascade-persists `Image` rows itself rather than calling `create` — skipping the duplicate guard, the 409 path and route validation. A blank filename is swallowed and the image **silently dropped**, with the product still succeeding. `images:create` is never checked on this path. | High | BR-IMAGE-024, `specs/06-integration-points.md` §2.3 |
| W4 | **Writes are addressed by query parameters**, so the predicate's short-circuiting (non-deterministic with multiple identity selectors) and unescaped metacharacters apply to `PUT`, `PATCH` and `DELETE` — not just searches. There is no path-identifier route form and no `url` selector at all. | Medium | BR-IMAGE-021, BR-IMAGE-008, BR-IMAGE-019 |
| W5 | **Stored URLs are never validated or allow-listed**, yet they are served to clients and rendered. Any string up to 500 characters is accepted, including hostile schemes or third-party hosts. | Medium | BR-IMAGE-005, `reference/integrations.md` |

---

## 3. File coverage

All 13 files in the manifest's `source_files[]` are documented. No file is excluded.

| # | Source file | LOC | Role | Covered in |
|---|---|---|---|---|
| 1 | `jpa/entities/Image.java` | 73 | entity | `01` §1 — ✅ Full |
| 2 | `api/models/ImagePojo.java` | 45 | transport model | `01` §2 — ✅ Full |
| 3 | `jpa/repositories/ImagesRepository.java` | 33 | repository | `02` §2 — ✅ Full |
| 4 | `jpa/services/conversion/ImagesConverterService.java` | 29 | contract | `02` §1 — ✅ Full |
| 5 | `jpa/services/conversion/impl/ImagesConverterServiceImpl.java` | 56 | service | `02` §4 — ✅ Full |
| 6 | `jpa/services/crud/ImagesCrudService.java` | 29 | contract | `02` §1 — ✅ Full |
| 7 | `jpa/services/crud/impl/ImagesCrudServiceImpl.java` | 64 | service | `02` §3 — ✅ Full |
| 8 | `jpa/services/patch/ImagesPatchService.java` | 29 | contract | `02` §1 — ✅ Full |
| 9 | `jpa/services/patch/impl/ImagesPatchServiceImpl.java` | 70 | service | `02` §5 — ✅ Full |
| 10 | `jpa/services/predicates/ImagesPredicateService.java` | 29 | contract | `02` §6.1 — ✅ Full |
| 11 | `jpa/services/predicates/impl/ImagesPredicateServiceImpl.java` | 67 | service | `02` §6.2 — ✅ Full |
| 12 | `jpa/sortspecs/ImagesSortSpec.java` | 39 | helper | `02` §7.1 — ✅ Full |
| 13 | `api/controllers/DataImagesController.java` | 128 | controller | `02` §7.3 — ✅ Full |

Paths relative to `src/main/java/org/trebol/`. **Coverage: 13/13 (100%).**

### 3.1 Shared-kernel files read but not owned

Read to specify inherited behaviour; not part of the coverage count. Authoritative
specification: `domain-decomposition-overview/shared-kernel.md`.

| File | Why read | Behaviour specified in |
|---|---|---|
| `jpa/services/crud/CrudGenericService.java` | supplies all CRUD behaviour except `getExisting` | `02` §3.2–§3.8 |
| `api/DataCrudGenericController.java` | the **write-capable** base — supplies `update`/`partialUpdate`/`delete`, including the two literal guard messages and the inverted delete guard | `02` §7.3 |
| `api/DataGenericController.java` | the read pipeline | `02` §7.3 |
| `api/DataCrudController.java`, `api/DataController.java` | the controller contracts | `02` §7.3 |
| `jpa/services/CrudService.java`, `ConverterService.java`, `PatchService.java`, `PredicateService.java` | the generic contracts this domain binds | `02` §1 |
| `jpa/Repository.java`, `jpa/DBEntity.java` | generic persistence contract and entity marker | `02` §2, `01` §1 |
| `api/services/impl/PaginationServiceImpl.java`, `jpa/services/impl/SortSpecParserServiceImpl.java` | page/sort resolution, including the 500-causing defects | `02` §7.2 |

### 3.2 Consumer files read to establish call boundaries

Owned by the `product` domain and documented in **its** specification; read here to census
inbound coupling: `jpa/services/crud/impl/ProductsCrudServiceImpl.java` (notably
`makeProductImageRelationships`), plus the `product` domain's converter.

Findings in `specs/06-integration-points.md` §2.2–§2.3.

---

## 4. Entities

| Entity | Table | Ownership | Columns | Relationships |
|---|---|---|---|---|
| `Image` | `images` | owned by this domain; declared **shared** (used by `image`, `product`) | 4 | 0 declared — the only association is the `product_images` join table, owned by `product` |

No abstract base types, no inheritance, no enumerations, no state field. **No
domain-specific exception type** — unlike the sibling `person` domain.

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage, boundary warnings, inventory |
| `01-image-entity-and-transport.md` | behavioural spec: entity and transport model |
| `02-image-services.md` | behavioural spec: repository, converter, CRUD, patch, predicate, sort helper, write-capable controller |
| `specs/00-plan.md` | reading order, coverage, omitted specs with reasons |
| `specs/01-data-model.md` | columns, verbatim types, the three-way uniqueness problem, divergences |
| `specs/03-business-rules.md` | 24 numbered testable rules |
| `specs/04-user-stories.md` | 9 stories with GIVEN/WHEN/THEN for happy, alternate and error paths |
| `specs/05-api-contract.md` | all five endpoints in full; the service surface; extraction gaps |
| `specs/06-integration-points.md` | external integrations (none, with evidence); the inbound call census and the product bypass |
| `diagrams/domain-er-diagram.md` | ER diagram, constraint layout, integrity notes |
| `diagrams/domain-sequence-diagrams.md` | list, create, patch, the four delete hazards, and the cross-domain write |
| `diagrams/domain-class-diagram.md` | self-contained class diagram including the cross-domain bypass |
| `errors.md` | 11 error conditions with literal messages; the deliberate non-errors; consumer-suppressed errors |
| `permissions.md` | four authorities; what each grants; the uncovered internal write path |
| `reference/api-endpoints.md` | endpoint inventory plus the product domain's image-writing routes |
| `reference/background-jobs.md` | none, with the link-checking and orphan consequences |
| `reference/integrations.md` | none, with the never-dereferenced-URL analysis |
| `reference/feature-flags.md` | none; the two kernel config values; behaviour that is not configurable |

**18 files.** Omitted standard specs — `02-state-machines.md`, `07-query-specs.md`,
`08-config-parameters.md`, `events.md` — are each justified in `specs/00-plan.md` §4.

---

## 6. Absorbed manifest domains

**None.** This domain absorbs no other manifest slug. Its 13 source files are exclusively
its own, and every other manifest slug either has or will have its own output directory.

---

## 7. Coverage summary

| Category | Files | Source covered | Status |
|---|---|---|---|
| Behavioural specs | 2 | 13/13 files, all public and protected members | Complete |
| Specs suite | 5 | — | Complete (4 justified omissions) |
| Diagrams | 3 | — | Complete |
| Domain references | 4 | — | Complete |
| Errors and permissions | 2 | — | Complete |
| Index and plan | 2 | — | Complete |
| **Total** | **18** | **100%** | **Complete** |

Rewrite-readiness: every public and protected member of all 13 files has a documented
purpose, parameters, return value or side effect, and at least one named error path or an
explicit statement that none exists. Every literal error message, authority string, sort
key, filter parameter and column type is recorded verbatim.
