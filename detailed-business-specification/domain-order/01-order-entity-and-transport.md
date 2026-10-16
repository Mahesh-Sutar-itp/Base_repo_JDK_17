# Order — Entities and Transport Models

Porting-depth behavioural specification. Subsystem 1 of 7. Paths relative to
`src/main/java/org/trebol/`.

---

## 1. `jpa/entities/Order.java` — `serialVersionUID = 14L`

### 1.1 Class level

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(columnList = "order_date"),
    @Index(columnList = "order_transaction_token"),
})
@Builder @NoArgsConstructor @AllArgsConstructor @Getter @Setter @EqualsAndHashCode @ToString
public class Order implements DBEntity {
```

**No `@UniqueConstraint`. No lifecycle callbacks.** Two indexes, matching the two things
orders are looked up by: date (the default sort) and transaction token (every payment
callback and every receipt).

### 1.2 Columns

| Field | Annotations | Note |
|---|---|---|
| `Long id` | `@Id @GeneratedValue(IDENTITY) @Column(name = "order_id", nullable = false)` | surfaces as `buyOrder` |
| `Instant date` | `@Column(name = "order_date", nullable = false)` + **`@CreationTimestamp`** | Hibernate sets it on insert; the converter only sets it if the client supplied one |
| `int totalItems` | `@Column(name = "order_total_items", nullable = false)` | **units, not lines** (BR-ORDER-018) |
| `int netValue` | `@Column(name = "order_net_value", nullable = false)` | |
| `int transportValue` | `@Column(name = "order_transport_value", nullable = false)` | mandatory, stored, **never set by any code path** (BR-ORDER-017) |
| `int taxesValue` | `@Column(name = "order_taxes_value", nullable = false)` | surfaces as `taxValue` |
| `int totalValue` | `@Column(name = "order_total_value", nullable = false)` | `taxesValue + netValue` — **excludes transport** (BR-ORDER-016) |
| `String transactionToken` | **`@Size(min = 64, max = 64)`** + `@Column(name = "order_transaction_token")` | exactly 64 characters, nullable |
| `String callbackNonce` | `@Column(name = "order_callback_nonce")` | **added by register item D4** — nullable, additive |

**All five monetary fields are `int`** — no decimal places anywhere (BR-ORDER-019), which is
what makes the truncating tax accumulation in `03` §4 observable rather than theoretical.

### 1.3 Relationships — ten of them, and which are frozen

| Field | `@JoinColumn` | Mapping | `updatable=false`? |
|---|---|---|---|
| `customer` | `customer_id` | `@ManyToOne(optional = false, cascade = {PERSIST, REFRESH}, fetch = LAZY)` | no |
| `paymentType` | `payment_type_id`, `nullable = false` | `@ManyToOne(optional = false, cascade = REFRESH, fetch = LAZY)` | **YES** |
| `status` | `order_status_id`, `nullable = false` | `@ManyToOne(optional = false, cascade = REFRESH, fetch = LAZY)` | **YES** |
| `billingType` | `billing_type_id`, `nullable = false` | `@ManyToOne(optional = false, cascade = REFRESH, fetch = LAZY)` | **YES** |
| `billingCompany` | `billing_company_id` | `@ManyToOne(cascade = {PERSIST, REFRESH, MERGE}, fetch = LAZY)` | no |
| `billingAddress` | `billing_address_id` | `@ManyToOne(cascade = {PERSIST, REFRESH, MERGE}, fetch = LAZY)` | **YES** |
| `shipper` | `shipper_id` | `@ManyToOne(cascade = REFRESH, fetch = LAZY)` | **YES** |
| `shippingAddress` | `shipping_address_id` | `@ManyToOne(cascade = {PERSIST, REFRESH, MERGE}, fetch = LAZY)` | **YES** |
| `salesperson` | `salesperson_id` | `@ManyToOne(cascade = {PERSIST, REFRESH, MERGE}, fetch = LAZY)` | no |
| `details` | `order_id`, `nullable = false` | `@OneToMany(cascade = PERSIST, fetch = LAZY)` | no |

**No `@ForeignKey`/`ConstraintMode` on `Order`, no `orphanRemoval`, no `mappedBy`.** All ten
are `LAZY`.

**Six associations are `updatable = false`.** That single fact drives three register items:

- **D5** — a patched `status` is excluded from every generated `UPDATE` and was therefore
  **silently discarded**, proven by `OrderStatusPatchPersistenceProbeTest`. The same is true
  of `paymentType` and `shipper`, recorded as **D27**.
- **BR-ORDER-027** — the status association is not updatable through the ORM, which is why
  **BR-ORDER-026** exists: transitions are applied by a direct `@Modifying` update
  (`OrdersRepository.setStatus`), not by saving the aggregate.
- **BR-BILLING-004** — the billing type on an order is immutable after creation.

**`PERSIST` cascades are how checkout creates rows it never explicitly saves**: a new
customer, a new billing company, up to two new addresses, and every order line, all inserted
when the order is saved. `billingCompany`, `billingAddress`, `shippingAddress` and
`salesperson` additionally cascade `MERGE`.

### 1.4 Copy constructor — and the field it drops

```java
public Order(Order source) {
    this.id = source.id;
    this.date = Instant.from(source.date);
    // totalItems, netValue, transportValue, taxesValue, totalValue, transactionToken
    this.paymentType = source.paymentType;
    this.status = source.status;
    this.billingType = source.billingType;
    this.billingAddress = null;
    this.customer = null;
    this.details = null;
    this.billingCompany = null;
    this.shipper = null;
    this.shippingAddress = null;
    this.salesperson = null;
}
```

Its javadoc says: *"Please note that this copy-constructor only preserves the following
relationships"* — `OrderStatus`, `PaymentType`, `BillingType`.

| Fact | Consequence |
|---|---|
| **`id` is copied** | a patched copy carries the same primary key, so `saveAndFlush` is an update |
| `date` is **re-wrapped** via `Instant.from(...)` | not shared by reference, unlike every other field |
| **Seven associations are explicitly nulled** | a patched `Order` has no customer, no details and no addresses. Only the three frozen associations survive |
| **`callbackNonce` is in neither list** | **boundary warning W5** — it is silently dropped by every copy |

W5 is harmless today: the patch path never writes the nonce, and `callbackNonce` is
`updatable` so a null would in principle be written — except `flushPartialChanges` only saves
when the copy differs from the original, and dropping the nonce *does* make them differ. The
reason no live nonce is destroyed is narrower than it looks: **an order in `Payment Started`
has status code 2, and `OrdersCrudServiceImpl.flushPartialChanges` blocks patches only at
code ≥ 3 or < 0** — so a patch *can* reach an order mid-payment. The nonce column is not
nulled only because the nonce is read by `findCallbackNonce` straight from the database
rather than through a copy. **This is the single most fragile interaction in the entity**, and
it is recorded so that anyone extending the copy constructor adds the nonce rather than
discovering this.

---

## 2. `jpa/entities/OrderDetail.java` — `serialVersionUID = 15L`

```java
@Entity
@Table(name = "order_details")
```

| Field | Annotations |
|---|---|
| `Long id` | `@Id @GeneratedValue(IDENTITY) @Column(name = "order_detail_id", nullable = false)` |
| `int units` | `@Column(name = "order_detail_units", nullable = false)` |
| `Integer unitValue` | `@Column(name = "order_detail_unit_value", nullable = false)` — boxed, unlike `units` |
| `String description` | `@Size(max = 260)` + `@Column(name = "order_detail_description", nullable = false)` |

### 2.1 The two relationships — both constraint-free

```java
@JoinColumn(name = "product_id", referencedColumnName = "product_id",
    updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
@ManyToOne(optional = false)
private Product product;

@JoinColumn(name = "order_id", referencedColumnName = "order_id",
    insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
@ManyToOne(optional = false, fetch = FetchType.LAZY)
private Order order;
```

| Fact | Consequence |
|---|---|
| **`product` specifies no `fetch`, so it defaults to `EAGER`** | **boundary warning W6** — every order line eagerly loads a catalogue entity. This is cut **C1**: the association becomes a lookup, the column survives as a plain identifier |
| **Both FKs are `ConstraintMode.NO_CONSTRAINT`** | Hibernate generates no constraint. That is *fortunate* for the extraction — the product reference is already constraint-free at the ORM level — but the committed DDL declares both anyway (§4) |
| **`order` is `insertable = false` AND `updatable = false`** | **BR-ORDER-068** — lines reference their order through a read-only column. The parent side owns the FK via `Order.details`' `@JoinColumn`, which is why a detail cannot be re-parented |
| `product` is `optional = false` | a line must have a product… but see W8 |

### 2.2 Deleting an order does not delete its lines

**BR-ORDER-067**, and boundary warning **W8**. `Order.details` cascades **`PERSIST` only** —
no `REMOVE`, no `orphanRemoval` — and the child FK is `NO_CONSTRAINT`, so nothing at either
layer deletes or blocks. `order_details` rows are **orphaned silently**.

Combined with the DDL's `ON DELETE SET NULL` on the product FK (§4), the table accumulates
rows that reference a deleted order and may have a null product. Preserved (G1).

### 2.3 Copy constructor

Copies `id`, `units`, `unitValue`, `description`; sets **`product = null; order = null;`**.
Javadoc: *"Please note: this copy-constructor does not include a OrderDetail's
relationships"*. Since a detail's whole purpose is its product reference, a copied detail is
not independently usable — which is fine, because nothing patches a detail.

---

## 3. `jpa/entities/OrderStatus.java` — `serialVersionUID = 16L`

```java
@Entity
@Table(name = "order_statuses")
...
@Column(name = "order_status_code", nullable = false, unique = true)
private Integer code;
@Size(min = 1, max = 100)
@Column(name = "order_status_name", nullable = false, unique = true)
private String name;
```

**Both `code` and `name` are independently unique.** No relationships, no indexes beyond the
unique keys, no callbacks. Copy constructor copies `id`, `code`, `name`.

**The code is numeric and ordered, the name is what the application compares.**
**BR-ORDER-020** — statuses are compared **by name, not by code**, throughout
`OrdersProcessServiceImpl`; there is even a `// TODO to compare statuses use numbers, not
strings` in the source. The *codes* are used for exactly one thing:
`OrdersCrudServiceImpl.flushPartialChanges`'s `statusCode >= 3 || statusCode < 0` lock
(`07` §3.3).

That split is why `02` is a document of its own: the status machine is name-driven, the
processed-order lock is code-driven, and the two are not kept in step by anything.

**`PaymentType`** (`serialVersionUID = 7L`) has the same shape — `@Size(min = 1, max = 100)`,
`unique = true` name, no relationships — and is **physically owned by the billing domain**
(see `domain-billing/01` §4). **BR-ORDER-060** records that payment types have no API surface.

---

## 4. Committed DDL — `src/main/resources/schema-mariadb.sql`

### 4.1 `orders` (line 284) and its nine foreign keys

```sql
`order_transaction_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
-- Register item D4: per-order payment-callback nonce. Additive and nullable; no existing
-- column is touched. Generated at Payment Started, embedded in the returnUrl handed to
-- Webpay, and required on the callback.
`order_callback_nonce` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
...
KEY `IDX_ORDER_TOKEN` (`order_transaction_token`),
```

| FK | → | Crosses a boundary? |
|---|---|---|
| `FK_ORDER_BILL_TYP_ID` | `billing_types` | no — travels with Orders |
| `FK_ORDER_PAYMT_TYP_ID` | `payment_types` | no |
| `FK_ORDER_BILL_CY_ID` | `billing_companies` | no |
| `FK_ORDER_ADDR_BILL` | `addresses` | no |
| `FK_ORDER_ADDR_SHIP` | `addresses` | no |
| `FK_ORDER_SHIPPER_ID` | `shippers` | no |
| `FK_ORDER_STATUS_ID` | `order_statuses` | no |
| **`FK_ORDER_CUSTOM_ID`** | **`customers`** | **YES — Identity, since Phase 2** |
| **`FK_ORDER_SPERSON`** | **`salespeople`** | **YES — Identity, since Phase 2** |

### 4.2 `order_details` (line 322)

```sql
CONSTRAINT `FK_ORDER_DETAIL_PROD_ID` FOREIGN KEY (`product_id`)
    REFERENCES `products` (`product_id`) ON DELETE SET NULL,
CONSTRAINT `FK_ORDER_DETAIL_PARENT_ID` FOREIGN KEY (`order_id`)
    REFERENCES `sales` (`order_id`),
```

Two things to carry across exactly:

1. **`FK_ORDER_DETAIL_PROD_ID` → `products` is the third boundary-crossing key**, and it is
   already **`ON DELETE SET NULL`** — so deleting a product nulls the reference on historic
   lines rather than blocking. That is what makes **BR-ORDER-011** (price-stable history)
   work: the line keeps its own `unit_value` and `description`, so the order total is
   unaffected by a product being deleted *or* repriced.
2. **`FK_ORDER_DETAIL_PARENT_ID` references a table named `sales`** — boundary warning
   **W7**. The entity maps `orders`; the DDL says `sales`, a legacy name. Port verbatim; the
   schema is contract and renaming it is a migration, not a port.

### 4.3 `order_statuses` (line 169) and `order_mail_outbox` (line 155)

`order_statuses` seeds **twelve** rows and restarts the identity sequence at 13 — the
definition and the register both say eleven, corrected in the register (`02` §4).

`order_mail_outbox` was added by **D11** and deliberately has **no foreign key** on
`order_id`. The committed comment says why: the table is infrastructure, an FK would drag it
into the order aggregate's delete semantics, and it is one fewer key for this extraction to
reason about.

---

## 5. Transport models — all in `api/models/`

### 5.1 `OrderPojo` — 18 fields

`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude` (bare, i.e. `ALWAYS`).

| # | Field | Validation |
|---|---|---|
| 1 | `Long buyOrder` | — |
| 2 | `String token` | **`@JsonIgnore`** |
| 3 | `Instant date` | — |
| 4 | `Collection<OrderDetailPojo> details` | **`@Valid @NotEmpty @JsonInclude(NON_EMPTY)`** |
| 5–9 | `int netValue, taxValue, transportValue, totalValue, totalItems` | — |
| 10 | `String status` | — |
| 11 | `String billingType` | — |
| 12 | `String paymentType` | **`@NotBlank`** |
| 13 | `PersonPojo customer` | **`@Valid`** |
| 14 | `PersonPojo salesperson` | — |
| 15 | `String shipper` | — |
| 16 | `BillingCompanyPojo billingCompany` | — |
| 17 | `AddressPojo billingAddress` | — |
| 18 | `AddressPojo shippingAddress` | — |

Five facts that are contract:

1. **`token` is `@JsonIgnore`** — the payment token is never serialised to a client, in
   either direction. It reaches the client only inside `PaymentRedirectionDetailsPojo`.
2. **`details` is the only field with a field-level `@JsonInclude(NON_EMPTY)`** on a pojo
   whose class-level policy is `ALWAYS` — so an empty line collection is omitted while every
   other field is always emitted.
3. **`@Valid` cascades into `details` and `customer` but *not* into
   `billingCompany`/`billingAddress`/`shippingAddress`** — which is why
   `BillingCompanyPojo`'s total absence of constraints (BR-BILLING-013) is never caught at
   the boundary.
4. **`@NotEmpty` on `details` is BR-ORDER-002** — an order must have at least one line.
5. **The five monetary fields are client-supplied and server-overwritten.** `updateTotals`
   recomputes `netValue`, `taxesValue`, `totalValue` and `totalItems` from the lines; whatever
   the client sent is discarded. **`transportValue` is the exception — it is read from nothing
   and written by nothing** (BR-ORDER-017, W4).

### 5.2 `OrderDetailPojo`

`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude`.

```java
@Min(1) private int units;       // BR-ORDER-003
private int unitValue;           // ignored on input - BR-ORDER-009
private String description;      // ignored on input - BR-ORDER-010
@NotNull private ProductPojo product;   // BR-ORDER-003
```

**`unitValue` and `description` are accepted and then overwritten.** The converter sets
`unitValue = product.getPrice()` and
`description = detail.getUnits() + "x " + product.getName()`. A client cannot influence
either — which is the whole of **BR-ORDER-009** and **BR-ORDER-010**, and the reason a
tampered price is not an exploit.

### 5.3 `ReceiptPojo`

`@Data @JsonInclude` — **no `@Builder`, no constructors** (boundary warning W10).

`long buyOrder` (**primitive**, unlike `OrderPojo`'s boxed `Long`),
`Collection<ReceiptDetailPojo> details`, `Instant date`, `String status`, **`String token`**,
`int totalValue`, `int taxValue`, `int transportValue`, `int totalItems`.

**No validation annotations at all** — correct, since it is response-only. Note that
**`token` is *not* `@JsonIgnore` here**, so the receipt echoes the transaction token back to
an anonymous caller who already had to know it. See `06` §5.

### 5.4 `ReceiptDetailPojo`

`@Data @JsonInclude`. `ProductPojo product`, `int units`, `Integer unitValue`,
`String description`. No validation.

**The `product` here carries only `name` and `barcode`** — `ReceiptServiceImpl` builds a
deliberately minimal `ProductPojo` (**BR-ORDER-047**), which is what makes cut **C2**
tractable: the receipt needs two fields, not a product.

### 5.5 `PaymentRedirectionDetailsPojo`

`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude`. `String url`,
`String token`. Javadoc: *"Wrapper class for data needed to redirect towards payment page"*.

**This is the only place a token reaches a client**, and it must stay that way: D4's nonce is
deliberately **not** added here (`04` §5).

### 5.6 `OrderStatusPojo` and `PaymentTypePojo`

```java
@Data @Builder @JsonInclude
public class OrderStatusPojo {
    @NotBlank private Integer code;                        // W9
    @JsonInclude(NON_EMPTY) @NotBlank private String name;
}
```

**`@NotBlank` on an `Integer` is an invalid constraint target** — boundary warning **W9**.
Bean Validation would raise `UnexpectedTypeException` if it were ever evaluated. It never is,
because `DataOrderStatusesController` exposes **read only** (BR-ORDER-065) and no route binds
this pojo as a body.

Port it as-is. Removing the annotation is a correctness improvement that changes nothing
observable, and it is exactly the kind of unrequested edit G1 excludes — but it is recorded,
because adding a write route to order statuses later would fail at runtime in a confusing way.

Note also `OrderStatusPojo` and `PaymentTypePojo` have **no `@NoArgsConstructor`/
`@AllArgsConstructor`**, like `BillingTypePojo` and unlike `ShipperPojo`. The inconsistency is
real and each pojo ports as written.
