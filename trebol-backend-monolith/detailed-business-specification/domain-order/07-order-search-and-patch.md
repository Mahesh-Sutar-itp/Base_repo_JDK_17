# Order — Search, CRUD, Patch and Controllers

Porting-depth behavioural specification. Subsystem 7 of 7. Paths relative to
`src/main/java/org/trebol/`.

Contains **boundary warning W1** — the sort key that reaches into another service and must be
dropped — and the **D5** patch fix.

---

## 1. `jpa/services/predicates/impl/OrdersPredicateServiceImpl`

```java
public interface OrdersPredicateService extends PredicateService {
    QOrder basePath = QOrder.order;
}
```

`@Service`, no `@Transactional`, private slf4j `logger`. `parseMap` iterates the entry set in
a `switch`:

| Parameter | Predicate | Note |
|---|---|---|
| `id` | **falls through** to `buyOrder` | |
| `buyOrder` | `basePath.id.eq(Long.valueOf(v))` — **early `return`** | discards every other filter |
| `date` | `and(basePath.date.eq(Instant.parse(v)))` | |
| `statusName` | `and(basePath.status.name.eq(v))` | |
| `token` | `and(basePath.transactionToken.eq(v))` | |
| *anything else* | `default: break` — **silently ignored** | |

**Four recognised names** (`id` and `buyOrder` being synonyms), all client-observable contract
(G2).

**Two catch blocks, with different levels:**

```java
catch (NumberFormatException exc) {
    logger.info("Param '{}' couldn't be parsed as number (value: '{}')", paramName, stringValue);
}
catch (DateTimeParseException exc) {
    logger.warn("Param '{}' couldn't be parsed as date (value: '{}')", paramName, stringValue);
}
```

**INFO for a bad number, WARN for a bad date.** The inconsistency is pre-existing; port both
levels as they are.

**BR-ORDER-070** — a malformed date filter is **ignored rather than rejected**. The request
proceeds with whatever other filters parsed, so `?date=not-a-date` silently returns everything.

### 1.1 The `default: break` is where D4's defect lived

`CheckoutServiceImpl` used to pass a key `"statusCode"`, which this switch does not
recognise — so the status filter was **silently dropped** and the callback lookup was by token
alone (`03` §1.3, `04` §5). **BR-ORDER-041 was therefore never true.**

The same silence is why the Phase-0 **D1** fix was needed on `DELETE`: a request whose
parameters are all unrecognised produces an empty `BooleanBuilder`, which would match every
row. `DataCrudGenericController.delete` now rejects that with
`BadInputException("No recognized filtering parameters were provided")` → 400. Register item
**D19** records that the same match-all shape remains reachable through `update` and
`partialUpdate`, ruled PRESERVE in scope.

---

## 2. `jpa/sortspecs/OrdersSortSpec`

```java
@NoArgsConstructor(access = PRIVATE)
public class OrdersSortSpec {
    private static final QOrder BASE_PATH = QOrder.order;
    private static final QPerson CUSTOMER_PATH = BASE_PATH.customer.person;   // line 35
    public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
        "buyOrder",       BASE_PATH.id.asc(),
        "date",           BASE_PATH.date.asc(),
        "status",         BASE_PATH.status.code.asc(),
        "customer",       CUSTOMER_PATH.lastName.asc(),                       // line 40
        "shipper",        BASE_PATH.shipper.name.asc(),
        "totalValue",     BASE_PATH.totalValue.asc(),
        "netValue",       BASE_PATH.netValue.asc(),
        "totalItems",     BASE_PATH.totalItems.asc(),
        "transportValue", BASE_PATH.transportValue.asc());
}
```

**Nine keys, all `.asc()`.** No `taxesValue`, no `paymentType`, no `salesperson` — so three
fields that `OrderPojo` exposes cannot be sorted by.

**`"status"` sorts by `status.code`, not by name** — the one place in the domain where the
numeric code drives behaviour rather than the name (`02` §3). So a status sort is
lifecycle-ordered (`-6 … 6`) rather than alphabetical, which is almost certainly intended and
is worth preserving deliberately.

This closed map is the verified mitigation for register item **D21** (QueryDSL
CVE-2024-49203): `SortSpecParserService` rejects any `sortBy` outside it, so `orderBy` never
receives a raw client string.

### 2.1 Boundary warning W1 — `"customer"` must be dropped

> **`CUSTOMER_PATH = BASE_PATH.customer.person`** (line 35) traverses
> `QOrder.order.customer.person` via `QPerson`, and `"customer"` maps to
> `CUSTOMER_PATH.lastName.asc()` (line 40).

