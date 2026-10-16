# Shipper — Error Conditions

Every error this domain can produce, with its **verbatim** message literal. Messages are
client-observable contract and port unchanged (G2).

---

## 1. Domain-specific errors

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E1 | Blank carrier name on any path that resolves a carrier | `BadInputException` | **`Billing type has no name`** | **400** | BR-SHIPPER-003 |
| E2 | `applyChangesToExistingEntity` on the converter | `UnsupportedOperationException` | `This method is deprecated` | 500 | — |
| E3 | `patchExistingEntity(ShipperPojo, Shipper)` on the patch service | `UnsupportedOperationException` | `This method has been deprecated` | 500 | — |

### E1 is the domain's most conspicuous defect, and it ports verbatim

> `ShippersCrudServiceImpl.getExisting` throws
> `new BadInputException("Billing type has no name")`.

The message names **the wrong domain entirely** — a copy-paste from
`BillingTypesCrudServiceImpl`. A client submitting a blank carrier name is told something
about billing types, which is actively misleading rather than merely unhelpful.

**It is preserved exactly.** The string is client-observable (G2) and correcting it is a
contract change needing its own ruling (G1). It is recorded as boundary warning W1 in
`00` §2, and `ShippersCrudServiceImplTest` asserts it — so the test and the code agree, and
a future "fix" to either will show up as a disagreement rather than a silent drift.

**E2 and E3 differ by one word** — `is` versus `has been`. Both strings exist in this domain;
do not normalise them. E2 is unreachable (the kernel uses the patch service instead); E3 is
unreachable over HTTP (the controller passes a `Map`, never a pojo).

---

## 2. Errors inherited from `CrudGenericService`

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E4 | `POST` with a carrier name that already exists | `EntityExistsException` | `The item already exists` | **409** | BR-SHIPPER-006 |
| E5 | `PUT`/`PATCH`/`DELETE` whose filters match nothing | `EntityNotFoundException` | `Requested item(s) not found` | **404** | — |
| E6 | `PUT`/`PATCH` whose filters match more than one row | `RuntimeException` | `Cannot update more than one item at a time` | 500 | **BR-SHIPPER-011** |
| E7 | `update(M)` — the single-argument overload | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 | — |
| E8 | A `BadInputException` raised inside `partialUpdate` | wrapped: `RuntimeException(e)` | the cause's message | **500** | — |

**E6 is a 500, not a 400**, because `CrudGenericService` throws a bare `RuntimeException`
rather than `BadInputException`. A client cannot distinguish "your filter was ambiguous" from
an internal fault. Preserved (G1).

**E8 is reachable here, unlike in most domains**, because `PATCH /data/shippers` is actually
mapped. A `BadInputException` from the patch service is wrapped and surfaces as a **500**
rather than the 400 it would otherwise be.

---

## 3. Errors from the framework and the database

| # | Condition | Exception | Message source | HTTP | Rule |
|---|---|---|---|---|---|
| E9 | `POST`/`PUT` body with a blank or missing `name` | `MethodArgumentNotValidException` | Spring's field-error rendering, **not** a domain literal | **400** | BR-SHIPPER-004 |
| E10 | `DELETE` whose parameters are **all** unrecognised | `BadInputException` | the Phase-0 **D1** guard: `No recognized filtering parameters were provided` | **400** | — |
| E11 | Deleting a carrier referenced by an order | `DataIntegrityViolationException` on `FK_ORDER_SHIPPER_ID` | Spring's | **500** | **BR-SHIPPER-013** |
| E12 | A non-string value under the `name` patch key | `ClassCastException` | none | **500** | — |
| E13 | A carrier name longer than the column's 255 characters | `DataIntegrityViolationException` | Spring's | **500** | BR-SHIPPER-005 |
| E14 | `getExisting` called with a **null** pojo | `NullPointerException` | none | **500** | — |

**E9 does not apply to `PATCH`** — that route takes `@RequestBody Map<String,Object>` with
**no `@Valid`** (W5), so a blank name in a patch body reaches the patch service, hits the
blank guard, and is **silently ignored** (§5.1) rather than rejected.

