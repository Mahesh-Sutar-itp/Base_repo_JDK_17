# Customer — Entity and Transport Model

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`02-customer-services.md`. Paths relative to `src/main/java/org/trebol/`.

---

## 1. `jpa/entities/Customer.java`

### 1.1 Declaration

```java
@Entity
@Table(name = "customers")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Customer
    implements DBEntity {
    private static final long serialVersionUID = 4L;
```

`serialVersionUID = 4L` — port verbatim; it is part of the serialised form.
**No `@Table(uniqueConstraints = ...)`** is declared: the uniqueness of `person_id` is
expressed by the association cardinality (§1.3) and, in the committed DDL, by an explicit
`UNIQUE KEY` (§1.5).

### 1.2 Columns

| Field | Annotations | Notes |
|---|---|---|
| `Long id` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "customer_id", nullable = false)` | surrogate key, never exposed by the API (BR-CUSTOMER-005) |

That is the **complete** column list. The entity has no other scalar field, which is the
literal form of BR-CUSTOMER-004 — the customer has no attributes of its own.

### 1.3 The one relationship

```java
@JoinColumn(name = "person_id", referencedColumnName = "person_id")
@OneToOne(optional = false, cascade = CascadeType.ALL)
private Person person;
```

Four things matter for the port, and all four are load-bearing:

1. **`@OneToOne`**, not `@ManyToOne`. This is what makes one-customer-per-person a
   structural property (BR-CUSTOMER-001) and it is the single difference from
   `Salesperson`, which uses `@ManyToOne` (BR-SALESPERSON-002). Changing either changes
   the generated DDL.
2. **`fetch` is not declared**, so the JPA default for `@OneToOne` applies: **EAGER**
   (BR-CUSTOMER-022). Every customer read joins `people`. The converter depends on this,
   because it dereferences `getPerson()` with no null check and no initialisation guard.
3. **`optional = false`** is what makes the person mandatory (BR-CUSTOMER-002). Note the
   `@JoinColumn` itself declares **no `nullable` attribute**, so the column-level default
   (`nullable = true`) applies; mandatoriness comes from the association, and the
   committed DDL states `NOT NULL` explicitly.
4. **`cascade = CascadeType.ALL`** — creating a customer persists the person in the same
   transaction (BR-CUSTOMER-003), and on `PUT` it **inserts a new person row** (§1.6).
   It also means a customer delete *would* cascade to the person, which contradicts
   BR-CUSTOMER-014; see `00` §2 W3.

### 1.4 Copy constructor

```java
/**
 * Please note: this copy-constructor DOES include a Customer's relationship to its own profile data
 *
 * @param source The original UserRolePermission
 */
