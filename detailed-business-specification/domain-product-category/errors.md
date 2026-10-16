# Errors — Product Category Tree

Every error condition reachable in this domain, with its **literal message**, the class
that raises it, and the HTTP status the shared `ExceptionsControllerAdvice` maps it to.
All message strings are client-observable contract under invariant G2 and must port
character-for-character.

---

## 1. Domain-raised errors

| # | Condition | Exception | Literal message | HTTP | Raised in |
|---|---|---|---|---|---|
| E1 | Existence check with a blank or missing `code` | `BadInputException` | `Invalid category code` | 400 | `ProductCategoriesCrudServiceImpl.getExisting` |
| E2 | `POST` of a category whose `code` already exists | `EntityExistsException` | `The item already exists` | 409 | `CrudGenericService.create` |
| E3 | `DELETE` whose predicate matches no row | `EntityNotFoundException` | `Requested item(s) not found` | 404 | `CrudGenericService.delete` |
| E4 | `readOne` with a predicate matching no row | `EntityNotFoundException` | `Requested item(s) not found` | 404 | `CrudGenericService.readOne` |
| E5 | `PUT` / `PATCH` whose predicate resolves no row | `EntityNotFoundException` | `No element was found to update` | 404 | `DataCrudGenericController.update` / `partialUpdate` |
| E6 | `PUT` / `PATCH` with an empty query-parameter map | `BadInputException` | `Missing request params` | 400 | `DataCrudGenericController` |
| E7 | `PUT` / `PATCH` whose predicate would match more than one row | `RuntimeException` | `Cannot update more than one item at a time` | **500** | `CrudGenericService.update(M, Predicate)` / `partialUpdate(Map, Predicate)` |
| E8 | `DELETE` whose parameters contain no recognised selector | `BadInputException` | `No recognized filtering parameters were provided` | 400 | `DataCrudGenericController.delete` — **the Phase-0 D1 fix** |
| E9 | `POST` / `PUT` body with a blank or missing `code` or `name` | `MethodArgumentNotValidException` | the **framework default** `@NotBlank` message (no custom text is declared) | 400 | Bean Validation at the route |
| E10 | Call to the deprecated converter method | `UnsupportedOperationException` | `This method is deprecated` | 500 | `ProductCategoriesConverterServiceImpl.applyChangesToExistingEntity` |
| E11 | Call to either deprecated single-argument signature | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 | `ProductCategoriesPatchServiceImpl.patchExistingEntity(Pojo, entity)` and `CrudGenericService.update(M)` |

**E7 is a 500, not a 409 or 400.** It is a bare `RuntimeException`, so the advice has no
specific handler for it. That is current observable behaviour and ports unchanged.

**E10 and E11 are unreachable through any HTTP route** in this domain — no controller
path calls those overloads. They are latent traps for a rewrite that re-wires the
generic bases. Their two messages differ from each other; do not unify them.

---

## 2. Database-surfaced errors — 500, not a clean status

These bypass every service guard and fail at flush. All of them produce an HTTP 500
with no useful message, because nothing translates the constraint violation.

| # | Condition | Why no guard catches it |
|---|---|---|
| E12 | Two sibling categories with the same `name` | `getExisting` resolves by `code` only, so the composite `(parent_product_category_id, product_category_name)` constraint is never pre-checked (BR-PRODCAT-002). Same defect class as D7 in the `image` domain, but **not** in the D7 fix scope. |
| E13 | `DELETE` of a category that still has children or products | No cascade and no orphaning call exists; the FK blocks the delete (BR-PRODCAT-016). The operation that would make it safe, `getBranchesFromRoot`, has no caller. |
| E14 | `PUT` replacement that writes a null into `product_category_code` or `product_category_name` | `update(M, Long)` builds a brand-new entity from the input, so an omitted field becomes null; `@NotBlank` at the route catches the normal case, but the service-level path has no guard. |
| E15 | `code` or `name` exceeding 50 / 100 characters on the `PATCH` path | `@Size` lives on the entity and is checked at flush; the patch service applies no length guard. |

---

## 3. Unhandled runtime failures

| # | Condition | Result |
|---|---|---|
| E16 | `PATCH` body with a non-string value for `code` or `name`, e.g. `{"code": 5}` | `ClassCastException` → **500**. The patch service performs an unchecked `(String)` cast with no type guard. |
| E17 | Equality or hash computation on a category that participates in a cycle | Potential `StackOverflowError` → 500. `@EqualsAndHashCode` includes `parent` and recurses transitively (BR-PRODCAT-009). `toString` is hand-written to avoid this; `equals` is not. |
| E18 | Reading `getParent()` outside an open persistence session | `LazyInitializationException` → 500. Not reachable today because the converter never emits the parent; a rewrite that adds parent output must handle it. |

---

## 4. Deliberate non-errors — silent outcomes a rewrite must preserve

These are the ones most likely to be "fixed" by accident. Each is current observable
behaviour and is forbidden to change without a register ruling (G1).

| # | Condition | Current outcome |
|---|---|---|
| N1 | `POST` with an **unknown** `parent.code` | **201 Created**, category silently becomes a **root** (BR-PRODCAT-008). No error. |
| N2 | `POST` with a blank or null `parent.code` | Same — root, no error, no NPE. |
| N3 | `POST` / `PUT` with `parent.code` equal to the category's own code | **Accepted.** A self-parent cycle is persisted with no validation (BR-PRODCAT-009). |
| N4 | `GET` with an unknown `rootCode` | The filter **silently disappears** and the query matches **everything** (BR-PRODCAT-011). |
| N5 | `GET` with an unknown `rootId` | The opposite: an empty `in` list is applied and the query matches **nothing**. The asymmetry with N4 is real. |
| N6 | A malformed numeric filter, e.g. `parentId=abc` | Logged at INFO and the filter is **dropped**; the request succeeds with a wider result set. No 400. |
| N7 | `PATCH` with a `parent` key | **Ignored twice over** — the patch service handles no `parent` key, and the CRUD override restores the original parent regardless (BR-PRODCAT-006, BR-PRODCAT-007). 204, nothing changed. |
| N8 | `PATCH` with a blank value, e.g. `{"name": ""}` | **Ignored.** The field is not cleared, no write occurs, 204 returned (BR-PRODCAT-019). |
| N9 | `PATCH` that changes nothing | No database write at all; the original entity is returned (the no-op optimisation of `02` §3.2). 204. |
| N10 | `PATCH` with unrecognised keys | Ignored silently. 204. |
| N11 | `DELETE` with a completely **empty** parameter map | **Silent no-op, 204.** Note the contrast with E8: an empty map is safe, an unrecognised selector is now a 400. |
| N12 | `GET` with no parameters | Returns **root categories only**, not the whole tree (BR-PRODCAT-012). |
| N13 | `PUT` of a category read back from the API | The response carries no `parent`, so the replacement **silently moves the category to the root**. Read-modify-write is lossy by design of the transport model. |

---

## 5. What this domain does **not** have

- **No domain-specific exception type.** Unlike the sibling `person` domain, there is no
  `ProductCategoryException`; everything reuses `BadInputException`,
  `EntityExistsException`, `EntityNotFoundException` and bare `RuntimeException`.
- **No validation error for a cycle, at any layer.** Not in the entity (no lifecycle
  callback), not in the converter, not in the service, not in the database.
- **No translation of constraint violations** into 409s. Only the `code` collision gets
  a clean 409, and only because `getExisting` pre-checks it.
