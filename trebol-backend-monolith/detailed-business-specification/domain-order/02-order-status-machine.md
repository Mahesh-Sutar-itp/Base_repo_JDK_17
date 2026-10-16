# Order — The Status Machine

Porting-depth behavioural specification. Subsystem 2 of 7. Paths relative to
`src/main/java/org/trebol/`.

This subsystem was **materially changed in Phase 3** by register items **D5** and **D10**.
What follows is the post-fix behaviour, with every divergence from the recorded rules named.

---

## 1. The twelve seeded statuses — not eleven

`data.sql` seeds **twelve** rows into `order_statuses` and restarts the identity sequence at
**13**. The transformation definition and the defect register both say eleven. **The
correction is recorded in `defect-register-rulings.md`** and the count is asserted by
`OrderStatusTransitionsTest.there_are_twelve_seeded_statuses_not_eleven`.

| Code | Name | Reachable? |
|---|---|---|
| **-6** | `Returned` | **no — D10** |
| **-5** | `Delivery Failed` | **no — D10** |
| **-4** | `Delivery Cancelled` | **no — D10** |
| -3 | `Rejected` | yes, terminal |
| -2 | `Payment Failed` | yes, terminal |
| -1 | `Payment Cancelled` | yes, terminal |
| 1 | `Pending` | yes, initial |
| 2 | `Payment Started` | yes |
| 3 | `Paid, Unconfirmed` | yes |
| 4 | `Paid, Confirmed` | yes |
| **05** | `Delivery On Route` | **no — D10** |
| 6 | `Delivery Complete` | yes, terminal |

Three details that are contract (G2):

- **The two comma-bearing names** — `"Paid, Unconfirmed"` and `"Paid, Confirmed"` — are
  client-observable literals. `OrderStatusTransitionsTest` pins both, because a well-meaning
  rename would silently break every transition through them.
- **`Delivery On Route` is seeded with code `05`**, zero-padded in the source seed data while
  every sibling is unpadded.
- **`Constants` does not name the four unreachable statuses at all.** That is not an
  oversight: no code path names them because none reaches them.

---

## 2. The seven transitions

Derived from the `markAs*` preconditions that were already in the code. **No rule changed in
Phase 3** — they were only moved into one place.

```
Pending            -> Payment Started
Payment Started    -> Payment Cancelled | Payment Failed | Paid, Unconfirmed
Paid, Unconfirmed  -> Paid, Confirmed   | Rejected
Paid, Confirmed    -> Delivery Complete
```

| Method | From | To | Reached by |
|---|---|---|---|
| `markAsStarted` | `Pending` | `Payment Started` | `CheckoutServiceImpl.requestTransactionStart` |
| `markAsAborted` | `Payment Started` | `Payment Cancelled` | `POST /public/checkout/validate` |
| `markAsFailed` | `Payment Started` | `Payment Failed` | `GET /public/checkout/validate`, provider decline |
| `markAsPaid` | `Payment Started` | `Paid, Unconfirmed` | `GET /public/checkout/validate`, success |
| `markAsConfirmed` | `Paid, Unconfirmed` | `Paid, Confirmed` | `POST /data/orders/confirmation` |
| `markAsRejected` | `Paid, Unconfirmed` | `Rejected` | `POST /data/orders/rejection` |
| `markAsCompleted` | `Paid, Confirmed` | `Delivery Complete` | `POST /data/orders/completion` |

**Terminal:** `Payment Cancelled`, `Payment Failed`, `Rejected`, `Delivery Complete`. No
transition leaves any of them, and **no status may transition to itself** — asserted
explicitly.

**BR-ORDER-022** — each transition validates the exact current status.
**BR-ORDER-023** — transitions are identified by the **order identifier only**: every
`markAs*` takes an `OrderPojo` and resolves it through `crudService.getExisting(sell)`, which
reads `buyOrder` and ignores every other field in the body. A caller can therefore send
`{"buyOrder": 7}` and nothing else.

---

## 3. `jpa/services/OrderStatusTransitions` — one source of truth

**Added in Phase 3, step 3.4.** Before it, the machine existed only as **seven separate `if`
statements** inside `OrdersProcessServiceImpl`, each checking one expected source status. It
could not be read in one place, tested as a machine, or enforced anywhere else.

```java
@NoArgsConstructor(access = PRIVATE)
public final class OrderStatusTransitions {
    public static final String ORDER_STATUS_RETURNED = "Returned";
    public static final String ORDER_STATUS_DELIVERY_FAILED = "Delivery Failed";
    public static final String ORDER_STATUS_DELIVERY_CANCELLED = "Delivery Cancelled";
    public static final String ORDER_STATUS_DELIVERY_ON_ROUTE = "Delivery On Route";

    public static final Set<String> UNREACHABLE_STATUSES = Set.of(...);  // the four above
    public static final Set<String> REACHABLE_STATUSES = Set.of(...);    // the eight others

    public static boolean isTerminal(String status);
    public static boolean isLegal(String source, String target);
    public static Set<String> legalTargetsFrom(String source);
}
```

