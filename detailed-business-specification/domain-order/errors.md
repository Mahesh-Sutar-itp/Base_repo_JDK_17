# Order — Error Conditions

Every error this domain can produce, with its **verbatim** message literal. Messages are
client-observable contract and port unchanged (G2).

Where Phase 3 changed an error, the change is named.

---

## 1. Checkout and conversion — `OrdersConverterServiceImpl`

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E1 | A payment type is absent from the submitted order | `BadInputException` | `A payment type has to be included` | **400** | BR-ORDER-004 |
| E2 | The named payment type does not exist | **`RuntimeException`** | `The payment type does not exist` | **500** | BR-ORDER-004 |
| E3 | The named billing type does not exist | `BadInputException` | `Specified billing type does not exist` | **400** | BR-ORDER-005, BR-BILLING-003 |
| E4 | The named shipper does not exist | `BadInputException` | `Specified shipper does not exist` | **400** | BR-ORDER-007, BR-SHIPPER-015 |
| E5 | A line's product barcode is blank | `BadInputException` → **re-wrapped** | `Product barcode must be valid` | **500** | BR-ORDER-008 |
| E6 | A line's product does not resolve by barcode | `BadInputException` → **re-wrapped** | `Unexisting product in sell details` | **500** | BR-ORDER-008 |
| E7 | `applyChangesToExistingEntity` | `UnsupportedOperationException` | `This method is deprecated` | 500 | BR-ORDER-013 |

**E1 and E2 are the asymmetry worth noting:** a *missing* payment type is a 400, an *unknown*
one is a bare `RuntimeException` → **500**. Both preserved.

**E5 and E6 are 500s despite being client errors.** The detail-conversion loop is wrapped:

```java
catch (BadInputException exc) { throw new RuntimeException(exc.getMessage(), exc); }
```

So every detail-level validation failure loses its 400 status on the way out. The message
survives; the status code does not. Preserved (G1).

---

## 2. The status machine — `OrdersProcessServiceImpl`

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E8 | A transition from a status the table does not permit | `BadInputException` | `The transaction is not in a valid state for this api` | **400** | BR-ORDER-022 |
| E9 | The target status row is missing from `order_statuses` | `IllegalStateException` | `No status matches the '<name>' name - Is the database empty or corrupt?` | **500** | BR-ORDER-021 |
| E10 | `markAs*` called for an order that does not exist | `EntityNotFoundException` | inherited `Requested item(s) not found` | **404** | BR-ORDER-023 |

**E8's wording is preserved exactly**, including `"for this api"` rather than "for this
operation" — the constant is named
`THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION` while its value says `api`. The
mismatch between constant name and literal is pre-existing; the **literal** is contract.

**E9 is assembled by concatenation** from two constants with the status name between them
(`02` §5). Port the concatenation, not a reformatted equivalent.

**Since D10, E8 also covers any attempt to reach one of the four unreachable statuses** — the
transition table rejects them as a target from every source.

---

## 3. The payment adapter

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E11 | Transbank cannot be reached or cannot commit during confirmation | `PaymentServiceException` | `Webpay failed to confirm the transaction` | **500** | BR-ORDER-037 |
| E12 | Transbank rejects the transaction-creation request | `PaymentServiceException` | the `TransactionCreateException` wrapper message | **500** | BR-ORDER-038 |
| E13 | A confirmation arrives for an order in the wrong state | `IllegalStateException` | `Transaction could not be confirmed` | **500** | BR-ORDER-039 |

**E11 is register item D3's fix.** It previously returned the magic value `1`, which
`CheckoutServiceImpl` read as a decline and recorded as `Payment Failed`.

> **BR-ORDER-036 described that as current behaviour. It is now fixed** — an infrastructure
> failure raises E11, and the order **keeps `Payment Started`**, the truthful state.

The literal was **reused, not invented** — it already existed on the `IOException` branch that
the commit exception was merged into, so D3 introduced no new client-visible string.

**E13 is deliberately opaque** (BR-ORDER-039). The actual status is logged at ERROR
(`"Incorrect state of sell, was: {}"`) and never surfaced to the caller.

All three reach the client through `PublicCheckoutController`'s handler:

