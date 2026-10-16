# Errors — Product Catalogue

Every error condition reachable in this domain and in the `company-profile` appendix,
with its **literal message**, the class that raises it, and the HTTP status the shared
`ExceptionsControllerAdvice` maps it to. All message strings are client-observable
contract under invariant G2 and must port character-for-character.

---

## 1. Domain-raised errors

| # | Condition | Exception | Literal message | HTTP | Raised in |
|---|---|---|---|---|---|
| E1 | Existence check with a blank or missing `barcode` | `BadInputException` | `Invalid product barcode` | 400 | `ProductsCrudServiceImpl.getExisting` |
| E2 | `POST` of a product whose `barcode` already exists | `EntityExistsException` | `The item already exists` | 409 | `CrudGenericService.validateInputPojoBeforeCreation` |
| E3 | `DELETE` whose predicate matches no row | `EntityNotFoundException` | `Requested item(s) not found` | 404 | `CrudGenericService.delete` |
| E4 | `readOne` with a predicate matching no row | `EntityNotFoundException` | `Requested item(s) not found` | 404 | `ProductsCrudServiceImpl.readOne` (override) |
| E5 | `PUT` / `PATCH` whose predicate resolves no row | `EntityNotFoundException` | `No element was found to update` | 404 | `DataCrudGenericController` |
| E6 | `PUT` / `PATCH` with an empty query-parameter map | `BadInputException` | `Missing request params` | 400 | `DataCrudGenericController` |
| E7 | `PUT` / `PATCH` whose predicate would match more than one row | `RuntimeException` | `Cannot update more than one item at a time` | **500** | `CrudGenericService.update(M, Predicate)` / `partialUpdate(Map, Predicate)` |
| E8 | `DELETE` whose parameters contain no recognised selector | `BadInputException` | `No recognized filtering parameters were provided` | 400 | `DataCrudGenericController.delete` — **the Phase-0 D1 fix** |
| E9 | `POST` / `PUT` body with a blank `name` or `barcode`, or a missing `price` | `MethodArgumentNotValidException` | the **framework default** `@NotBlank` / `@NotNull` message (no custom text declared) | 400 | Bean Validation at the route |
| E10 | Call to the deprecated converter method | `UnsupportedOperationException` | `This method is deprecated` | 500 | `ProductsConverterServiceImpl.applyChangesToExistingEntity` |
| E11 | Call to either deprecated single-argument signature | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 | `ProductsPatchServiceImpl.patchExistingEntity(Pojo, entity)` and `CrudGenericService.update(M)` |

**E7 is a 500, not a 409.** It is a bare `RuntimeException` with no specific handler.
Current observable behaviour; ports unchanged.

**E10 and E11 are unreachable through any HTTP route.** They are latent traps for a
rewrite that re-wires the generic bases. The two messages differ from each other — do not
unify them.

**E1 is cross-service contract from Phase 3 onward**, because `getExisting` is the
semantic core of the Gate A `ProductLookupContract` that the order domain calls per order
line.

---

## 2. Errors raised by the image path — and the one that is swallowed

| # | Condition | Outcome |
|---|---|---|
| E12 | An `ImagePojo` in the `images` collection has a blank `filename` | `ImagesCrudServiceImpl.getExisting` throws `BadInputException("Invalid filename")` → **caught in `makeProductImageRelationships`, logged at DEBUG, the image is dropped, and the request returns 201 / 204.** No client-visible error at all (BR-PRODUCT-013). DEBUG is below the default level, so production leaves no trace. |
| E13 | An `ImagePojo` with a `code` or `url` colliding with a different existing image | Since the Phase-0 **D7** fix, `getExisting` checks filename, then `code`, then `url` (first match wins), so this now **resolves to the existing image** instead of producing a second row that collided at the database. Intentional, already-ruled behaviour change. |
| E14 | The same image appears twice in one payload | **No error.** Two `product_images` rows are written; the read side hides it with `distinct()` (BR-PRODUCT-015). |

E12 is the single most important non-error in the domain: **the image subsystem fails
open.**

---

## 3. Database-surfaced errors — 500, not a clean status

These bypass every service guard and fail at flush, producing an HTTP 500 with no useful
message because nothing translates the constraint violation.

| # | Condition | Why no guard catches it |
|---|---|---|
| E15 | Two products with the same `name` | `getExisting` resolves by `barcode` only, so the `product_name` unique constraint is never pre-checked (BR-PRODUCT-001). Same defect class as D7 in the `image` domain, but **not** in the D7 fix scope. |
| E16 | `DELETE` of a product that still has image links | No cascade and no orphaning call; the `product_images` FK blocks the delete (BR-PRODUCT-028). |
| E17 | `name`, `barcode` or `description` exceeding 200 / 50 / 4000 characters | The pojo declares **no `@Size`**; the limits live on the entity and are checked at flush. |
| E18 | `PUT` replacement that writes a null into `product_name` or `product_code` | `update(input, id)` builds a brand-new entity, so an omitted field becomes null. `@NotBlank` catches the HTTP case; the service path has no guard. |
| E19 | A `PUT`/`PATCH` that leaves an `Image` unpersisted | No cascade is declared on `ProductImage.image`, so a genuinely new image depends on transaction reachability; it surfaces as a placeholder row rather than an error (BR-PRODUCT-014). |

**Deleting a product does not fail because of orders** — the `order_details.product_id`
FK is `ON DELETE SET NULL`, and `order_details` keeps its own description and unit-value
snapshot, so order history survives product deletion (BR-PRODUCT-029). Product-list
membership blocks nothing either (BR-PRODUCT-030).

