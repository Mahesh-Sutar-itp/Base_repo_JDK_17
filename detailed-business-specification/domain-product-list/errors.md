# Errors — Product Lists

Every error condition reachable in this domain, with its **literal message**, the class
that raises it, and the HTTP status the shared `ExceptionsControllerAdvice` maps it to.
All message strings are client-observable contract under invariant G2 and must port
character-for-character.

This domain has **two error surfaces**: the conventional list controller, which inherits
the kernel's guards, and the contents controller, which **extends nothing** and therefore
inherits none of them (BR-PRODLIST-030).

---

## 1. List surface — `/data/product_lists`

| # | Condition | Exception | Literal message | HTTP | Raised in |
|---|---|---|---|---|---|
| E1 | Existence check with a blank or missing `name` | `BadInputException` | `The specified list has no name` | 400 | `ProductListsCrudServiceImpl.getExisting` |
| E2 | `POST` of a list whose **name** already exists | `EntityExistsException` | `The item already exists` | 409 | `CrudGenericService.validateInputPojoBeforeCreation` |
| E3 | `DELETE` whose predicate matches no list | `EntityNotFoundException` | `Requested item(s) not found` | 404 | `ProductListsCrudServiceImpl.delete` (override) — BR-PRODLIST-022 |
| E4 | `readOne` with a predicate matching no row | `EntityNotFoundException` | `Requested item(s) not found` | 404 | `CrudGenericService.readOne` |
| E5 | `PUT` / `PATCH` whose predicate resolves no row | `EntityNotFoundException` | `No element was found to update` | 404 | `DataCrudGenericController` |
| E6 | `PUT` / `PATCH` with an empty query-parameter map | `BadInputException` | `Missing request params` | 400 | `DataCrudGenericController` |
| E7 | `PUT` / `PATCH` whose predicate would match more than one row | `RuntimeException` | `Cannot update more than one item at a time` | **500** | `CrudGenericService` — BR-PRODLIST-029 |
| E8 | `DELETE` whose parameters contain no recognised selector | `BadInputException` | `No recognized filtering parameters were provided` | 400 | `DataCrudGenericController.delete` — **the Phase-0 D1 fix** |
| E9 | `POST` / `PUT` body with an empty `code` | `MethodArgumentNotValidException` | the **framework default** `@NotEmpty` message | 400 | Bean Validation at the route |
| E10 | Call to `ProductListConverterServiceImpl.applyChangesToExistingEntity` or `ProductListItemsConverterServiceImpl.applyChangesToExistingEntity` | `UnsupportedOperationException` | `This method is deprecated` | 500 | both converters |
| E11 | Call to either deprecated single-argument signature | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 | `ProductListPatchServiceImpl` and `CrudGenericService.update(M)` |
| E12 | Call to `ProductListItemsConverterServiceImpl.convertToNewEntity` | `UnsupportedOperationException` | **`Not implemented`** | 500 | the item converter |

**Three distinct `UnsupportedOperationException` messages exist in this domain** —
`"This method is deprecated"`, `"This method signature has been deprecated"` and
`"Not implemented"`. Do not unify them.

**E12 is architecturally load-bearing**, not dead: because the item converter cannot
build an entity, the contents controller constructs membership rows by hand, which is how
the generic framework comes to be bypassed.

**`name` is unvalidated at the route** (the pojo has no annotation on it), so a `POST`
with no name produces **E1, a 400 from the service layer**, not a bean-validation 400.
The response shape differs accordingly.

---

## 2. Contents surface — `/data/product_list_contents`

| # | Condition | Exception | Literal message | HTTP | Raised in |
|---|---|---|---|---|---|
| E13 | Any contents request with a blank or missing `listCode` | `BadInputException` | `listCode query param is required` | 400 | `fetchProductListByCode` — BR-PRODLIST-011 |
| E14 | `listCode` naming a list that does not exist | `EntityNotFoundException` | `Requested item(s) not found` | 404 | all four endpoints — BR-PRODLIST-012 |
| E15 | A submitted product with a blank or missing `barcode` | `BadInputException` | **`Invalid product barcode`** | 400 | `ProductsCrudServiceImpl.getExisting` — **raised by the product domain** |

Note E14's message comes from the controller's **own** `ITEM_NOT_FOUND` constant, which
duplicates the kernel's text. Both copies port.

**E15 is the dangerous one on the replace path.** Because the `PUT` body is not `@Valid`
(BR-PRODLIST-018), a blank barcode is not caught at the route; it throws **mid-loop,
after `deleteByListId` has already committed** (§3 below).

**The contents surface raises no other error.** There is no conflict status, no
validation of the collection, and no guard equivalent to E8.

---

## 3. The partial-state failure — the domain's worst defect

`updateContents` has **no `@Transactional`**, while `deleteByListId` carries its own on
the repository method. Sequence:

1. `deleteByListId` runs and **commits**.
2. Re-inserts begin, **each in its own transaction**.
3. If E15 is raised for any element, the request fails with 400.

