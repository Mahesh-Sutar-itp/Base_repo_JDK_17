# Order — The Receipt Subsystem

Porting-depth behavioural specification. Subsystem 6 of 7. Paths relative to
`src/main/java/org/trebol/`.

Small — four files — but it contains **boundary warning W2**, one of only two couplings the
extraction *must* cut, and the one that **Gate A's verification grep did not catch**.

---

## 1. `api/services/ReceiptService`

```java
/**
 * Provides a mean for users to review past orders.
 */
public interface ReceiptService {
    /**
     * @throws EntityNotFoundException When no transaction matches the input token
     */
    ReceiptPojo fetchReceiptByTransactionToken(String token) throws EntityNotFoundException;
}
```

One method. A receipt is addressed **by transaction token**, never by order id — which is what
makes the public route safe enough to be anonymous (`permissions.md`).

---

## 2. `api/services/impl/ReceiptServiceImpl` — 84 lines

`@Service`. Dependencies: **`OrdersRepository`** and Spring's
`org.springframework.core.convert.ConversionService`.

```
1. Optional<Order> match = ordersRepository.findByTransactionToken(token);
2. if (match.isEmpty())
       throw new EntityNotFoundException("The transaction could not be found, no receipt can be created");
3. Order foundMatch = match.get();
4. ReceiptPojo target = conversionService.convert(foundMatch, ReceiptPojo.class);
5. if (target != null) {
       for (OrderDetail d : foundMatch.getDetails()) {
           ReceiptDetailPojo targetDetail = conversionService.convert(d, ReceiptDetailPojo.class);
           if (targetDetail != null) {
               Product pd = d.getProduct();                                   // <-- W2
               ProductPojo targetDetailProduct = ProductPojo.builder()
                   .name(pd.getName()).barcode(pd.getBarcode()).build();      // <-- W2
               targetDetail.setProduct(targetDetailProduct);
               targetDetail.setUnitValue(d.getUnitValue());
               targetDetails.add(targetDetail);
           }
       }
   }
6. target.setDetails(targetDetails);
   target.setStatus(foundMatch.getStatus().getName());
7. return target;
```

| Rule | Behaviour |
|---|---|
| **BR-ORDER-044** | the receipt is public and **token-addressed** |
| **BR-ORDER-045** | **receipt lookup by token is not status-restricted** — `findByTransactionToken` has no status filter, so a receipt is readable at *any* stage, including `Payment Started` and `Payment Failed`, not only once complete or rejected as the route's `@Operation` summary claims |
| **BR-ORDER-047** | the receipt exposes **only the product name and barcode** per line |
| **BR-ORDER-048** | the status is attached **separately from the conversion** — step 6, because the converter does not set it (§4) |

### 2.1 Two fragilities

**`target` can be `null` and is returned unguarded** — boundary warning **W10**. If the
`ConversionService` yields null at step 4, step 5's guard is skipped but **step 6 still
dereferences `target`**, so it is actually an NPE rather than a null return. Either way it is
a 500. `ReceiptPojo` has **no `@Builder` and no constructors**, so the conversion is the only
way one is produced.

**`setUnitValue` is re-set after conversion** at step 5, even though
`OrderDetailEntity2ReceiptDetailPojo` already sets it. Harmless duplication, preserved — it
is the kind of redundancy that reads like a bug fix whose cause was removed.

---

## 3. Boundary warning W2 — the coupling Gate A missed

> **`ReceiptServiceImpl` imports `org.trebol.jpa.entities.Product` (line 30) and dereferences
> it directly** — `Product pd = d.getProduct();` (line 71), then `pd.getName()` and
> `pd.getBarcode()` (line 72).

**There is no `ProductLookupContract` reference anywhere in the file**, and no
`ProductsConverterService`. The file also imports `org.trebol.jpa.entities.Order` (31) and
`OrderDetail` (32), which is fine — those are its own.

**Why Gate A did not catch it.** Gate A's verification is a grep for
`import org.trebol.jpa.repositories` within `api/services/`. This file imports an **entity**,
not a repository, so it passed cleanly. The same blind spot hid coupling hotspot **HS-02**,
which was a *service-level* leak closed in Phase 2 — this is the **entity-level** variant.

**That is the general lesson, and it is worth stating:** a grep that names one layer proves
something about that layer only. Gate A's wording is correct about repositories and silent
about entities and services, and two real couplings lived in those silences.

### 3.1 Why it is tractable

The receipt needs **two fields** — `name` and `barcode` — not a product, not a price, not an
image. So cut **C2** is a published read contract with one narrow method, not a general
catalogue client.

The eager `OrderDetail.product` association (cut **C1**, `01` §2.1) is what makes
`d.getProduct()` work at all today. Once that association becomes an identifier, this code
cannot compile — which is the desired outcome: **the extraction breaks it loudly rather than
leaving it working by accident**.

### 3.2 The architecture test must be extended

`OrderDomainCouplingArchitectureTest` (3 tests) is the Gate A pin. **It does not currently
catch W2**, for the same reason the grep did not. Step 3.8 extends it to assert that no class
under `api/services/` or `common/converters/` imports a catalogue **entity**.

Without that, nothing prevents the coupling being reintroduced after the port.

---

## 4. The two converters — neither is independently complete

