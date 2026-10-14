# Salesperson — Entity and Transport Model

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`02-salesperson-services.md`. Paths relative to `src/main/java/org/trebol/`.

Points marked **⚠ DIFFERS** are where this domain diverges from its near-twin `customer`.

---

## 1. `jpa/entities/Salesperson.java`

### 1.1 Declaration

```java
@Entity
@Table(name = "salespeople")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Salesperson
    implements DBEntity {
    private static final long serialVersionUID = 13L;
```

`serialVersionUID = 13L` — port verbatim (the customer's is `4L`). **No
`@Table(uniqueConstraints = ...)`**, and unlike `customers` there is no `UNIQUE KEY` in
the DDL either (§1.5).

### 1.2 Columns

| Field | Annotations | Notes |
|---|---|---|
| `Long id` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "salesperson_id", nullable = false)` | surrogate key, never exposed by the API (BR-SALESPERSON-005) |

That is the **complete** column list — the literal form of BR-SALESPERSON-004. The
salesperson has no commission rate, no territory, no employment dates, no status flag.
Anything a reader expects a "salesperson" to have, this entity does not have.

### 1.3 The one relationship

```java
@JoinColumn(name = "person_id", referencedColumnName = "person_id")
@ManyToOne(optional = false, cascade = CascadeType.ALL)
private Person person;
```

| Attribute | Value | Consequence |
|---|---|---|
| **cardinality** | **⚠ DIFFERS — `@ManyToOne`**, not `@OneToOne` | several salespeople may share one person (BR-SALESPERSON-002). This is the structural difference from `customer` and it is deliberate |
| `fetch` | **not declared** → JPA default for `@ManyToOne` is **EAGER** | every salesperson read joins `people` (BR-SALESPERSON-023); the converter depends on it |
| `optional = false` | mandatory association | a salesperson cannot exist without a person (BR-SALESPERSON-001) |
| `cascade = ALL` | person persisted in the same transaction | creating a salesperson creates or reuses a person atomically (BR-SALESPERSON-003); on `PUT` it **inserts a new person row** (§1.6); and a delete *would* cascade, contradicting BR-SALESPERSON-018 (`00` §2 W4) |

`@JoinColumn` declares **no `nullable` attribute**, so mandatoriness comes from
`optional = false` on the association; the committed DDL states `NOT NULL` explicitly.

### 1.4 Copy constructor

```java
/**
 * Please note: this copy-constructor DOES include a Salesperson's relationship to its own profile data
 *
 * @param source The original Sell
 */