That is a QueryDSL path from `orders` **into the Identity service's `people` table**, through
`customers`. It generates a two-level join across a service boundary and **cannot survive the
extraction**.

**Ruling: drop the key**, per the Phase 3 decision, leaving **eight**.

The alternatives were considered and rejected:

| Option | Why not |
|---|---|
| Denormalise the surname onto `orders` | a new column, a write path to keep it in step, and a migration — new behaviour, not conversion |
| Fetch ids from Identity and sort in memory | breaks pagination, since sorting must happen before the page is cut |
| Keep the join via a database view | reintroduces the shared schema Phase 4 exists to retire |

**`OrdersSortSpecTest` must assert the reduced eight-key map**, so the removal is pinned and a
future reintroduction fails the build. It is the one client-visible capability this conversion
removes, and it is recorded in the exit artefacts as such.

---

## 3. `jpa/services/crud/impl/OrdersCrudServiceImpl` — 115 lines

`@Transactional` + `@Service`, `extends CrudGenericService<OrderPojo, Order>`.

Constructor dependencies: `OrdersRepository`, `OrdersConverterService`, `OrdersPatchService`
(passed to `super` only), **`AddressesConverterService`** and **`ApiProperties`**.

**Exactly three overrides.**

### 3.1 `getExisting`

```java
Long buyOrder = input.getBuyOrder();
if (buyOrder==null) {
    return Optional.empty();
}
return this.ordersRepository.findById(buyOrder);
```

**A null `buyOrder` yields `Optional.empty()` rather than an exception** — unlike every other
domain's `getExisting`, which raises `BadInputException` on a blank business key. The
consequence: `create` sees "no existing order" and proceeds, which is correct for creation,
and `markAs*` sees it too and raises `EntityNotFoundException`. **BR-ORDER-023** —
transitions are identified by the order identifier only.

### 3.2 `readOne` — and boundary warning W3

```java
Optional<Order> matchingOrder = ordersRepository.findOne(conditions);
if (matchingOrder.isPresent()) {
    Order found = matchingOrder.get();
    OrderPojo target = ordersConverterService.convertToPojo(found);
    AddressPojo billingAddress = addressesConverterService.convertToPojo(found.getBillingAddress());
    target.setBillingAddress(billingAddress);                      // <-- UNCONDITIONAL
    if (found.getShippingAddress()!=null) {                        // <-- guarded
        AddressPojo shippingAddress = addressesConverterService.convertToPojo(found.getShippingAddress());
        target.setShippingAddress(shippingAddress);
    }
    List<OrderDetailPojo> details = found.getDetails().stream()
        .map(ordersConverterService::convertDetailToPojo)
        .collect(Collectors.toList());
    target.setDetails(details);
    return target;
}
throw new EntityNotFoundException("No sell matches the filtering conditions");
```

**BR-ORDER-069** — the single-item read hydrates addresses and lines, because
`convertToPojo` deliberately sets none of them (`03` §3.7).

> **W3: `getBillingAddress()` is converted unconditionally while `getShippingAddress()` is
> null-guarded.** `Order.billingAddress` is nullable (`billing_address_id` has no
> `nullable = false`), and **BR-ORDER-059** states both addresses are optional. So any order
> without a billing address — every individual-billing order that supplied none — **throws an
> NPE inside the converter** on a single-item read.

The two lines sit adjacent and differ only in the guard, which is what makes it clearly an
omission rather than a decision. **Preserved** (G1): adding the guard would change a 500 into
a 200 with a null field, which is a behavioural change needing its own ruling.

It is reachable through `GET /data/orders?buyOrder=N` (§5.1) and through the **D11
dispatcher**, which resolves orders by id through this method (`05` §5.2) — so a failing
outbox entry could be caused by this NPE, recorded as `last_error`, and retried five times.
That interaction is worth knowing.

### 3.3 `flushPartialChanges` — the processed-order lock

```java
Integer statusCode = existingEntity.getStatus().getCode();
if ((statusCode >= 3 || statusCode < 0) && !apiProperties.isAbleToEditOrdersAfterBeingProcessed()) {
    throw new BadInputException("The requested transaction cannot be modified");
}
return super.flushPartialChanges(changes, existingEntity);
```

| Rule | Behaviour |
|---|---|
| **BR-ORDER-029** | processed orders are locked against partial updates |
| **BR-ORDER-030** | the lock is a **configuration toggle**, not an invariant — `trebol.api.able-to-edit-orders-after-being-processed` |
| **BR-ORDER-031** | **the lock does not cover full replacement** — it is on `flushPartialChanges` only, so `PUT /data/orders` bypasses it entirely |

