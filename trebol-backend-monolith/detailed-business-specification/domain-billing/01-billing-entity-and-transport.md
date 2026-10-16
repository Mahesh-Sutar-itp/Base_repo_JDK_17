# Billing — Entities and Transport Models

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`02-billing-services.md`. Paths relative to `src/main/java/org/trebol/`.

All four entities share the Lombok set
`@Builder @NoArgsConstructor @AllArgsConstructor @Getter @Setter @EqualsAndHashCode @ToString`,
implement `org.trebol.jpa.DBEntity`, use `jakarta.*` imports, and have **no relationships, no
cascade or fetch attributes, and no lifecycle callbacks**. All four copy constructors **copy
`id`**.

---

## 1. `jpa/entities/BillingType.java` — `serialVersionUID = 3L`

```java
@Entity
@Table(name = "billing_types")
...
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "billing_type_id", nullable = false)
private Long id;
@Size(min = 1, max = 100)
@Column(name = "billing_type_name", nullable = false, unique = true)
private String name;

public BillingType(BillingType source) {
    this.id = source.id;
    this.name = source.name;
}
```

Two columns, no relationships. `name` is `unique = true` — **BR-BILLING-001**.

**Only two values are meaningful to the system** (BR-BILLING-002), and they are seeded:
the individual and enterprise types. `Constants.BILLING_TYPE_ENTERPRISE` is the literal the
order domain compares against to decide whether an invoice needs a company and a billing
address (BR-BILLING-007, BR-BILLING-008). The DDL's `AUTO_INCREMENT=3` records that two rows
are seeded.

**The billing type on an order is immutable after creation** (BR-BILLING-004) — not because
of anything here, but because `Order.billingType` is mapped `updatable = false`. See
`domain-order/01`.

---

## 2. `jpa/entities/BillingCompany.java` — `serialVersionUID = 2L`

```java
@Entity
@Table(name = "billing_companies")
...
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "billing_company_id", nullable = false)
private Long id;
@Size(min = 1, max = 20)
@Column(name = "billing_company_id_number", nullable = false, unique = true)
private String idNumber;
@Size(min = 1, max = 100)
@Column(name = "billing_company_name", nullable = false, unique = true)
private String name;

public BillingCompany(BillingCompany source) {
    this.id = source.id;
    this.idNumber = source.idNumber;
    this.name = source.name;
}
```

**Both** `idNumber` and `name` are `unique = true` — **BR-BILLING-009**. The DDL confirms two
separate unique keys, `UK_BILL_CY_ID_NO` and `UK_BILL_CY_NAME`.

**`idNumber` is the business key** used by `getExisting` for de-duplication (BR-BILLING-010).
The name's uniqueness is therefore a *second*, independent constraint that checkout does not
consult — so a company submitted with a known id number but a different name resolves to the
existing row and the submitted name is ignored; a company with a new id number but an
*existing* name fails at the database with a 500 rather than a 409. Preserved.

**It is never format-validated** (BR-BILLING-012). A 20-character ceiling is the only rule;
nothing checks that it looks like a tax identifier.

---

## 3. `jpa/entities/Address.java` — `serialVersionUID = 1L`

```java
@Entity
@Table(
    name = "addresses",
    indexes = {
        @Index(columnList = "address_first_line"),
        @Index(columnList = "address_second_line"),
        @Index(columnList = "address_postal_code")
    },
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {
            "address_city", "address_municipality", "address_first_line",
            "address_second_line", "address_postal_code", "address_notes"
        })
    })
```

| Field | Annotations | Nullable? |
|---|---|---|
| `Long id` | `@Id @GeneratedValue(IDENTITY) @Column(name = "address_id", nullable = false)` | no |
| `String city` | `@Size(max = 50) @Column(name = "address_city", nullable = false)` | no |
| `String municipality` | `@Size(max = 50) @Column(name = "address_municipality", nullable = false)` | no |
| `String firstLine` | `@Size(max = 100) @Column(name = "address_first_line", nullable = false)` | no |
| `String secondLine` | `@Size(max = 50) @Column(name = "address_second_line")` | **yes** |
| `String postalCode` | `@Column(name = "address_postal_code")` — **no `@Size`** | **yes** |
| `String notes` | `@Size(max = 50) @Column(name = "address_notes")` | **yes** |

The copy constructor copies `id` and all six fields.

### 3.1 The six-column unique constraint, and the query that cannot use it

`UK_ADDR` spans **all six** value columns, so an address is identified by its entire content —
there is no shorter natural key. `AddressesRepository.findByFields` takes exactly those six
parameters, which is clearly the intent.

**But three of the six are nullable, and the query compares them with `=`.** In SQL,
`column = NULL` is never true, so:

