# Order — Checkout and Creation

Porting-depth behavioural specification. Subsystem 3 of 7. Paths relative to
`src/main/java/org/trebol/`.

This is the subsystem that **crosses the most boundaries**: it resolves products from the
catalogue, a customer from identity, and billing and shipper data from domains that travel
with it. It is also where all the money arithmetic happens.

---

## 1. `api/services/impl/CheckoutServiceImpl`

`@Service`. Dependencies: `OrdersCrudService`, `OrdersProcessService`,
`OrdersPredicateService`, `PaymentService`. Private slf4j `logger`.

Two public operations plus a result-page URL builder.

### 1.1 `requestTransactionStart` — begins a checkout

```java
@Override
public PaymentRedirectionDetailsPojo requestTransactionStart(OrderPojo transaction)
    throws PaymentServiceException, BadInputException {
    String callbackNonce = newCallbackNonce();
    PaymentRedirectionDetailsPojo response =
        paymentIntegrationService.requestNewPaymentPageDetails(transaction, callbackNonce);
    try {
        transaction.setToken(response.getToken());
        ordersProcessService.markAsStarted(transaction, callbackNonce);
        return response;
    } catch (EntityNotFoundException exc) {
        throw new IllegalStateException("The server had a problem requesting the transaction", exc);
    }
}
```

**The ordering is load-bearing and was changed by register item D4.** The nonce must exist
*before* the payment service is asked for a redirection URL, because it is embedded in that
URL — so it is generated here, at the top, and then persisted by `markAsStarted` in the same
transaction as the status transition (`02` §5.2).

**The nonce is never returned to the caller.** It travels only to the payment provider, which
is precisely what makes a callback presenting it evidence of origin.

```java
static String newCallbackNonce() {
    return UUID.randomUUID().toString().replace("-", "");
}
```

32 hexadecimal characters, 122 bits from `UUID.randomUUID()`'s cryptographically strong
source. Not a MAC and not required to be — it only has to be unguessable and single-use, and
it fits the column's 64 characters with room to spare.

**Note the exception translation:** an `EntityNotFoundException` here becomes
`IllegalStateException("The server had a problem requesting the transaction")` — a 500 — on
the reasoning that the order was created moments earlier by the same request, so its
disappearance is a server fault rather than client error.

### 1.2 `confirmTransaction` — the callback handler

Changed in three ways by **D4**. Specified in full in `04` §5; summarised here because it is
the checkout service's method:

```java
@Override
public OrderPojo confirmTransaction(String transactionToken, boolean wasAborted, String callbackNonce)
    throws EntityNotFoundException, PaymentServiceException, BadInputException {
    OrderPojo sellByToken = this.getSellRequestedWithMatchingToken(transactionToken);
    this.assertCallbackNonceMatches(transactionToken, callbackNonce);

    if (ALREADY_RESOLVED_STATUSES.contains(sellByToken.getStatus())) {
        logger.info("Ignoring a repeated payment callback for an order already in '{}'",
            sellByToken.getStatus());
        return sellByToken;
    }

    try {
        if (wasAborted) {
            return ordersProcessService.markAsAborted(sellByToken);
        } else {
            return this.processSellPaymentStatus(sellByToken);
        }
    } catch (BadInputException e) {
        logger.error("Incorrect state of sell, was: {}", sellByToken.getStatus());
        throw new IllegalStateException("Transaction could not be confirmed");
    }
}
```

```java
static final Set<String> ALREADY_RESOLVED_STATUSES = Set.of(
    ORDER_STATUS_PAYMENT_CANCELLED,
    ORDER_STATUS_PAYMENT_FAILED,
    ORDER_STATUS_PAID_UNCONFIRMED);
static final String CALLBACK_NONCE_REJECTED = "The transaction callback could not be authenticated";
```