**The condition is code-driven**, the only such check in the domain. `statusCode >= 3` covers
`Paid, Unconfirmed` upward; `< 0` covers the three negative terminal states. **Codes 1
(`Pending`) and 2 (`Payment Started`) are *not* locked** — so an order mid-payment can be
patched, which is the interaction that makes `Order`'s copy-constructor nonce omission worth
documenting (`01` §1.4, W5).

**BR-ORDER-031 is the more consequential half:** a caller with `orders:update` can `PUT` a
completed order and change anything the converter accepts, with no lock at all. Preserved.

### 3.4 Inherited behaviour

From `CrudGenericService`: `ITEM_NOT_FOUND = "Requested item(s) not found"`,
`ITEM_ALREADY_EXISTS = "The item already exists"`, the
`"Cannot update more than one item at a time"` count guard on `update`/`partialUpdate`, and
the `catch (BadInputException e) { throw new RuntimeException(e); }` wrapping that turns a
patch-time 400 into a **500**.

That last one matters here: **every `BadInputException` raised by `OrdersPatchServiceImpl` —
including D5's two new messages — reaches the client as a 500, not a 400.** Recorded in
`errors.md`.

---

## 4. `jpa/services/patch/impl/OrdersPatchServiceImpl` — 133 lines

Changed by **D5** and **D10**.

```java
static final String STATUS_IS_NOT_PATCHABLE =
    "An order's status cannot be changed through this route; use the confirmation, rejection or completion routes instead";
static final String ILLEGAL_STATUS_TRANSITION =
    "The order cannot move to the requested status from its current one";
static final String NON_TEXTUAL_PATCH_VALUE =
    "A patched field was not textual where text was expected";
```

`patchExistingEntity(Map<String,Object>, Order existing)` starts `Order target = new Order(existing);`
then, inside a `try`:

| Key | Behaviour |
|---|---|
| `date` | cast to `String`; if non-null, `Instant.parse(...)` → `target.setDate(...)` |
| **`status`** | **always throws** — see §4.1 |
| `paymentType` | `billingContract.findPaymentTypeByName(name).ifPresent(target::setPaymentType)` — **an unknown name is silently ignored** |
| `shipper` | `shipperLookupContract.findShipperByName(name).ifPresent(target::setShipper)` — **likewise** |

```java
catch (ClassCastException ex) {
    throw new BadInputException(NON_TEXTUAL_PATCH_VALUE);
}
```

The pojo overload throws
`UnsupportedOperationException("This method signature has been deprecated")`.

**BR-ORDER-032** — unknown names on an order patch are silently ignored. That is the
`.ifPresent` in both the `paymentType` and `shipper` branches, and it is **BR-SHIPPER-015**'s
asymmetry: the same unknown carrier name that `OrdersConverterServiceImpl` rejects with
`"Specified shipper does not exist"` is swallowed here.

**Per register item D27, both are in any case silent no-ops** — `paymentType` and `shipper`
are mapped `updatable = false`, so even a *recognised* name is discarded on save.

### 4.1 Register item D5 — the status branch

```java
String statusName = (String) changes.get("status");
if (!StringUtils.isBlank(statusName)) {
    String currentStatus = existing.getStatus()==null ? null : existing.getStatus().getName();
    if (!OrderStatusTransitions.isLegal(currentStatus, statusName)) {
        throw new BadInputException(ILLEGAL_STATUS_TRANSITION);
    }
    throw new BadInputException(STATUS_IS_NOT_PATCHABLE);
}
```

**Both branches throw.** The transition table is consulted **first** so that an *illegal*
target is reported as illegal, even though no target can be applied here.

**Why this shape.** An empirical probe written *before* the fix —
`OrderStatusPatchPersistenceProbeTest`, which asks Hibernate's
`EntityPersister.getPropertyUpdateability()` directly — established that **`Order.status` is
mapped `updatable = false`, so a patched status was silently discarded and the request
reported success regardless**.

> **D5 is therefore not the state-machine bypass the register recorded.** It was a write that
> **reported success while changing nothing** — and arguably worse than a bypass, because a
> bypass would at least be visible in the data.

Silently accepting an instruction that cannot be carried out is the part worth fixing, so it
is now rejected outright, naming the routes that do work. **If `Order.status` is ever made
updatable, the guard is already the right one and the probe test fails loudly to say so.**

**D5 also replaced the truncated literal.** The `ClassCastException` branch threw
`new BadInputException("The ")` — literally the string `"The "`, carrying no information a
client could act on. **BR-ORDER-033** recorded that as current behaviour; it is now
`NON_TEXTUAL_PATCH_VALUE` and the rule is stale (`00` §3).

### 4.2 The two inverted assertions