> **An address with a null `secondLine`, `postalCode` or `notes` can never be found by the
> only query that looks addresses up.** Checkout therefore creates a fresh row instead of
> reusing the existing one — and `UK_ADDR` permits it, because a NULL column makes the unique
> tuple distinct too.

This is boundary warning **W1**. It ports unchanged: correcting the comparison would change
which addresses are reused and which rows accumulate, which is a behavioural change needing
its own ruling (G1). `postalCode` having no `@Size` while the column is `varchar(255)` is the
same kind of latent inconsistency, recorded as W6.

---

## 4. `jpa/entities/PaymentType.java` — owned here

Two columns — `payment_type_id` and a `unique = true` `payment_type_name` (`UK_PAYMT_NAME`) —
no relationships, no callbacks. One row is seeded (`AUTO_INCREMENT=2`): `WebPay Plus`.

**It lives in this domain because nothing else claims it.** `BillingContract` and
`BillingContractImpl` import `PaymentType` and `PaymentTypesRepository`, and the manifest
gives payment types no domain of their own. An order must name a payment type that exists,
and the association is `updatable = false`, so it is fixed at creation.

---

## 5. Transport models

### 5.1 `api/models/BillingTypePojo.java`

```java
@Data @Builder @JsonInclude
public class BillingTypePojo {
    @NotBlank
    private String name;
}
```

Three things to port exactly:

1. **No `@NoArgsConstructor` / `@AllArgsConstructor`** — Jackson deserialises through the
   Lombok builder. Adding them changes edge-case behaviour.
2. **`@JsonInclude` with no argument**, i.e. the annotation default `ALWAYS` — the opposite
   policy from `AddressPojo`'s `NON_EMPTY`. Both coexist deliberately; the emitted JSON shape
   is contract (G2).
3. `@NotBlank` makes the name mandatory on the transport model (**BR-BILLING-006**), enforced
   by `@Valid` wherever a body is bound — which, for this domain, is **nowhere**, since the
   only route is a read (`02` §5).

### 5.2 `api/models/BillingCompanyPojo.java`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude
public class BillingCompanyPojo {
    private String idNumber;
    private String name;
}
```

**Zero validation annotations — BR-BILLING-013.** Not even `@NotBlank` on `idNumber`, which
is the business key. The only guard is `BillingCompaniesCrudServiceImpl.getExisting`, which
throws `BadInputException("Billing company has no id number")` on a blank value
(BR-BILLING-011).

That matters because this pojo arrives **nested inside `OrderPojo`** at checkout, not as a
request body of its own. `OrderPojo` is `@Valid`-ated, but validation cascades only through
fields annotated `@Valid`; nothing here would be checked even if annotations existed. The
service-layer guard is the whole of the validation.

### 5.3 `api/models/AddressPojo.java`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_EMPTY)
public class AddressPojo {
    @NotBlank private String firstLine;
    private String secondLine;
    @NotBlank private String municipality;
    @NotBlank private String city;
    private String postalCode;
    private String notes;
}
```

| Fact | Consequence |
|---|---|
| **`@JsonInclude(NON_EMPTY)`** | an empty-string *or* null `secondLine`/`postalCode`/`notes` is **omitted** from responses. Distinct from `BillingTypePojo`'s `ALWAYS` and from `PersonPojo`'s `NON_NULL` — **three different inclusion policies coexist in this codebase**, and each is client-observable contract |
| Three `@NotBlank` fields | `firstLine`, `municipality`, `city` are mandatory; the three optional ones match the three nullable columns |
| **Field order is `firstLine, secondLine, municipality, city, postalCode, notes`** | not the column order, and not the order `findByFields` takes its parameters (`city, municipality, firstLine, secondLine, postalCode, notes`). Worth noting because a positional mistake at a call site would compile and silently query the wrong columns |
| No `id` field | addresses are addressed only by content, which is what makes `UK_ADDR` and `findByFields` the whole identity story |

---

## 6. Identity and equality summary

| Entity | Database identity | Business identity | Java equality |
|---|---|---|---|
| `BillingType` | `billing_type_id` | `name` (`UK_BILL_TYP_NAME`) | all fields incl. `id` |
| `BillingCompany` | `billing_company_id` | `idNumber` (`UK_BILL_CY_ID_NO`); `name` separately unique | all fields incl. `id` |
| `Address` | `address_id` | the **six-column tuple** (`UK_ADDR`) | all fields incl. `id` |
| `PaymentType` | `payment_type_id` | `name` (`UK_PAYMT_NAME`) | all fields incl. `id` |

Lombok `@EqualsAndHashCode` covers every field including `id` on all four, with no excludes.
Combined with copy constructors that **copy `id`**, this is what makes the patch path work:
`CrudGenericService.flushPartialChanges` compares the patched copy with the original and
skips the write when they are equal, and the retained `id` means a save is an update rather
than an insert.