**The `catch (BadInputException)` → `IllegalStateException` translation is BR-ORDER-039** — a
confirmation in the wrong state becomes an **opaque failure**. The client gets a 500 and the
message `"Transaction could not be confirmed"`; the actual status is logged at ERROR and never
surfaced. Preserved.

### 1.3 `getSellRequestedWithMatchingToken` — and BR-ORDER-041's error

```java
private OrderPojo getSellRequestedWithMatchingToken(String transactionToken)
    throws EntityNotFoundException {
    Map<String, String> tokenMatcher = new HashMap<>(Map.of("token", transactionToken));
    Predicate transactionWithMatchingToken = ordersPredicateService.parseMap(tokenMatcher);
    return ordersCrudService.readOne(transactionWithMatchingToken);
}
```

**This used to pass a second map key, `"statusCode"`, which `OrdersPredicateServiceImpl` does
not recognise.** Its switch accepts only `id`, `buyOrder`, `date`, `statusName` and `token`;
an unknown key hits `default: break` and is **silently ignored**. Therefore:

> **BR-ORDER-041 — "the validation lookup requires the `Payment Started` status" — was never
> true.** The status filter never applied. The lookup was by token alone, and the replay
> protection the register credited to that filter **did not exist**.

**D4 deliberately did not "fix" the key to `statusName`.** Filtering by status in the query
makes an already-processed order **indistinguishable from a non-existent one** — both return
`EntityNotFoundException` — which makes idempotency impossible to implement. The order is now
resolved by token in whatever state it is in, and the status is checked **explicitly in
code**.

---

## 2. `api/controllers/PublicCheckoutController`

```java
@RestController
@RequestMapping("/public/checkout")
@Tag(name = "Checkout")
```

Dependencies: `CheckoutService`, `OrdersCrudService`, `OrdersPredicateService`, and
`@Nullable @Autowired(required = false) MailingService`.

| Verb | Path | `@PreAuthorize` | Returns |
|---|---|---|---|
| POST | `/public/checkout` | **`hasAuthority('checkout')`** | `PaymentRedirectionDetailsPojo` |
| GET | `/public/checkout/validate` | **none — anonymous** | `303 SEE OTHER` |
| POST | `/public/checkout/validate` | **none — anonymous** | `303 SEE OTHER` |

```java
@ResponseStatus(INTERNAL_SERVER_ERROR)
@ExceptionHandler(PaymentServiceException.class)
public String handleException(PaymentServiceException ex) {
    return ex.getMessage();
}
```

### 2.1 `submitCart`

```java
public PaymentRedirectionDetailsPojo submitCart(@Valid @RequestBody OrderPojo transactionRequest)
    throws BadInputException, PaymentServiceException, EntityExistsException {
    OrderPojo createdTransaction = ordersCrudService.create(transactionRequest);
    return service.requestTransactionStart(createdTransaction);
}
```

Two steps, no transaction wrapping them: **the order is created and committed, then the
payment page is requested.** If the payment service fails, the order already exists in
`Pending` — which is correct and intentional, since it is the record that a customer tried.

**BR-ORDER-062** — checkout requires the standalone `checkout` authority, which the seeded
`Customer` role holds (and nothing else).

### 2.2 The two callbacks

**BR-ORDER-040** — the validation callbacks are **public and unauthenticated**.
**BR-ORDER-042** — both redirect rather than respond with data.
**BR-ORDER-043** — the success callback is a `GET` and the abort callback is a `POST`.

```java
@GetMapping("/validate")
public ResponseEntity<Void> validateSuccesfulTransaction(@RequestParam Map<String, String> transactionData)
    throws BadInputException, EntityNotFoundException, PaymentServiceException, MailingServiceException {
    if (!transactionData.containsKey(WEBPAY_SUCCESS_TOKEN_HEADER_NAME)) { // success
        throw new BadInputException("No transaction token was provided");
    }
    String token = transactionData.get(WEBPAY_SUCCESS_TOKEN_HEADER_NAME);
    String callbackNonce = transactionData.get(CHECKOUT_CALLBACK_NONCE_PARAM_NAME);
    service.confirmTransaction(token, false, callbackNonce);
    // Register item D11: the mail send that used to sit here has moved into the
    // transition's own transaction, as an outbox entry - see MailOutboxService.
    URI transactionUri = service.generateResultPageUrl(token);
    return ResponseEntity.status(SEE_OTHER).location(transactionUri).build();
}
```

