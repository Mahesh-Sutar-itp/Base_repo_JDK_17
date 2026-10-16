# Billing — Error Conditions

Every error this domain can produce, with its **verbatim** message literal. Messages are
client-observable contract and port unchanged (G2).

---

## 1. Domain-specific errors

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E1 | Blank billing-type name on any path that resolves a billing type | `BadInputException` | `Billing type has no name` | **400** | BR-BILLING-005 |
| E2 | Blank company id number on any path that resolves a billing company | `BadInputException` | `Billing company has no id number` | **400** | BR-BILLING-011 |
| E3 | `applyChangesToExistingEntity` on any of the three converters | `UnsupportedOperationException` | `This method is deprecated` | 500 | — |
| E4 | `patchExistingEntity(Pojo, Entity)` on either patch service | `UnsupportedOperationException` | `This method has been deprecated` | 500 | — |

**E3 and E4 differ by one word** — `is` versus `has been`. Both strings exist in this domain
and must not be normalised. Neither is reachable through any route.

**E2 is the only validation a billing company ever gets.** `BillingCompanyPojo` carries no
annotations at all (BR-BILLING-013), so there is no 400-from-bean-validation path for it.

---

## 2. Errors inherited from `CrudGenericService`

| # | Condition | Exception | Verbatim message | HTTP |
|---|---|---|---|---|
| E5 | Creating a billing type whose name already exists | `EntityExistsException` | `The item already exists` | **409** |
| E6 | Creating a billing company whose id number already exists | `EntityExistsException` | `The item already exists` | **409** |
| E7 | `readOne` finding nothing | `EntityNotFoundException` | `Requested item(s) not found` | **404** |
| E8 | `update(M)` — the single-argument overload | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 |
| E9 | A `BadInputException` raised inside `partialUpdate` | wrapped: `RuntimeException(e)` | the cause's message | **500** |

**E5 and E6 are unreachable over HTTP in this domain.** There is no create route for either
entity — the only reachable creation is through checkout, which goes via
`BillingContract.resolveOrCreateBillingCompany` and therefore hits `getExisting` first. A
409 from this domain is possible only through direct service use.

**E8 and E9 are likewise unreachable**: no update or patch route exists.

---

## 3. Errors from the framework and the database

| # | Condition | Exception | HTTP |
|---|---|---|---|
| E10 | Creating a billing company whose **name** already exists but whose id number is new | `DataIntegrityViolationException` on `UK_BILL_CY_NAME` | **500** |
| E11 | Deleting a billing type referenced by an order | `DataIntegrityViolationException` on `FK_ORDER_BILL_TYP_ID` | **500** |
| E12 | A non-string value under a patch key | `ClassCastException` | **500** |
| E13 | `getExisting` called with a null pojo | `NullPointerException` | **500** |

**E10 is the consequence of two independent unique constraints with only one being consulted.**
`getExisting` de-duplicates on `idNumber` (BR-BILLING-010), so a company with a *new* id
number and an *existing* name passes the service check and then fails at the database — as a
**500, not a 409**. The inverse case is silent: a known id number with a different name
resolves to the existing row and the submitted name is simply ignored.

**E11 is BR-BILLING-017** — the delete is blocked by the **database**, not by application
logic, and surfaces as a 500 rather than a 409. All five tables in this domain are pure
parents with no outbound foreign keys, so every referential failure here has this shape.

---

## 4. Errors raised about billing data, from the order domain

Two of this domain's rules are enforced **outside it**, in the checkout path, because that is
the only place billing data is ever submitted. The literals belong to the order domain and are
specified in `domain-order/errors.md`; they are cross-referenced here because this domain's
data is what the lookup fails to find.

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E14 | An order names a billing type that does not exist | `BadInputException` | `Specified billing type does not exist` | **400** | **BR-BILLING-003** |
| E15 | An enterprise order omits its billing company | `BadInputException` | raised from `resolveOrCreateBillingCompany` via `getExisting` — `Billing company has no id number` | **400** | BR-BILLING-007 |

**E14 is `OrdersConverterServiceImpl`'s `UNEXISTING_BILLING_TYPE` constant**, checked after
`BillingContract.findBillingTypeByName` returns empty. It is the enforcement point for
BR-BILLING-003: an order must declare a billing type that exists, and only two do
(BR-BILLING-002).

**E15 is the enterprise path.** When the submitted billing type equals
`Constants.BILLING_TYPE_ENTERPRISE`, the converter additionally resolves a billing company
and a billing address (BR-BILLING-007, BR-BILLING-008). A missing company surfaces as this
domain's own E2, raised from inside the contract.

Neither error can be reached through any route this domain owns, because it owns only one
anonymous read.

---

## 5. The error that cannot be raised, and the silence that replaces it

| # | Condition | What happens instead |
|---|---|---|
| E16 | An address with a null `secondLine`, `postalCode` or `notes` already exists and should be reused | **No error, and no match.** `findByFields` compares those columns with `=`, which is never true against NULL, so the query returns empty and checkout **creates a duplicate row**. `UK_ADDR` permits it, because a NULL makes the unique tuple distinct |

This is boundary warning **W1** and the most consequential behaviour in the domain. It is
not an error path at all — it is **silent row growth**, one address per checkout, for any
address with a null optional field. Nothing logs it and nothing surfaces it.

Ported unchanged: correcting the comparison would change which addresses are reused and
which rows accumulate, which is a behavioural change needing its own ruling (G1).

---

## 6. Deliberate non-errors — silence that is contract

### 6.1 A blank patch value
Both patch services blank-guard every key, so patching `""` **leaves the stored value
unchanged** and reports success. **BR-BILLING-015** — a blank value never clears stored
billing data.

### 6.2 An unrecognised filter parameter
`parseMap`'s `default: break` ignores it silently. On the one `GET` route this widens the
result set.

### 6.3 A non-numeric `id` filter
Caught, logged at INFO with
`"Param '{}' couldn't be parsed as number (value: '{}')"`, and the parameter is dropped. The
request proceeds with whatever other filters parsed.

### 6.4 An unvalidated company id number
Nothing checks that it looks like a tax identifier (**BR-BILLING-012**). A 20-character
ceiling from `@Size(min = 1, max = 20)` is the only constraint, and even that is on the
*entity*, not the pojo — so an over-long value fails at the database, not at the boundary.

### 6.5 A no-op patch
`flushPartialChanges` finds the patched copy `equals` to the original — Lombok equality covers
all fields including `id`, which the copy constructor preserved — and **skips the write**,
returning success.

### 6.6 No tax, and no invoice document
**BR-BILLING-018.** Nothing in this domain computes tax or generates a document. Tax is
`TAX_PERCENT = 0.19` hardcoded in the order domain's `updateTotals`. The domain is named for
a capability it does not implement, and the port must not be read as an invitation to
implement it.

---

## 7. What this domain never raises

- **No 403, ever, from anywhere.** There is no billing authority in the 54-code catalogue
  (BR-BILLING-019), so no `@PreAuthorize` references this domain and no authorisation
  decision about billing data is possible.
- **No 401 on the one route** — `GET /data/billing_types` is deliberately anonymous
  (BR-BILLING-016).
- **No optimistic-locking failure.** No `@Version` on any of the four entities.
- **No 409 on any delete.** Referential failures are 500s, per §3.
- **No validation error on `BillingCompanyPojo` or on the three optional `AddressPojo`
  fields.** The first has no annotations at all; the second three are deliberately optional.