public Salesperson(Salesperson source) {
    this.id = source.id;
    this.person = new Person(source.person);
}
```

- **`id` IS copied**; the person is deep-copied including `person.id`.
- The javadoc's `@param` text says `Sell` — a **copy-paste error in the original**, the
  same class of defect as the customer entity's stray `UserRolePermission`. Both are
  preserved verbatim: they sit inside comment blocks and carry no behaviour.

Used by exactly one caller, `SalespeoplePatchServiceImpl`. The id-copying is what makes
`CrudGenericService.flushPartialChanges` short-circuit correctly: it compares the patched
copy with the original using Lombok `equals`, which **includes `id` and `person`** (no
excludes anywhere), so a patch that changes nothing **skips the write**.

### 1.5 Committed DDL and the uniqueness asymmetry

`src/main/resources/schema-mariadb.sql`:

```sql
DROP TABLE IF EXISTS `salespeople`;
CREATE TABLE `salespeople` (
  `salesperson_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `person_id` bigint(20) NOT NULL,
  PRIMARY KEY (`salesperson_id`),
  CONSTRAINT `FK_SPERSON_PERSON_ID` FOREIGN KEY (`person_id`) REFERENCES `people` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**There is no `UNIQUE KEY` on `person_id`.** Compare the sibling table:

| Table | Association | `person_id` unique? | Constraint name |
|---|---|---|---|
| `customers` | `@OneToOne` | **YES** | `UK_CUSTOM_PERSON_ID` |
| `salespeople` | `@ManyToOne` | **NO** | — (only `FK_SPERSON_PERSON_ID`) |
| `app_users` | `@ManyToOne`, `fetch = LAZY`, **optional** | **NO** | — (BR-USER-004 is enforced in application code only) |

¹ see `domain-user/01` §1.3 — the third and loosest variant: the person link is
*optional* there (BR-USER-003), it is LAZY rather than EAGER, and the
one-person-one-account rule (BR-USER-004) has **no database expression at all**. It is
enforced only by an application check in `ProfileServiceImpl`, with the literal
`"Person profile is associated to another account. Cannot use it."`

**The annotations and the DDL agree here**, so this is a real design decision, not a
schema drift. The three identity tables express three *different* person-cardinality
rules, and the inconsistency is the point: harmonising them is a behavioural change in
every direction (G1).

**The latent fault this creates.** `SalespeopleRepository.findByPersonIdNumber` returns
`Optional<Salesperson>` over a **non-unique** column. Two salespeople sharing one person
would make Spring Data raise a non-unique-result exception → HTTP 500. It is unreachable
today only because `people.person_id_number` carries `UNIQUE KEY UK_PERSON_ID_NO`, so a
second salesperson can only be attached to the *same* person row by direct manipulation
or by the order-side cascade (`00` §2 W2). Recorded, not fixed.

### 1.6 Lifecycle

**None.** No `@PrePersist`, no `@PreUpdate`, no `@Version`, no optimistic locking.

---

## 2. Transport model — `api/models/PersonPojo.java`

This domain has **no transport model of its own** (BR-SALESPERSON-008). Every generic
parameter is bound to `PersonPojo`:

```java
SalespeopleCrudService      extends CrudService<PersonPojo, Salesperson>
SalespeopleConverterService extends ConverterService<PersonPojo, Salesperson>
SalespeoplePatchService     extends PatchService<PersonPojo, Salesperson>
DataSalespeopleController   extends DataCrudGenericController<PersonPojo, Salesperson>
```

`PersonPojo` is owned and specified by the person domain. The facts this domain's
behaviour depends on:

| Fact | Consequence |
|---|---|
| **No `id` field of any kind** | the API cannot address a salesperson by `salesperson_id` or `person_id`; `idNumber` is the only usable key (BR-SALESPERSON-005) |
| `@JsonInclude(NON_NULL)` | a null `phone1`/`phone2` is **omitted**, not emitted as `null` |
| `@NotBlank` on `firstName`, `lastName`, `idNumber`, `email` | with `@Valid` on the controller body, all four are mandatory on POST and PUT (BR-SALESPERSON-009). Violation is a Spring `MethodArgumentNotValidException` → **400**, not a `BadInputException` |
| Both `@Pattern` annotations on the phone fields are **commented out** | phone numbers are entirely unvalidated; port the commented lines verbatim |
| `phone1`/`phone2` have no `@NotBlank` | optional; `Person` defaults them to `""` via `@Builder.Default` |

### 2.1 What the conversion drops

| Direction | Dropped |
|---|---|
| `Salesperson` → `PersonPojo` | `salesperson.id` **and** `person.id` |
| `PersonPojo` → new `Salesperson` | nothing from the input, but the result has `salesperson.id == null` **and** `person.id == null` |

The second row is the mechanism behind `00` §2 W3 and BR-SALESPERSON-013: on `PUT`,
`CrudGenericService.update(M, Long)` sets only the **outer** id, so `cascade = ALL`
inserts a **new** `people` row and repoints `person_id`. The previous person row is
orphaned, and no code path in the system deletes it — the same orphaning shape that made
transactional registration necessary (ADR-001 §Decision.1).

---

## 3. Identity and equality summary

| Concept | Carried by | Enforced where |
|---|---|---|
| Database identity | `salespeople.salesperson_id` | `@Id`, `IDENTITY` generation |
| **Business identity** | `person.idNumber`, via `PersonPojo.idNumber` | `SalespeopleCrudServiceImpl.getExisting` + `UK_PERSON_ID_NO` on `people` (BR-SALESPERSON-005) |
| Person cardinality | **nothing** — many-to-one, no unique key | deliberately unconstrained (BR-SALESPERSON-002) |
| Java equality | **all** fields, `id` and `person` included | Lombok `@EqualsAndHashCode`, consumed by `flushPartialChanges` |
| API identity | **nothing** | `PersonPojo` has no id |
| Login identity | **nothing** | a salesperson is not an account (BR-SALESPERSON-020); no relationship to `app_users` exists |

A blank `idNumber` is rejected before any query runs, with the literal
`"Salesperson does not have an ID card"` (BR-SALESPERSON-006) — note it is **a different
string** from the customer domain's `"Customer does not have an ID card"`. See `02` §3 and
`errors.md` §1.