The abort mapping is identical in shape, reading `WEBPAY_ABORTION_TOKEN_HEADER_NAME` and
passing `wasAborted = true`.

Three constants, all client-observable (G2):

```java
WEBPAY_SUCCESS_TOKEN_HEADER_NAME   = "token_ws"
WEBPAY_ABORTION_TOKEN_HEADER_NAME  = "TBK_TOKEN"
CHECKOUT_CALLBACK_NONCE_PARAM_NAME = "nonce"      // added by D4
```

**The parameter names are Transbank's, not ours** — `token_ws` and `TBK_TOKEN` are dictated by
the provider, including the inconsistent casing. The `nonce` parameter is ours.

**These two mappings stay anonymous by design.** The provider cannot authenticate, so the
nonce *is* the access control — see `04` §5 and `permissions.md`.

---

## 3. `jpa/services/conversion/impl/OrdersConverterServiceImpl` — 329 lines

`@Transactional` + `@Service`. The largest single class in the domain, and the one that does
all boundary resolution.

```java
static final double TAX_PERCENT = 0.19; // TODO refactor into a "tax service" of sorts
static final String UNEXISTING_BILLING_TYPE = "Specified billing type does not exist";
```

Static imports: `Constants.BILLING_TYPE_ENTERPRISE`, `Constants.ORDER_STATUS_PENDING`.

### 3.1 `convertToNewEntity(OrderPojo)` — the creation sequence

```
1. Order.builder().build()
2. if (model.getDate() != null) setDate(...)
3. orderStatusesRepository.findByName(ORDER_STATUS_PENDING).ifPresent(target::setStatus)
4. convertPaymentTypeInformationForEntity
5. convertCustomerInformationForEntity
6. convertBillingInformationForEntity
7. convertShippingInformationForEntity
8. convertDetailsForEntity
9. updateTotals
```

**Step 3 is BR-ORDER-001** — a new order always starts as `Pending`. Note it uses
`ifPresent`, so a **missing `Pending` row leaves the status null** and the insert then fails
on `nullable = false` rather than raising the clear
`"No status matches the..."` message that `OrdersProcessServiceImpl` would give. An
inconsistency between the two paths, preserved.

**Step 2 means a client may supply the order date.** If it does not, `@CreationTimestamp`
sets it on insert.

### 3.2 Step 4 — payment type

```java
billingContract.findPaymentTypeByName(name)
    .ifPresentOrElse(target::setPaymentType,
        () -> { throw new RuntimeException("The payment type does not exist"); });
```
…and when the pojo's payment type is absent:
```java
throw new BadInputException("A payment type has to be included");
```

**BR-ORDER-004.** Note the asymmetry: a *missing* payment type is a `BadInputException`
(400), an *unknown* one is a bare `RuntimeException` (500). Both preserved.

### 3.3 Step 5 — customer, through the Gate A contract

`customerProvisioningContract.resolveOrCreateCustomer(...)` — **BR-ORDER-006**, a customer is
mandatory and is **reused when it exists**. The contract was published in Phase 2 to close
coupling hotspot HS-02; it is a **read *or* a write**, and once Identity is separate this is
a remote write inside checkout. `ADR-001` records it.

### 3.4 Step 6 — billing

```java
if (!BILLING_TYPE_ENTERPRISE.equals(model.getBillingType())) { /* individual */ }
else { /* resolve billingCompany and billingAddress */ }
...
throw new BadInputException(UNEXISTING_BILLING_TYPE);
```