---

## 4. Unhandled runtime failures

| # | Condition | Result |
|---|---|---|
| E20 | `PATCH` with `{"price": null}` | **NPE on unboxing** → 500. The patch service casts and sets unconditionally (BR-PRODUCT-023). |
| E21 | `PATCH` with `{"price": "abc"}` or `{"currentStock": 1.5}` | **`ClassCastException`** → 500. Same for `criticalStock`. |
| E22 | `PATCH` with a non-string value for `name`, `barcode` or `description` | `ClassCastException` → 500. The blank guard runs **after** the cast. |
| E23 | `convertToNewEntity` reached with a null `price` from a non-HTTP caller | NPE on unboxing → 500. `@NotNull` only protects the route. |
| E24 | Reading `getProductCategory()` outside an open session | `LazyInitializationException` → 500. Not reachable today; the converter runs inside a `@Transactional` service. |

---

## 5. Deliberate non-errors — silent outcomes a rewrite must preserve

Each is current observable behaviour and is forbidden to change without a register
ruling (G1).

| # | Condition | Current outcome |
|---|---|---|
| N1 | `POST` with an **unknown** `category.code` | **201 Created**, product silently **uncategorised** (BR-PRODUCT-010). |
| N2 | `POST` with a blank `category.code` | Same — the lookup is skipped entirely, uncategorised, no error. |
| N3 | `POST`/`PUT` with a negative `price`, `currentStock` or `criticalStock` | **Accepted and stored** (BR-PRODUCT-007). |
| N4 | `POST` with a fractional price, e.g. `19.99` | Deserialization to `Integer` fails → 400. There are **no decimal places anywhere** (BR-PRODUCT-008). |
| N5 | `POST` omitting `currentStock` / `criticalStock` | Stored as **0**, not null (BR-PRODUCT-006) — the primitive default. |
| N6 | Ordering an out-of-stock product | **Succeeds.** Stock is recorded but never checked or decremented (BR-PRODUCT-021, BR-PRODUCT-022). |
| N7 | `PUT` with no `images` field | **Every existing image link is deleted** and nothing is rebuilt (BR-PRODUCT-011). 204. |
| N8 | `PATCH` with an `images` key | **Ignored.** Images are not patchable (BR-PRODUCT-012). 204. |
| N9 | `PATCH` with a `category` key | **Ignored twice over** — no key handling, and the patch service restores the original category (BR-PRODUCT-017). A patch can never declassify. 204. |
| N10 | `PATCH` with a blank `name`, `barcode` or `description` | **Ignored.** Blank values never clear a stored value (BR-PRODUCT-024). 204. |
| N11 | `PATCH` that changes nothing | No database write; the original entity is returned (`flushPartialChanges`). 204. |
| N12 | `PATCH` with unrecognised keys | Ignored silently. 204. |
| N13 | A malformed numeric filter, e.g. `id=abc` | Logged at INFO and the filter is **dropped**; the request succeeds with a wider result set. No 400. |
| N14 | `GET` list | Images are **never** included (BR-PRODUCT-016) — only the single-item read resolves them. |
| N15 | `DELETE` with a completely **empty** parameter map | **Silent no-op, 204.** Contrast E8: an empty map is safe, an unrecognised selector is now a 400. |
| N16 | `GET` with no parameters | Returns the **whole catalogue**, paged — no default filter is substituted (unlike the category controller). |

**N7 combined with N14 is the sharpest hazard in the catalogue:** a client that lists
products, picks one and `PUT`s it back destroys that product's images, because the list
response never carried them.

---

## 6. `company-profile` appendix — the error model is "there isn't one"

| Condition | Outcome |
|---|---|
| A `company` parameter row is missing | The corresponding field is left **null** and the request **succeeds** (BR-COMPANY-005). |
| A parameter name is not one of the four recognised ones | **Silently ignored** by the `switch` default (BR-COMPANY-004). |
| The whole `company` category is absent | An all-null `CompanyDetailsPojo` is returned with HTTP 200. |
| An attempt to write the profile | **No write path exists** — no endpoint, no service method (BR-COMPANY-008). |
| A `param_value` exceeding 500 characters, or empty | Rejected by the entity's `@Size(min = 1, max = 500)` at flush — but only reachable through direct database access, since there is no write path (BR-COMPANY-009, BR-COMPANY-010). |
| The identity generator is not advanced past the seeded rows | The first insert collides on `param_id`. `data.sql` must keep `ALTER TABLE app_params ALTER COLUMN param_id RESTART WITH 5;` (BR-COMPANY-012). |

`CompanyServiceImpl` declares and throws **no exceptions**, has no `@Transactional` and
no caching; the profile is re-queried on every request (BR-COMPANY-011).

**One documented discrepancy.** `CompanyDetailsPojo` is `@JsonInclude(ALWAYS)`, so unset
fields are emitted as explicit `null` keys. **BR-COMPANY-006 asserts they are omitted.**
The code is authoritative for the port; the rule text is wrong. Recorded as a register
item rather than fixed silently.

---

## 7. What this domain does **not** have

- **No domain-specific exception type.** Everything reuses `BadInputException`,
  `EntityExistsException`, `EntityNotFoundException` and bare `RuntimeException`.
- **No translation of constraint violations into 409s.** Only the barcode collision gets
  a clean 409, and only because `getExisting` pre-checks it.
- **No stock validation of any kind**, at any layer.
- **No price validation** beyond presence.
- **No error whatsoever on the image path** — it fails open by construction.