**E11 is BR-SHIPPER-013** — the delete is blocked by the **database**, not by application
logic, and arrives as a 500 rather than a 409. `shippers` is a pure parent with no outbound
foreign key, so every referential failure in this domain has that shape.

**E13 exists because the entity has no `@Size`** (W4). Every other string column in the
billing and shipper domains is bounded at the entity level and so fails at the boundary with
a 400; this one fails at the database with a 500.

---

## 4. The asymmetry worth knowing: unknown carrier on create versus on patch

**BR-SHIPPER-015.** The same bad input — a carrier name that does not exist — behaves
differently depending on which path reaches it:

| Path | Behaviour |
|---|---|
| **Checkout / order create** — `OrdersConverterServiceImpl.convertShippingInformationForEntity` | `shipperLookupContract.findShipperByName(name)` is empty → **`BadInputException("Specified shipper does not exist")` → 400** |
| **Order patch** — `OrdersPatchServiceImpl` | `shipperLookupContract.findShipperByName(name).ifPresent(target::setShipper)` → **silently ignored**, no error, request reports success |

So an order can be patched with a nonsense carrier name and the client is told nothing. The
literal `"Specified shipper does not exist"` belongs to the **order** domain and is specified
in `domain-order/errors.md`; it is cross-referenced here because this domain's data is what
the lookup fails to find.

**Neither path is changed.** Both are current observable behaviour (G1). Note also that after
the Phase-3 **D5** fix the order patch path rejects a `status` change outright, but the
`shipper` key still behaves exactly as described — and per register item **D27**, a patched
shipper is in any case a **silent no-op**, because `Order.shipper` is mapped
`updatable = false`.

---

## 5. Deliberate non-errors — silence that is contract

### 5.1 A blank patch value
The patch service blank-guards the `name` key, so patching `""` **leaves the stored name
unchanged** and reports success. **BR-SHIPPER-007** — a blank value never clears a stored
carrier name.

### 5.2 An empty `DELETE` filter
**BR-SHIPPER-012.** `DataCrudGenericController.delete` parses a predicate **only when
parameters are present**, so a parameter-less `DELETE` is a **silent no-op** returning 204.

That is a different shape from E10, and the distinction matters: parameters *present but all
unrecognised* is now rejected with 400 by the Phase-0 D1 guard, while *no parameters at all*
still does nothing quietly. Both behaviours are deliberate and both port.

### 5.3 An unrecognised filter parameter
`parseMap`'s `default: break` ignores it silently. On `GET` this widens the result set; on
`PUT`/`PATCH` it is the D19 match-all shape, ruled PRESERVE in scope.

### 5.4 A non-numeric `id` filter
Caught, logged at INFO with
`"Param '{}' couldn't be parsed as number (value: '{}')"`, and the parameter is dropped. The
request proceeds with whatever other filters parsed.

### 5.5 A no-op patch
`flushPartialChanges` finds the patched copy `equals` to the original — Lombok equality covers
both fields including the `id` the copy constructor preserved — and **skips the write**,
returning 204.

### 5.6 An order with no carrier
Entirely normal. **BR-SHIPPER-014** — `Order.shipper` is nullable, and shipping information
is resolved only when the order actually carries a shipper name. Nothing validates, warns or
reports on an order without one.

---

## 6. What this domain never raises

- **No 403 on the read.** `GET /data/shippers` has no `@PreAuthorize` at all
  (BR-SHIPPER-008), and the seeded `shippers:read` authority is **never referenced by any
  annotation** (BR-SHIPPER-009) — so no authorisation decision about reading carriers is ever
  made.
- **No 401 on the read** either, for the same reason: the controller carries no class-level
  guard, and `SecurityConfig` declares no request-matcher rules.
- **No optimistic-locking failure.** No `@Version` on the entity; concurrent writers
  last-write-win on a two-column row.
- **No 409 on delete.** A referential failure is a 500, per §3.
- **No validation error on `PATCH`.** That route has no `@Valid` (W5).
- **No length validation before the database.** The entity has no `@Size` (W4), so E13 is a
  500 where every sibling domain would give a 400.