**BR-BILLING-003** via `UNEXISTING_BILLING_TYPE`; **BR-BILLING-007** and **BR-BILLING-008**
for the enterprise branch, which additionally resolves a company and a billing address.
**BR-ORDER-005.**

### 3.5 Step 7 — shipping

```java
throw new BadInputException("Specified shipper does not exist");
```

**BR-ORDER-007** — a shipper is optional but must exist when supplied. **BR-SHIPPER-014** —
an order may have no carrier at all. **BR-ORDER-059** — shipping and billing addresses are
both optional.

**BR-SHIPPER-015 is the asymmetry here:** this path *rejects* an unknown carrier name, while
`OrdersPatchServiceImpl` resolves the same name with `.ifPresent(...)` and **silently
ignores** it.

### 3.6 Step 8 — details, and the product boundary

```java
throw new BadInputException("Product barcode must be valid");
throw new BadInputException("Unexisting product in sell details");
...
description = detail.getUnits() + "x " + product.getName();
unitValue   = product.getPrice();
...
catch (BadInputException exc) { throw new RuntimeException(exc.getMessage(), exc); }
```

| Rule | Behaviour |
|---|---|
| **BR-ORDER-008** | each line's product must resolve **by barcode** |
| **BR-ORDER-009** | the **server** sets `unitValue` from `product.getPrice()` — the client's value is discarded |
| **BR-ORDER-010** | the description is **generated**: `"3x Widget"`. Note the literal `"x "` with its trailing space |
| **BR-ORDER-011** | because both are copied onto the line, order history is **price-stable** — a later reprice or deletion cannot change a past order |
| **BR-ORDER-012** | **no stock check occurs at any point** (W14). Any quantity of anything can be ordered |

**The outer `catch` re-wraps a `BadInputException` as a `RuntimeException`**, converting what
would be a 400 into a **500** for every detail-level failure. So `"Product barcode must be
valid"` and `"Unexisting product in sell details"` reach the client as 500s despite being
client errors. Preserved, and recorded in `errors.md`.

### 3.7 `convertToPojo(Order)` — the read sequence

Builder from `getId` → `buyOrder`, `date`, `netValue`, `taxValue` ← `getTaxesValue`,
`totalValue`, `totalItems`, `transportValue`, `token` ← `getTransactionToken`; then

```
customerProvisioningContract.describeCustomer(source.getCustomer())   -> setCustomer
setStatus(source.getStatus().getName())
setPaymentType(source.getPaymentType().getName())
setBillingType(source.getBillingType().getName())
if billingType == BILLING_TYPE_ENTERPRISE -> convert billingCompany
if getShipper() != null                   -> setShipper(name)
if getSalesperson() != null               -> salespersonLookupContract.describeSalesperson(...)
```

Both `describeCustomer` and `describeSalesperson` are **read-only** contract methods published
in Phase 2. **Neither `billingAddress` nor `shippingAddress` nor `details` is set here** —
`OrdersCrudServiceImpl.readOne` adds them (`07` §3.2), which is **BR-ORDER-069**.

`applyChangesToExistingEntity` throws
`UnsupportedOperationException("This method is deprecated")` — **BR-ORDER-013**.

---

## 4. `updateTotals` — the money arithmetic

```java
for (OrderDetail sd : details) {
    int unitValue = sd.getUnitValue();
    double unitTaxValue = unitValue * TAX_PERCENT;
    double unitNetValue = unitValue - unitTaxValue;
    taxesValue += (unitTaxValue * sd.getUnits());
    netValue   += (unitNetValue * sd.getUnits());
    totalUnits += sd.getUnits();
}
target.setTaxesValue(taxesValue);
target.setNetValue(netValue);
target.setTotalValue(taxesValue + netValue);
target.setTotalItems(totalUnits);
```