```java
@ResponseStatus(INTERNAL_SERVER_ERROR)
@ExceptionHandler(PaymentServiceException.class)
public String handleException(PaymentServiceException ex) { return ex.getMessage(); }
```

**The message is the response body**, so every payment-service literal is client-visible.

---

## 4. The payment callbacks — including D4's new condition

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E14 | No token parameter on either `/validate` mapping | `BadInputException` | `No transaction token was provided` | **400** | BR-ORDER-040 |
| E15 | **The callback presents no nonce, a blank nonce, or a mismatched one** | `BadInputException` | **`The transaction callback could not be authenticated`** | **400** | — (**new, D4**) |
| E16 | The token matches no order | `EntityNotFoundException` | `No sell matches the filtering conditions` | **404** | — |

**E15 is the one new client-visible literal in all of Phase 3.** It was unavoidable, because
callback authentication is a new error condition. Three properties are deliberate:

- it **does not say which check failed** — missing, blank and mismatched are indistinguishable;
- it **does not echo either value**;
- it is raised **before any transition is attempted**, so a rejected callback has no side
  effect whatsoever.

**An order with no recorded nonce cannot be confirmed by callback** — orders predating the
column, and orders that never began a checkout, both produce E15. The alternative, where a
null expected value matches a null presented value, would be a bypass.

**A replayed callback is not an error at all** — see §8.2.

---

## 5. The receipt subsystem

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E17 | A blank receipt token in the path | `BadInputException` | `An incorrect receipt token was provided` | **400** | BR-ORDER-046 |
| E18 | The token matches no order | `EntityNotFoundException` | `The transaction could not be found, no receipt can be created` | **404** | BR-ORDER-046 |

**Two distinct messages for two distinct failures** — unusually careful for this codebase, and
worth preserving precisely. BR-ORDER-046 exists to record exactly this.

---