| Property | Behaviour |
|---|---|
| A **null** source or target | never legal |
| An **unknown** status name, in either position | never legal |
| A status to **itself** | never legal |
| One of D10's four as a **target** | never legal, **from any source** |
| One of D10's four as a **source** | no legal targets, and **not** "terminal" — a terminal status is one an order can legitimately reach and then stay in; these cannot be reached at all |

**The four unreachable names are declared as string literals here rather than promoted to
`Constants`** — deliberately. Adding a constant would imply they are usable.

### 3.1 How the write path consults it

All seven `markAs*` preconditions were rewritten to:

```java
if (!OrderStatusTransitions.isLegal(existingOrder.getStatus().getName(), TARGET_STATUS)) {
    throw new BadInputException(THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION);
}
```

**Same exception type, same verbatim message** —
`"The transaction is not in a valid state for this api"`, including its existing wording. The
rule did not change; only where it is stated did. The two write paths — `markAs*` and the
patch service — now consult the same table and **cannot drift apart**.

---

## 4. Register item D10 — the four unreachable statuses

**Ruling: PRESERVE the seed rows, with a recorded limitation. Implemented in step 3.4.**

`Returned` (-6), `Delivery Failed` (-5), `Delivery Cancelled` (-4) and `Delivery On Route`
(05) are seeded but reached by no code path. **BR-ORDER-025** records four unreachable
statuses and is the one rule in this subsystem that was already accurate.

**The seed rows are preserved exactly** — the data stays, the transitions do not.
`isLegal` rejects all four as a target from every source, asserted exhaustively by
`OrderStatusTransitionsTest.the_four_d10_statuses_are_rejected_as_targets_from_every_source`.

**Why preserve rows that nothing can reach?** Because deleting them is a data migration with
no behavioural benefit, and because their presence is the evidence that a delivery lifecycle
was designed and not built. A future delivery feature would add the transitions, not the rows.

---

## 5. `api/services/impl/OrdersProcessServiceImpl` — the only legitimate writer

`@Transactional` (class level) + `@Service`. Constructor dependencies: `OrdersCrudService`,
`OrdersRepository`, `OrderDetailsRepository`, `OrderStatusesRepository`,
`OrdersConverterService`, `ProductsConverterService`, and — **added by D11** —
`MailOutboxService`.

Three verbatim constants:

```java
THE_TRANSACTION_IS_NOT_IN_A_VALID_STATE_FOR_THIS_OPERATION
    = "The transaction is not in a valid state for this api"
NO_STATUS_MATCHES_THE = "No status matches the"
NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT = "name - Is the database empty or corrupt?"
```

The last two are **concatenated at the throw site** with the status name between them:

```java
throw new IllegalStateException(NO_STATUS_MATCHES_THE + " '" + ORDER_STATUS_PAYMENT_STARTED
    + "' " + NAME_IS_THE_DATABASE_EMPTY_OR_CORRUPT);
```

producing e.g. `No status matches the 'Payment Started' name - Is the database empty or
corrupt?` — **BR-ORDER-021**, a missing status row is a data-integrity failure. Port the
concatenation, not a reformatted equivalent.

There is also a `// TODO figure out how to shorten below methods` and a
`// TODO to compare statuses use numbers, not strings` above `markAsStarted`. Both are
pre-existing and preserved.

### 5.1 The common shape of every `markAs*`

1. `fetchExistingOrThrowException(sell)` — `crudService.getExisting(sell)`, or
   `EntityNotFoundException`;
2. the transition-table check (§3.1);
3. `orderStatusesRepository.findByName(TARGET)` — empty is the `IllegalStateException` above;
4. **`ordersRepository.setStatus(id, status)`** — a direct `@Modifying` update;
5. `convertOrThrowException(existingOrder)` → an `OrderPojo`;
6. `target.setStatus(TARGET)` — set on the **pojo**, because step 4 bypassed the aggregate;
7. **the D11 outbox enqueue**, where applicable (`05` §3);
8. return the pojo.

**Step 4 is BR-ORDER-026**, and it exists because of **BR-ORDER-027**: `Order.status` is
`updatable = false`, so saving the aggregate would not write the column. Step 6 is the
consequence — the returned pojo is corrected by hand because the entity in memory still holds
the old status.

### 5.2 The two methods that do more

**`markAsStarted`** additionally persists the token **and**, since D4, the nonce:

```java
ordersRepository.setStatus(existingOrder.getId(), startedStatus.get());
ordersRepository.setTransactionToken(existingOrder.getId(), sell.getToken());
ordersRepository.setCallbackNonce(existingOrder.getId(), callbackNonce);
```

**BR-ORDER-024** — starting a payment also stores the payment token. D4 added the third line,
in the **same transaction**, so an order can never sit in `Payment Started` without the nonce
that authenticates its callback (`04` §5).

Its signature changed in D4: `markAsStarted(OrderPojo sell, String callbackNonce)`. The nonce
is generated by the **caller**, because `CheckoutServiceImpl` needs it before it asks the
payment service for a redirection URL.