| Rule | Behaviour |
|---|---|
| **BR-ORDER-014** | tax is **19% of the unit value**, computed **per line** — `TAX_PERCENT = 0.19`, a literal that ports exactly |
| **BR-ORDER-015** | the accumulators are `int` while `unitTaxValue`/`unitNetValue` are `double`, so each `+=` **truncates**. Rounding is emergent, not specified (W13) |
| **BR-ORDER-016** | **`totalValue = taxesValue + netValue` — transport is excluded** |
| **BR-ORDER-017** | `transportValue` is `nullable = false`, is stored, and **is never set by any code path** — it is neither read from the pojo here nor written anywhere else |
| **BR-ORDER-018** | `totalItems` counts **units**, not lines |
| **BR-ORDER-019** | every stored monetary value is `int`: no decimal places anywhere |

### 4.1 Why W4 matters

Tax is computed as a slice **of** the unit value rather than added **to** it — the price is
tax-inclusive, so `net + tax == unitValue * units` by construction, and the total is
therefore the sum of line prices. That part is internally consistent.

**What is not consistent is transport.** The column exists, is mandatory, is surfaced on
`OrderPojo`, is echoed on `ReceiptPojo` — and is excluded from `totalValue` while being
written by nothing. So:

> A shipping charge **can be recorded** in the schema and in the API, and **will never be
> billed**, because nothing sets it and the total ignores it.

Both halves are preserved (G1). They are recorded together because fixing either one alone
would be worse than fixing neither: setting `transportValue` without changing the total would
show customers a charge that is not in their total, and adding it to the total without a
source would change every historic figure.

---

## 5. Addresses — resolved here, owned by billing

An order carries up to two addresses, and **this is the only code path that ever creates
one**. The rules are numbered in this domain because the behaviour lives here, while the
entity and table belong to billing (`domain-billing/01` §3).

```java
private Address findAddress(@NotNull AddressPojo address) {
    return addressesRepository.findByFields(
        address.getCity(), address.getMunicipality(), address.getFirstLine(),
        address.getSecondLine(), address.getPostalCode(), address.getNotes())
        .orElse(null);
}
```

| Rule | Behaviour |
|---|---|
| **BR-ORDER-056** | an address is identified by **all six of its fields** — there is no shorter natural key, and `UK_ADDR` spans exactly those six columns |
| **BR-ORDER-057** | **addresses are immutable once referenced.** `Order.billingAddress` and `Order.shippingAddress` are both `updatable = false` (`01` §1.3), and no route patches an address, so a referenced row can never change |
| **BR-ORDER-058** | **addresses have no API surface** — no controller, no CRUD service, no patch service, no predicate service, no sort spec. They exist only as a side effect of checkout |

**Note this injects `AddressesRepository` directly**, not through a contract. That is
deliberate and permitted: addresses are billing-owned, and billing travels into the **same**
Orders service, so no seam is required (`domain-billing/00` §3.3).

### 5.1 The duplicate-row consequence

`findByFields` compares three **nullable** columns — `secondLine`, `postalCode`, `notes` —
with `=`, and `= NULL` is never true in SQL. So:

> **An address with a null optional field can never be found**, `findAddress` returns null,
> and checkout **creates a fresh row**. `UK_ADDR` accepts it, because a NULL makes the unique
> tuple distinct too.

The result is silent row growth in `addresses`, one per checkout, for most real addresses.
This is boundary warning **W1 of the billing domain**, specified in
`domain-billing/02` §2.3, and it ports unchanged — correcting the comparison would change
which addresses are reused and which rows accumulate (G1).

**Combined with BR-ORDER-057**, the effect is benign for correctness and poor for storage: a
new row is created rather than an existing one mutated, so no historic order's address can
ever change. The immutability the rule describes is achieved partly by accident.

---

## 6. Rules covered

BR-ORDER-001 – 019, 039, 040, 041 (corrected), 042, 043, 056, 057, 058, 059, 062, 069; plus
BR-BILLING-003, 007, 008 and BR-SHIPPER-014, 015 by reference.

**BR-ORDER-041 is corrected** — see §1.3 and `00` §3.