| Was | Now |
|---|---|
| `patches_status` — asserted the patched status was set on the returned entity | `rejects_a_status_change_because_this_route_cannot_apply_one` **+** `rejects_even_a_legal_status_transition_through_this_route` |
| `patches_all_fields` — patched all four keys including status | `patches_every_other_field_at_once` — the other three keys, unchanged |

The second new test exists to exercise the **legal**-transition branch: the shared fixture's
status is `ANY`, which the table does not know, so it reaches the *illegal* branch. The test
builds a `Pending` order explicitly so both branches are genuinely covered, with their
different messages.

---

## 5. `api/controllers/DataOrdersController` — 199 lines

```java
@RestController
@RequestMapping("/data/orders")
@Tag(name = "Orders management")
@PreAuthorize("isAuthenticated()")
public class DataOrdersController extends DataCrudGenericController<OrderPojo, Order> {
```

**Both layers of guard** — a class-level `isAuthenticated()` **and** a per-method authority.
**BR-ORDER-061** — all order administration requires authentication **plus** an authority.
That is the safer shape, and it contrasts with `DataShippersController`, which has only the
per-method layer.

| Verb | Path | Authority | `@ResponseStatus` |
|---|---|---|---|
| GET | `/data/orders` | `orders:read` | — (200) |
| POST | `/data/orders` | `orders:create` | **CREATED** |
| PUT | `/data/orders` | `orders:update` | NO_CONTENT |
| PATCH | `/data/orders` | `orders:update` | NO_CONTENT |
| DELETE | `/data/orders` | `orders:delete` | NO_CONTENT |
| POST | `/data/orders/confirmation` | `orders:update` | **NO_CONTENT** |
| POST | `/data/orders/rejection` | `orders:update` | **none → 200** |
| POST | `/data/orders/completion` | `orders:update` | **none → 200** |

**Boundary warning W12:** the three process routes are siblings doing the same kind of work,
and `/confirmation` returns **204** while `/rejection` and `/completion` return **200**. Port
the inconsistency (G2).

The three process methods take `@RequestBody OrderPojo sell` **without `@Valid`** — correct,
since only `buyOrder` is read (BR-ORDER-023), and requiring a full valid order to confirm one
would be wrong.

All three still declare `throws MailingServiceException` and the controller still injects
`@Nullable MailingService`, though neither is used after D11 (`05` §7).

### 5.1 `readMany` — two special cases

```java
if (allRequestParams!=null && allRequestParams.containsKey("buyOrder")) {
    Predicate predicate = predicateService.parseMap(allRequestParams);
    OrderPojo orderPojo = crudService.readOne(predicate);
    DataPagePojo<OrderPojo> page = new DataPagePojo<>();
    page.setItems(List.of(orderPojo));
    page.setTotalCount(1);
    page.setPageSize(1);
    return page;
}
if (!allRequestParams.containsKey("sortBy") && !allRequestParams.containsKey("order")) {
    allRequestParams = new HashMap<>(allRequestParams);
    allRequestParams.put("sortBy", "buyOrder");
    allRequestParams.put("order", "desc");
}
return super.readMany(allRequestParams);
```

| Rule | Behaviour |
|---|---|
| **BR-ORDER-064** | a `buyOrder` parameter is **special-cased into a single-item page** — it routes to `readOne`, so it returns the *hydrated* order with addresses and details, unlike a normal page |
| **BR-ORDER-063** | the search **defaults its sort** to `buyOrder desc` — newest first — when the client supplies neither `sortBy` nor `order` |

**The `buyOrder` branch is the reachable path to W3's NPE** (§3.2), since it is the only
caller of `readOne` from HTTP.

Note the default is applied **only if neither** parameter is present; supplying `order=asc`
alone leaves `sortBy` unset and the kernel's own default applies.

---

## 6. Tests (G8)

| Tests | Class | Note |
|---|---|---|
| **8** | `OrdersPatchServiceImplTest` | **two assertions inverted by D5** (§4.2) |
| 6 | `DataOrdersControllerTest` | |
| 3 | `OrdersCrudServiceImplTest` | does **not** cover W3's NPE |
| 2 | `OrdersPredicateServiceImplTest` | |
| 3 | `OrderDomainCouplingArchitectureTest` | the Gate A pin — **must be extended for W2** (`06` §3.2) |

**There is no `OrdersSortSpecTest` today.** Step 3.8 adds one, because the eight-key map is
the pin for W1's dropped key — without it, nothing prevents `"customer"` being reintroduced.

---

## 7. Rules covered

BR-ORDER-023, 026, 027, **028 (corrected)**, 029, 030, 031, 032, **033 (corrected)**,
**041 (corrected)**, 061, 063, 064, 068, 069, 070.