**Result: the list is left emptied or half-populated, with no rollback and no
compensating action** (BR-PRODLIST-016). A client receiving the 400 has no way to know how
much of its collection was applied.

This is **preserved behaviour** for Phase 1. Adding `@Transactional` would be a
behavioural change under G1 and requires a register ruling it does not currently have.

---

## 4. Database-surfaced errors — 500, not a clean status

| # | Condition | Why no guard catches it |
|---|---|---|
| E16 | `POST` of a list whose **code** already exists | `getExisting` resolves by **name** only, so the `product_list_code` unique constraint is never pre-checked (BR-PRODLIST-001, BR-PRODLIST-003). The clean 409 of E2 exists only for the name. |
| E17 | `code` or `name` shorter than 2 or longer than 25 / 50 characters | The entity's `@Size(min = 2, ...)` is checked at flush; the pojo mirrors neither bound. `@NotEmpty` on `code` lets a single space through, which then fails on `min = 2`. |
| E18 | `PUT` replacement that writes a null `name` | `update(M, Long)` builds a brand-new entity, so an omitted `name` becomes null against a `NOT NULL` column. The pojo has no validation on `name`. |
| E19 | Concurrent adds of the same product to the same list | **No unique constraint on `(product_list_id, product_id)`** (BR-PRODLIST-014), so the duplicate simply succeeds — no error at all, just a duplicated row. |

---

## 5. Unhandled runtime failures

| # | Condition | Result |
|---|---|---|
| E20 | `PATCH` with a non-string value for `name` or `code` | **`ClassCastException`** → 500. The patch service casts before the blank guard and never raises `BadInputException`. |
| E21 | Equality, hashing or `toString` on a `ProductList` outside a session | Possible `LazyInitializationException` → 500. Lombok includes the lazy `items` collection in all three. |
| E22 | A contents read of a very large list | No failure, but every row eagerly loads a full `ProductList` and a full `Product` (BR-PRODLIST-020), plus a `COUNT` per list on the list surface. A performance cliff, not an exception. |

---

## 6. Deliberate non-errors — silent outcomes a rewrite must preserve

Each is current observable behaviour and is forbidden to change without a register
ruling (G1).

| # | Condition | Current outcome |
|---|---|---|
| N1 | Contents `POST` with an **unknown** barcode | **201 Created, nothing happens** (BR-PRODLIST-015). |
| N2 | Contents `POST` of a product already in the list | **201, no duplicate** — the `exists(Example.of(...))` check suppresses it (BR-PRODLIST-014). Not atomic. |
| N3 | Contents `PUT` with an **empty body** | List **emptied**, 204. |
| N4 | Contents `PUT` where **every** product is unresolvable | List **emptied**, 204 — indistinguishable from success. |
| N5 | Contents `PUT` with duplicates in the collection | Collapse to one row, 204. |
| N6 | Contents `DELETE` with only `listCode` | **The whole list is cleared**, 204 (BR-PRODLIST-017). No guard protects this — the controller extends nothing. |
| N7 | Contents `DELETE` with a **mistyped** product filter | The filter is ignored, so the predicate degrades to list-wide and **the whole list is cleared**, 204. |
| N8 | Any contents operation on a **disabled** list | **Fully permitted.** The flag is never consulted (BR-PRODLIST-009). |
| N9 | `PUT` on a list | **`disabled` is silently reset to `false`** (BR-PRODLIST-008), 204. |
| N10 | `PATCH` with a `disabled` or `totalCount` key | **Ignored.** Only `name` and `code` are recognised. 204. |
| N11 | `PATCH` with a blank `name` or `code` | **Ignored** — blank values never clear a stored value. 204. |
| N12 | `PATCH` that changes nothing | No database write; the original entity is returned. 204. |
| N13 | Supplying `totalCount` in a request body | Silently ignored — the field is output-only. |
| N14 | A list with no items | `totalCount` is **omitted from the response entirely** (`NON_DEFAULT`), not reported as `0`. |
| N15 | `DELETE` on the list surface with a completely **empty** parameter map | **Silent no-op, 204.** Contrast E8. |
| N16 | Deleting a product that belongs to lists | Its membership rows vanish via the database's `ON DELETE CASCADE` (BR-PRODLIST-023). The application-level cleanup `deleteByProductId` is **never called** (BR-PRODLIST-024). **The cascade DDL must port**, or this silently becomes an FK violation. |
| N17 | A filtered contents read | `totalCount` **ignores the filters** and counts the whole list, so the reported total contradicts the page. |
| N18 | An unsorted contents read | **No stable order** — no sort key covers membership id or insertion sequence. |

---

## 7. What this domain does **not** have

- **No domain-specific exception type.** Everything reuses `BadInputException`,
  `EntityExistsException`, `EntityNotFoundException` and bare `RuntimeException`.
- **No conflict status on the contents surface.** Duplicate membership is suppressed
  silently rather than reported.
- **No transaction on any contents operation**, which is the root of §3.
- **No database-level duplicate protection** for membership.
- **No validation of the bulk replace body.**
- **No error for operating on a disabled list** — because nothing reads the flag.