public Customer(Customer source) {
    this.id = source.id;
    this.person = new Person(source.person);
}
```

- **`id` IS copied**, unlike `Product` and `ProductCategory` which deliberately omit it.
- The person is **deep-copied** via `new Person(source.person)`, which itself copies
  `person.id`.
- The javadoc's `@param` text says `UserRolePermission` — a **copy-paste error in the
  original**. Preserve the comment verbatim: it is inside a 5+ line comment block and
  carries no behaviour. Do not "fix" it during the port.

The copy constructor is used by exactly one caller, `CustomersPatchServiceImpl`, and its
id-copying behaviour is what makes `CrudGenericService.flushPartialChanges` work: that
method compares the patched copy to the original using Lombok `equals`, which here
**includes both `id` and `person`** (no `@EqualsAndHashCode.Exclude` anywhere). A patch
that changes nothing therefore compares equal and **skips the write entirely**.

### 1.5 Committed DDL — `src/main/resources/schema-mariadb.sql`

```sql
DROP TABLE IF EXISTS `customers`;
CREATE TABLE `customers` (
  `customer_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `person_id` bigint(20) NOT NULL,
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `UK_CUSTOM_PERSON_ID` (`person_id`),
  CONSTRAINT `FK_CUSTOM_PERSON_ID` FOREIGN KEY (`person_id`) REFERENCES `people` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**`UNIQUE KEY UK_CUSTOM_PERSON_ID` is the only database-level expression of
BR-CUSTOMER-001 and it must port with its name unchanged.** The annotations and the DDL
agree here — which is *not* true of the three asymmetries listed in `00` §2 of the user
spec, and not true of `salespeople` (no unique key at all). An engineer tidying the three
identity tables into a consistent shape would silently either weaken this constraint or
invent one on `salespeople`; both are behavioural changes (G1).

The FK targets `people`, which is inside Identity. **No FK leaves the identity set.**

### 1.6 Lifecycle

**None.** No `@PrePersist`, no `@PreUpdate`, no `@Version`, no optimistic locking. Every
state change is explicit in the service layer, which is why `02` can specify this domain
entirely as step sequences.

---

## 2. Transport model — `api/models/PersonPojo.java`

This domain has **no transport model of its own** (BR-CUSTOMER-008). Every generic
parameter is bound to `PersonPojo`:

```java
CustomersCrudService      extends CrudService<PersonPojo, Customer>
CustomersConverterService extends ConverterService<PersonPojo, Customer>
CustomersPatchService     extends PatchService<PersonPojo, Customer>
DataCustomersController   extends DataCrudGenericController<PersonPojo, Customer>
```

`PersonPojo` is owned and specified by the person domain
(`detailed-business-specification/domain-person/`). Recorded here only as the facts this
domain's behaviour depends on:

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)
public class PersonPojo {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank private String idNumber;
    @NotBlank private String email;
    // @Pattern(regexp = "^(((\\(\\+?[0-9]{3}\\))|(\\+?[0-9]{3})) ?)?[0-9]{3,4}[ -]?[0-9]{4}$")
    private String phone1;
    // @Pattern(...) (commented out)
    private String phone2;
}
```

| Fact | Consequence for this domain |
|---|---|
| **No `id` field of any kind** | the API cannot address a customer by `customer_id` **or** by `person_id`; `idNumber` is the only usable key (`00` §2 W1) |
| `@JsonInclude(NON_NULL)` | a null `phone1`/`phone2` is **omitted** from responses, not emitted as `null`. Contrast `CompanyDetailsPojo`, which uses `ALWAYS` — the two conventions coexist in this codebase and both are client-observable contract (G2) |
| `@NotBlank` on four fields | combined with `@Valid` on the controller's `@RequestBody`, these four are mandatory on both POST and PUT (BR-CUSTOMER-009). Rejection is a Spring `MethodArgumentNotValidException` → HTTP 400, **not** a `BadInputException` |
| **The two `@Pattern` annotations are commented out** | phone numbers are entirely unvalidated. Port the commented-out lines verbatim — they document an intentional relaxation, and uncommenting them would start rejecting currently-accepted input (G1) |
| `phone1`/`phone2` have **no** `@NotBlank` | both are optional, and `Person` defaults them to `""` via `@Builder.Default` |

### 2.1 What the conversion drops

| Direction | Dropped |
|---|---|
| `Customer` → `PersonPojo` | `customer.id` **and** `person.id` — neither has anywhere to go |
| `PersonPojo` → new `Customer` | nothing from the input, but the result has `customer.id == null` **and** `person.id == null` |

The second row is the mechanism behind `00` §2 W2: on `PUT`,
`CrudGenericService.update(M, Long)` sets only the **outer** entity's id, so the nested
`Person` is still id-less and `cascade = ALL` inserts a **new** `people` row rather than
updating the existing one. The old person row is orphaned and no code path deletes it —
the same orphaning shape that made transactional registration necessary (ADR-001
§Decision.1, BR-PERSON-013).

---

## 3. Identity and equality summary

| Concept | Carried by | Enforced where |
|---|---|---|
| Database identity | `customers.customer_id` | `@Id`, `IDENTITY` generation |
| **Business identity** | `person.idNumber`, via `PersonPojo.idNumber` | `CustomersCrudServiceImpl.getExisting` + `UK_PERSON_ID_NO` on `people` (BR-CUSTOMER-005) |
| One-customer-per-person | `customers.person_id` | `@OneToOne` + `UK_CUSTOM_PERSON_ID` (BR-CUSTOMER-001) |
| Java equality | **all** fields, `id` and `person` included | Lombok `@EqualsAndHashCode`, consumed by `flushPartialChanges` |
| API identity | **nothing** | `PersonPojo` has no id (`00` §2 W1) |

A blank `idNumber` is rejected before any query runs, with the literal
`"Customer does not have an ID card"` (BR-CUSTOMER-006) — see `02` §3 and `errors.md` §1.