### 4.1 `common/converters/OrderEntity2ReceiptPojo`

```java
@Component
public class OrderEntity2ReceiptPojo implements Converter<Order, ReceiptPojo> {
    public ReceiptPojo convert(Order source) {
        ReceiptPojo target = new ReceiptPojo();
        target.setBuyOrder(source.getId());
        target.setDate(source.getDate());
        target.setTransportValue(source.getTransportValue());
        target.setTaxValue(source.getTaxesValue());
        target.setTotalItems(source.getTotalItems());
        target.setTotalValue(source.getTotalValue());
        target.setToken(source.getTransactionToken());
        return target;
    }
}
```

**Sets neither `status` nor `details`.** No null check on `source`. Note it **does** copy
`transportValue` — so the receipt shows a transport figure that is never set and is excluded
from the total (`03` §4, W4).

### 4.2 `common/converters/OrderDetailEntity2ReceiptDetailPojo`

```java
@Component
public class OrderDetailEntity2ReceiptDetailPojo implements Converter<OrderDetail, ReceiptDetailPojo> {
    public ReceiptDetailPojo convert(OrderDetail source) {
        ReceiptDetailPojo target = new ReceiptDetailPojo();
        target.setUnits(source.getUnits());
        target.setUnitValue(source.getUnitValue());
        target.setDescription(source.getDescription());
        return target;
    }
}
```

**Does not set `product`.**

**Boundary warning W11:** both converters are registered as Spring `Converter` beans and are
therefore callable by anything through the `ConversionService` — but **neither produces a
complete object**. `ReceiptServiceImpl` fills in `status`, `details` and `product`
afterwards. Any other caller would get a partially populated pojo with no indication that it
is incomplete.

Preserved (G1). It is recorded because the natural instinct during a port is to "complete"
the converters, which would move the `Product` dereference from the service into a
`common/converters` class — making cut C2 harder, not easier.

---

## 5. `api/controllers/PublicReceiptController` — 67 lines

```java
@RestController
@RequestMapping("/public/receipt")
@Tag(name = "Checkout")
public class PublicReceiptController {
```

**No class-level `@PreAuthorize` and no method-level `@PreAuthorize`.**

```java
@GetMapping("/{token}")
@Operation(summary = "View a summary of an order once complete or rejected")
public ReceiptPojo fetchReceiptById(@PathVariable("token") String token)
    throws BadInputException, EntityNotFoundException {
    if (StringUtils.isBlank(token)) {
        throw new BadInputException("An incorrect receipt token was provided");
    }
    return this.receiptService.fetchReceiptByTransactionToken(token);
}
```

No `@ResponseStatus`, so a success is 200.

**BR-ORDER-046** — a blank or unknown token is rejected **distinctly**:

| Input | Exception | Message | HTTP |
|---|---|---|---|
| blank token | `BadInputException` | `An incorrect receipt token was provided` | **400** |
| unknown token | `EntityNotFoundException` | `The transaction could not be found, no receipt can be created` | **404** |

Two different messages for two different failures — unusually careful for this codebase, and
worth preserving precisely.

### 5.1 The `@Operation` summary is inaccurate

It says *"once complete or rejected"*. **BR-ORDER-045** says otherwise, and the code agrees
with the rule: there is **no status restriction** on the lookup. A receipt is readable the
moment a token exists, which is as soon as `markAsStarted` runs.

The summary is OpenAPI-visible documentation, so it ports verbatim (G2) — but it is recorded
here as inaccurate, because it is the kind of text someone will later cite as the intended
behaviour.

### 5.2 The security shape

The token is a **64-character** value (`@Size(min = 64, max = 64)` on
`Order.transactionToken`, `01` §1.2), generated by the payment provider. It is the only
credential the receipt route needs — knowing it is sufficient, and it is unguessable.

**`ReceiptPojo` echoes the token back** (`01` §5.3) — unlike `OrderPojo`, where `token` is
`@JsonIgnore`. That is not a leak, since the caller supplied it in the path to get here, but
it is an inconsistency between the two models worth knowing.

The genuine consequence: **a receipt is readable by anyone who obtains the token**, including
after the order completes, forever, with no authentication. Given the token reaches the
customer's browser via a redirect and appears in the provider's referrer chain, that is a
real if narrow exposure. **Preserved** — it is BR-ORDER-044's intent, not an oversight.

---

## 6. Tests (G8)

| Tests | Class |
|---|---|
| 3 | `ReceiptServiceImplTest` |
| 2 | `PublicReceiptControllerTest` |
| 2 | `OrderEntity2ReceiptPojoTest` |
| 1 | `OrderDetailEntity2ReceiptDetailPojoTest` |

**Eight tests for the whole subsystem**, which is thin for code containing W2, W10 and W11.
`ReceiptServiceImplTest`'s 3 tests do not cover the null-`target` path (W10).

Step 3.8 does **not** add coverage for W10 — adding tests for a path that is preserved
unchanged is still scope growth, and the gap is recorded instead. What step 3.8 **does** add
is the architecture assertion for W2 (§3.2), because that one prevents a regression rather
than documenting a quirk.

---

## 7. Rules covered

BR-ORDER-044, 045, 046, 047, 048.

All five are **accurate** against current code — the only subsystem in this domain where
that is true, since the receipt path was not touched by any Phase 3 register item.