**`markAsPaid`** additionally rebuilds the detail collection: it reads
`orderDetailsRepository`, maps each line through `productConverterService`, and
`target.setDetails(pojoDetails)` — the only transition that returns a fully hydrated pojo,
because it is the one whose notification needs line data.

### 5.3 `findCallbackNonce` — added by D4

```java
@Override
@Transactional(readOnly = true)
public String findCallbackNonce(String transactionToken) {
    return ordersRepository.findByTransactionToken(transactionToken)
        .map(Order::getCallbackNonce)
        .orElse(null);
}
```

**It is exposed here, rather than by injecting `OrdersRepository` into
`CheckoutServiceImpl`, for a specific reason:** Gate A's verification grep matches on
`Checkout`, so a repository import there would reopen the exact coupling Gate A closed. The
placement is structural, not stylistic, and the javadoc says so.

---

## 6. `jpa/repositories/OrdersRepository` — the status writers

```java
Optional<Order> findByTransactionToken(String token);

@Query("SELECT s FROM Order s JOIN FETCH s.details WHERE s.id = :id")
Optional<Order> findByIdWithDetails(@Param("id") Long id);

@Modifying(clearAutomatically = true)
@Query("UPDATE Order s SET s.status = :status WHERE s.id = :id")
int setStatus(@Param("id") Long id, @Param("status") OrderStatus status);

@Modifying
@Query("UPDATE Order s SET s.transactionToken = :token WHERE s.id = :id")
int setTransactionToken(@Param("id") Long id, @Param("token") String token);

@Modifying(clearAutomatically = true)                       // added by D4
@Query("UPDATE Order s SET s.callbackNonce = :nonce WHERE s.id = :id")
int setCallbackNonce(@Param("id") Long id, @Param("nonce") String nonce);
```

**`setStatus` and `setCallbackNonce` carry `clearAutomatically = true`; `setTransactionToken`
does not.** The inconsistency is pre-existing for the first two and was matched deliberately
for the third — D4's `setCallbackNonce` mirrors `setStatus`, the annotation it sits beside.

These three `@Modifying` updates are the **only** way a status, token or nonce is ever
written. **An explicit JPQL update is required precisely because the corresponding
associations and columns bypass or are excluded from the aggregate save path** — `status` is
`updatable = false` (BR-ORDER-027), and the generic save path cannot be relied upon for any
of them.

---

## 7. The order-status catalogue is read-only

**BR-ORDER-065.** `DataOrderStatusesController` exposes **`GET` only**, guarded by
`@PreAuthorize("isAuthenticated()")` at class level — see `permissions.md`. There is no
create, update, patch or delete route, so the twelve statuses are fixed by seed data.

A full set of supporting services nevertheless exists — `OrderStatusesCrudServiceImpl`,
`OrderStatusesConverterServiceImpl`, `OrderStatusesPatchServiceImpl` (5 tests),
`OrderStatusesPredicateServiceImpl` — all unreachable over HTTP beyond the read.
`OrderStatusesCrudServiceImpl.getExisting` rejects a blank name (**BR-ORDER-066**).

All of it ports. Unreachable code with tests is still behaviour under G3, and the patch
service's 5 tests are the only specification of what a status patch *would* do.

---

## 8. Tests (G8)

| Tests | Class | Role |
|---|---|---|
| **15** | `OrderStatusTransitionsTest` | **new in D10.** The full **144-pair cross-product** of all twelve statuses, so a transition cannot be added, removed or mistyped without a failure. Also pins the twelve-not-eleven count and both comma-bearing names |
| **3** | `OrderStatusPatchPersistenceProbeTest` | **new in D5.** Asks Hibernate's `EntityPersister.getPropertyUpdateability()` directly, pinning that `status`, `paymentType` and `shipper` are **excluded from generated `UPDATE`s** while `date` is not |
| 25 | `OrdersProcessServiceImplTest` | the seven transitions, in nested classes per method |
| 5 | `OrderStatusesPatchServiceImplTest` | the unreachable status-patch path |
| 2 | `OrderStatusesConverterServiceImplTest` | |
| 1 each | `OrderStatusesCrudServiceImplTest`, `OrderStatusesPredicateServiceImplTest`, `DataOrderStatusesControllerTest` | |

**`OrderStatusPatchPersistenceProbeTest` is the one to protect.** It is the empirical record
that D5 is not the defect the register described, and if it ever fails, a patched status has
become capable of reaching the database and the D5 ruling must be revisited. Its javadoc says
exactly that.

---

## 9. Rules covered

BR-ORDER-001, 020, 021, 022, 023, 024, 025, 026, 027, **028 (corrected)**, 065, 066.

**BR-ORDER-028 states "the order status IS patchable despite the mapping".** It was **never
true** — `OrderStatusPatchPersistenceProbeTest` proves Hibernate excludes the column from
every `UPDATE`, so a patched status was **silently discarded**. Since D5 it is rejected
outright. See `00` §3 and `07` §4.