## 6. CRUD, patch and search

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E19 | `readOne` matches nothing | `EntityNotFoundException` | `No sell matches the filtering conditions` | **404** | — |
| E20 | Patching an order at status code ≥ 3 or < 0, with the toggle off | `BadInputException` → **wrapped** | `The requested transaction cannot be modified` | **500** | BR-ORDER-029, 030 |
| E21 | **Patching an order's `status`, where the transition is illegal** | `BadInputException` → **wrapped** | `The order cannot move to the requested status from its current one` | **500** | **D5** |
| E22 | **Patching an order's `status`, where the transition is legal** | `BadInputException` → **wrapped** | `An order's status cannot be changed through this route; use the confirmation, rejection or completion routes instead` | **500** | **D5** |
| E23 | A non-string value under a textual patch key | `BadInputException` → **wrapped** | `A patched field was not textual where text was expected` | **500** | BR-ORDER-033 (corrected) |
| E24 | `patchExistingEntity(OrderPojo, Order)` | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 | — |
| E25 | `PUT`/`PATCH` whose filters match more than one row | `RuntimeException` | `Cannot update more than one item at a time` | **500** | — |
| E26 | `DELETE` whose parameters are all unrecognised | `BadInputException` | `No recognized filtering parameters were provided` | **400** | Phase-0 **D1** |
| E27 | Creating an order whose `buyOrder` already exists | `EntityExistsException` | `The item already exists` | **409** | — |

### 6.1 Every patch error is a 500, not a 400

**E20 through E23 are all raised as `BadInputException` and all arrive as 500s**, because
`CrudGenericService` wraps them:

```java
catch (BadInputException e) { throw new RuntimeException(e); }
```

That is pre-existing and preserved — but it is the single most misleading thing in this
domain's error surface, and it means **D5's two carefully-worded messages reach the client
with a 500 status**. The messages are right; the status code is inherited and wrong. Recorded
rather than fixed (G1).

### 6.2 E23 replaced the truncated literal

The `ClassCastException` branch previously threw `new BadInputException("The ")` — literally
the string `"The "`. **BR-ORDER-033** recorded that; **D5 replaced it** and the rule is stale
(`00` §3).

---

## 7. NPEs and framework failures

| # | Condition | Exception | HTTP | Where |
|---|---|---|---|---|
| E28 | **A single-item read of an order with no billing address** | `NullPointerException` | **500** | **W3** — `OrdersCrudServiceImpl.readOne` converts `getBillingAddress()` unconditionally |
| E29 | `convertToPojo` on an order whose `Pending` status row is missing | `NullPointerException` | 500 | `03` §3.1 — `ifPresent` leaves the status null |
| E30 | `OrderEntity2ReceiptPojo.convert(null)` | `NullPointerException` | 500 | `06` §4.1 — no null check |
| E31 | A `ReceiptPojo` that the `ConversionService` yields as null | `NullPointerException` | 500 | **W10** |
| E32 | `POST`/`PUT /data/orders` with an empty `details` collection | `MethodArgumentNotValidException` | **400** | `@NotEmpty`, BR-ORDER-002 |
| E33 | A line with `units < 1` or a null product | `MethodArgumentNotValidException` | **400** | `@Min(1)`, `@NotNull`, BR-ORDER-003 |
| E34 | A blank `paymentType` on the body | `MethodArgumentNotValidException` | **400** | `@NotBlank` |

**E28 is reachable from HTTP** through `GET /data/orders?buyOrder=N` (`07` §5.1), and **also
from the D11 dispatcher**, which resolves orders through the same method. So an order with no
billing address produces a failing outbox entry, recorded as `last_error`, retried five times
and then abandoned. That interaction is worth knowing and is recorded in `07` §3.2.

---

## 8. Deliberate non-errors — silence that is contract

### 8.1 An unmapped status produces no mail
**BR-ORDER-051.** Only four customer statuses and three owner statuses have templates. Any
other status produces **no mail and no error** — and since D11, the outbox entry is still
marked dispatched, because the dispatcher treats "nothing to send" as success.

### 8.2 A replayed payment callback
**Register item D4.** An order already in `Payment Cancelled`, `Payment Failed` or
`Paid, Unconfirmed` is returned unchanged, the controller issues the **same 303**, and **no
second transition and no second provider commit** occur.

Previously this produced **HTTP 500** for what is a normal event.

### 8.3 An unknown `paymentType` or `shipper` name on a patch
**BR-ORDER-032.** Both branches use `.ifPresent(...)`, so an unrecognised name is **silently
ignored**. Per register item **D27** both are in any case no-ops, because the associations are
`updatable = false`.

Note the asymmetry with E4: the same unknown shipper name that checkout **rejects** is
swallowed here (**BR-SHIPPER-015**).

### 8.4 A malformed date filter
**BR-ORDER-070.** Caught, logged at **WARN**, and the parameter is dropped — so
`?date=not-a-date` silently returns everything. A malformed `id` is caught at **INFO**. Two
levels, preserved.

### 8.5 An unrecognised filter parameter
`default: break`. On `GET` this widens the result set; on `update`/`partialUpdate` it is the
**D19** match-all shape, ruled PRESERVE in scope. On `DELETE` it is now E26.

### 8.6 No stock check, ever
**BR-ORDER-012.** Nothing validates availability at any point. An order can be placed for any
quantity of anything.

### 8.7 Transport value is never set
**BR-ORDER-017.** The column is mandatory and stored; **no code path writes it**, and
`totalValue` excludes it (**BR-ORDER-016**). A shipping charge can be recorded and will never
be billed. Both halves preserved — see `03` §4.1 for why fixing either alone would be worse.

### 8.8 Deleting an order orphans its lines
**BR-ORDER-067.** `details` cascades `PERSIST` only, with no `orphanRemoval`, and the child FK
is `NO_CONSTRAINT`. `order_details` rows are orphaned **silently**.

### 8.9 A no-op patch
`flushPartialChanges` finds the patched copy `equals` to the original and **skips the write**,
returning 204.

---

## 9. What this domain never raises

- **No 403 on the two anonymous callbacks or the public receipt.** None carries a
  `@PreAuthorize`; the nonce is the control on the callbacks (`permissions.md`).
- **No 409 on any delete.** Referential failures from `billing_types`, `shippers` and the rest
  are `DataIntegrityViolationException` → 500.
- **No optimistic-locking failure.** No `@Version` on any of the four entities.
- **No 400 from any patch path.** All of them are wrapped into 500s (§6.1).
- **No error when a status transition is attempted into one of D10's four unreachable
  statuses** that is distinguishable from any other illegal transition — all produce E8.
