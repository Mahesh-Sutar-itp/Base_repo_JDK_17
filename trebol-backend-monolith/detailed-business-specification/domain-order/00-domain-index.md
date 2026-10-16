# Order — Domain Index

Domain slug: `order` · Complexity: **high** · 58 source files · 4,243 LOC · 4 owned tables ·
4 entities · **14 HTTP endpoints** · 70 business rules

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "order"`.

Produced for **Phase 3 (Orders extraction)**. The definition requires this domain's spec to be
**partitioned by subsystem**, because it is the largest in the system and spans seven
genuinely different concerns. It is, accordingly, eight behavioural documents rather than the
usual two.

---

## 1. Domain summary

**Purpose.** The transactional core: an order, its lines, its status lifecycle, the checkout
that creates it, the payment provider that charges for it, the notifications it emits and the
receipt a customer reads afterwards.

**It is the only domain that writes money-shaped data**, and the only one with an outbound
call to a third party. It is also the last domain to be extracted, which is deliberate:
everything it depends on — catalogue, identity, billing, shipper — is either already
extracted or travels with it.

**Core invariants, as implemented.** A new order always starts as `Pending` (BR-ORDER-001);
the **server** sets the unit value and the line description, never the client (BR-ORDER-009,
BR-ORDER-010), which is what makes order history price-stable (BR-ORDER-011); tax is 19% of
the unit value computed per line (BR-ORDER-014); and every status transition validates the
exact current status (BR-ORDER-022).

**Key entities.** `Order` → `orders`; `OrderDetail` → `order_details`; `OrderStatus` →
`order_statuses`; plus the `order_mail_outbox` table added by register item **D11** in this
phase.

---

## 2. What Stage 3A changed inside this domain

Phase 3 remediated six register items in the monolith **before** any extraction, as invariant
G6 requires. Every one of them lives in this domain, so this spec describes the **post-fix**
behaviour and says so wherever it differs from the recorded rule.

| Item | Change | Document |
|---|---|---|
| **D18** | `WebpayTransactionFactory` seam makes the payment adapter testable without a network | `04` §2 |
| **D3** | A payment-infrastructure failure is no longer recorded as a customer decline | `04` §4 |
| **D4** | Payment callbacks are **authenticated** by a per-order nonce and are **idempotent** | `03` §5, `04` §5 |
| **D5** | A `PATCH` can no longer silently accept a status it cannot persist | `07` §4 |
| **D10** | The four unreachable statuses are rejected as transition targets | `02` §4 |
| **D11** | Mail is behind a **transactional outbox** | `05` |

The monolith went from 414 tests with three permanent known failures to **460 tests with
zero**. Exit criterion **X9** closed.

---

## 3. Six business rules are now stale — specification corrections

Writing this spec against the code found **six** `BR-ORDER-*` rules that **no longer describe
the implementation**. All six are recorded here rather than silently written around.

| Rule | States | Actually |
|---|---|---|
| **BR-ORDER-028** | "The order status **IS** patchable despite the mapping" | **Never true.** `Order.status` is `updatable = false`, so Hibernate excluded `order_status_id` from every `UPDATE` and a patched status was **silently discarded** — proven empirically by `OrderStatusPatchPersistenceProbeTest`. Since **D5**, a status patch is rejected outright |
| **BR-ORDER-033** | "The order patch service reports a **truncated** error message" | Fixed by **D5**. The literal `"The "` is now `"A patched field was not textual where text was expected"` |
| **BR-ORDER-034** | "The payment session identifier is derived from an **object hash**" | Fixed by **D9**, before this phase. `newSessionId()` returns a separator-free `UUID`; the javadoc records the former `transaction.hashCode()` |
| **BR-ORDER-036** | "A payment-commit failure is **silently treated as a declined payment**" | Fixed by **D3**. A `TransactionCommitException` now raises `PaymentServiceException`; the order keeps `Payment Started` rather than moving to `Payment Failed` |
| **BR-ORDER-041** | "The validation lookup **requires** the `Payment Started` status" | **Never true.** The lookup passed a map key `"statusCode"` that `OrdersPredicateServiceImpl` does not recognise, so the status filter was **silently dropped**. Since **D4**, the status is checked **explicitly in code** |
| **BR-ORDER-055** | "Notification is **not transactional** with the transition" | Fixed by **D11**. The outbox entry is written **inside** the transition's transaction |

**Two of the six — 028 and 041 — were never true of any version of the code.** They describe
intent that the mapping and the predicate switch silently defeated. That is the more
important category: a rule that was wrong from the start is not caught by re-reading the
rule, only by probing the behaviour.

**Action required of the owner:** amend all six in the source rule document. No code change
follows from this; the code is correct and the rules are behind it.

---

## 4. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **`OrdersSortSpec`'s `"customer"` key traverses `QOrder.order.customer.person` via `QPerson`** (`OrdersSortSpec.java:35`, `:40`) — a sort that reaches **into the Identity service's `people` table**. It cannot survive the extraction. **Ruling: drop the key**, per the Phase 3 decision. | **High** | `07` §2.2 |
| W2 | **`ReceiptServiceImpl` imports `org.trebol.jpa.entities.Product` and dereferences it directly** (`:30`, `:71`, `:72`) — reading `name` and `barcode` off a catalogue entity. **Gate A's grep did not catch it** because it imports an *entity*, not a repository. **This is a real cross-service read that must be cut.** | **High** | `06` §3 |
| W3 | **`OrdersCrudServiceImpl.readOne` converts `found.getBillingAddress()` unconditionally** while guarding `getShippingAddress()` with a null check — an **NPE** on any order with no billing address. | **High** | `07` §3.2 |
| W4 | **The total excludes transport** (BR-ORDER-016). `setTotalValue(taxesValue + netValue)`; `transportValue` is mandatory, stored, and **never set by any code path** (BR-ORDER-017). A shipping charge can be recorded and will not be billed. | **High** | `03` §4 |
| W5 | **`Order`'s copy constructor silently drops `callbackNonce`** — it copies 11 fields and nulls 7 associations, and the nonce added by D4 is in neither list. Every patch therefore produces a copy with no nonce. Harmless today because `callbackNonce` is `updatable`-by-default and the patch path never writes it, but a trap for anyone extending the copy constructor. | Medium | `01` §1.3 |
| W6 | **`OrderDetail.product` is `@ManyToOne(optional = false)` with no `fetch` — so it defaults to `EAGER`**, and its foreign key is `ConstraintMode.NO_CONSTRAINT`. Eager-loading a catalogue entity per line is exactly what the extraction must replace. | **High** | `01` §2 |
| W7 | **`order_details`'s parent FK references a table named `sales`** (`FK_ORDER_DETAIL_PARENT_ID`) — a legacy name still in the committed DDL, while the entity maps `orders`. | Medium | `01` §4 |
| W8 | **Deleting an order does not delete its lines** (BR-ORDER-067). `details` cascades `PERSIST` only, with no `orphanRemoval`, and the child FK is `NO_CONSTRAINT`, so `order_details` rows are orphaned silently. | Medium | `01` §2.2 |
| W9 | **`OrderStatusPojo` declares `@NotBlank` on an `Integer`** — an invalid constraint target that would fail validation bootstrapping if the pojo were ever bound to a request body. It is not, which is why it has never been noticed. | Low | `01` §5.6 |
| W10 | **`ReceiptPojo` has no `@Builder` and no constructors**, and `ReceiptServiceImpl` can **return `null`** if the `ConversionService` yields null — unguarded. | Medium | `06` §2 |
| W11 | **The two receipt converters each leave fields unset** — `OrderEntity2ReceiptPojo` sets neither `status` nor `details`; `OrderDetailEntity2ReceiptDetailPojo` does not set `product`. The service fills them in afterwards, so the converters are not independently correct. | Low | `06` §4 |
| W12 | **`POST /data/orders/rejection` and `/completion` carry no `@ResponseStatus`**, so they return **200** while `/confirmation` returns **204**. Three sibling routes, two status codes. | Low | `07` §5 |
| W13 | **Monetary values are `int`** with no decimal places (BR-ORDER-019), and per-line tax is accumulated **truncated** (BR-ORDER-015). Rounding behaviour is emergent, not specified. | Medium | `03` §4 |
| W14 | **No stock check occurs at any point** (BR-ORDER-012). An order can be placed for any quantity of anything. | Medium | `03` §3 |

No warning is fixed during the port (G1), **except** W1 and W2, which are the two the
extraction *must* resolve because they cross a service boundary.

---

## 5. The three cuts Phase 3 must make

Everything else in this domain travels intact. These three do not.

| # | Coupling | Shape today | Resolution |
|---|---|---|---|
| **C1** | `OrderDetail.product` → `Product` | `@ManyToOne(optional = false)`, EAGER, `NO_CONSTRAINT` FK to `products` | the FK is already constraint-free, so the column survives as a plain identifier; the **association** is replaced by a catalogue lookup at write time |
| **C2** | `ReceiptServiceImpl` → `Product` entity | direct import and dereference of `name` + `barcode` (W2) | replaced by a published catalogue contract — the only two fields needed |
| **C3** | `OrdersSortSpec."customer"` → `QPerson.lastName` | QueryDSL path into `people` (W1) | **the key is dropped.** Sorting orders by customer surname cannot be done without a join across services, and no alternative preserves the semantics |

`Order.customer` and `Order.salesperson` are **already** mediated by the Phase-0 and Phase-2
contracts (`CustomerProvisioningContract`, `SalespersonLookupContract`), so they are not cuts
— they are calls that become remote.

---

## 6. Spec inventory — partitioned by subsystem

| File | Subsystem |
|---|---|
| `00-domain-index.md` | this document — coverage, the stale rules, boundary warnings, the three cuts |
| `01-order-entity-and-transport.md` | the four entities, the committed DDL, the seven transport models |
| `02-order-status-machine.md` | the twelve statuses, the seven transitions, the transition table, D10 |
| `03-order-checkout.md` | creation, conversion, totals and tax, the checkout service |
| `04-order-payment-adapter.md` | the Webpay Plus adapter, D18's seam, D3, D4's nonce |
| `05-order-mail-and-outbox.md` | the mailing contract, the four/three status coverage, D11's outbox |
| `06-order-receipt.md` | the public token-addressed receipt, the two converters, W2 |
| `07-order-search-and-patch.md` | predicates, the sort spec, CRUD, patch, the controllers |
| `errors.md` | every error condition with its literal message |
| `permissions.md` | the four order authorities, `checkout`, and the two anonymous routes |

---

## 7. Rule traceability

All 70 `BR-ORDER-*` rules (001–070, contiguous) are referenced by id across these ten
documents, including the six corrected in §3 — which are referenced **with** their
correction, not quietly omitted.

---

## 8. Test inventory (G8)

**149 `@Test` methods** across the order, billing, shipper, payment and receipt set. The
order-specific ones:

| Tests | Class |
|---|---|
| 25 | `OrdersProcessServiceImplTest` |
| 18 | `OrdersConverterServiceImplTest` |
| 15 | `OrderStatusTransitionsTest` — **new in D10**, 144-pair cross-product |
| 9 | `WebpayplusPaymentServiceImplTest` — **rewritten in D18**, no network |
| 8 | `OrdersPatchServiceImplTest` — **two assertions inverted by D5** |
| 6 | `DataOrdersControllerTest` |
| 5 | `OrderStatusesPatchServiceImplTest` |
| 3 | `OrdersCrudServiceImplTest`, `ReceiptServiceImplTest`, `OrderStatusPatchPersistenceProbeTest` (**new in D5**), `WebpayplusPaymentSessionIdTest`, `OrderDomainCouplingArchitectureTest` |
| 2 | `OrdersPredicateServiceImplTest`, `PublicReceiptControllerTest`, `OrderEntity2ReceiptPojoTest`, `PaymentServiceExceptionTest`, `OrderStatusesConverterServiceImplTest` |
| 1 | `DataOrderStatusesControllerTest`, `OrderStatusesCrudServiceImplTest`, `OrderStatusesPredicateServiceImplTest`, `OrderDetailEntity2ReceiptDetailPojoTest` |
| 12 | `MailOutboxTest` — **new in D11** |
| 0 | `OrdersTestHelper` — fixture |

`OrderDomainCouplingArchitectureTest` is the Gate A pin and must be **extended** in step 3.8
to cover C2, which it does not currently catch (W2).

---

## 9. Phase 3 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 58 files with **identical fully-qualified names** |
| Literals | Every message in `errors.md`, the 4 predicate keys, the **8** surviving sort keys, the 12 status names including both comma-bearing ones, and `TAX_PERCENT = 0.19` (G2) |
| Tables | `orders`, `order_details`, `order_statuses`, `order_mail_outbox` with DDL, indexes, engine, charset and collation unchanged — including the legacy `sales` reference in `FK_ORDER_DETAIL_PARENT_ID` (W7) and the absent FK on `order_mail_outbox` |
| Seed data | **12** order statuses, not eleven, with the identity restart at 13 |
| Foreign keys to cut | `FK_ORDER_DETAIL_PROD_ID` → `products` (already `ON DELETE SET NULL` and `NO_CONSTRAINT` at the ORM level); `FK_ORDER_CUSTOM_ID` → `customers` and `FK_ORDER_SPERSON` → `salespeople` **remain**, because customers and salespeople moved to Identity in Phase 2 — these are the three outward keys the plan identified |
| Sort key dropped | `"customer"` removed from `ORDER_SPEC_MAP`, leaving **8** keys. `OrdersSortSpecTest` must assert the reduced map so the removal is pinned |
| Tests | all 149, none deleted or disabled (G3), plus the new `ShipperLookupContractImplTest` |
| Behaviour | no warning in §4 is fixed beyond W1 and W2 (G1) |
