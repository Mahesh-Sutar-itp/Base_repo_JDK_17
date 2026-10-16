# Shipper — Entity and Transport Model

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`02-shipper-services.md`. Paths relative to `src/main/java/org/trebol/`.

---

## 1. `jpa/entities/Shipper.java` — `serialVersionUID = 18L`

### 1.1 Declaration, in full

```java
@Entity
@Table(name = "shippers")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Shipper
    implements DBEntity {
    private static final long serialVersionUID = 18L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipper_id", nullable = false)
    private Long id;
    @Column(name = "shipper_name", nullable = false, unique = true)
    private String name;
```

That is the entire entity. **Two columns, no relationships, no lifecycle callbacks, no
`@Version`, no indexes beyond the primary and unique keys.**

| Fact | Consequence |
|---|---|
| `name` is `unique = true` | **BR-SHIPPER-001** — carrier names are unique, backed by `UK_SHIPPER_NAME` in the DDL |
| `name` is the **only** business attribute | **BR-SHIPPER-002**. No address, no rate, no service level, no coverage area, no contact. The modelled-but-unbuilt shipping-rate capability is a non-goal of this conversion |
| **`name` has no `@Size` annotation at all** | **BR-SHIPPER-005** — length is bounded only by the column's `varchar(255)`. Every other string column in the billing and shipper domains carries an entity-level `@Size`; this one does not. Boundary warning W4 |
| `nullable = false` | a carrier must have a name at the database level; the service layer rejects a blank one first (`02` §3) |

### 1.2 Copy constructor

```java
public Shipper(Shipper source) {
    this.id = source.id;
    this.name = source.name;
}
```

**`id` is copied.** This is load-bearing for the patch path: `ShippersPatchServiceImpl`
builds `new Shipper(existing)`, and because the copy retains the primary key,
`saveAndFlush` issues an **update** rather than an insert.

It also interacts with Lombok `@EqualsAndHashCode`, which covers **both** fields including
`id` with no excludes. `CrudGenericService.flushPartialChanges` compares the patched copy
with the original and **skips the write entirely** when they are equal — so a patch that
changes nothing is a silent, successful no-op.

### 1.3 Committed DDL — `src/main/resources/schema-mariadb.sql`

```sql
CREATE TABLE `shippers` (
  `shipper_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `shipper_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`shipper_id`),
  UNIQUE KEY `UK_SHIPPER_NAME` (`shipper_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**No `AUTO_INCREMENT` seed value**, because `shippers` is **not seeded** — unlike
`billing_types` (`AUTO_INCREMENT=3`) and `payment_types` (`AUTO_INCREMENT=2`). A fresh
database has no carriers at all, and an order placed before any are created simply has no
shipper (BR-SHIPPER-014).

**`shippers` is a pure parent.** It declares no outbound foreign key. The single key that
references it is `FK_ORDER_SHIPPER_ID`, declared on `orders` — which travels into the same
Orders service, so **Phase 3 cuts nothing here**. It is also why a referential delete failure
surfaces as a database error rather than an application one (BR-SHIPPER-013).

---

## 2. `api/models/ShipperPojo.java`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude
public class ShipperPojo {
    @NotBlank
    private String name;
}
```

| Fact | Consequence |
|---|---|
| **`@JsonInclude` with no argument** | the annotation default, `ALWAYS`. The same policy as `BillingTypePojo`, and **not** the `NON_EMPTY` of `AddressPojo` or the `NON_NULL` of `PersonPojo`. Three inclusion policies coexist in this codebase and each is client-observable contract (G2) |
| `@NotBlank name` | **BR-SHIPPER-004** — the carrier name is mandatory on the transport model, enforced wherever `@Valid` binds the body: on `POST` and `PUT`, but **not** on `PATCH`, which takes a raw `Map` with no `@Valid` (W5) |
| It carries `@NoArgsConstructor` **and** `@AllArgsConstructor` | unlike `BillingTypePojo`, which has neither and relies on the Lombok builder for deserialisation. The two sibling single-field pojos differ here; port each as it is |
| **No `id` field** | a carrier is addressed by name or by filter map, never by `shipper_id`, so read-modify-write is only possible through the name |
| No length constraint | matching the entity (W4). Nothing between the client and the database bounds the name |

### 2.1 What the conversion drops

| Direction | Dropped |
|---|---|
| `Shipper` → `ShipperPojo` | `shipper.id` — it has nowhere to go |
| `ShipperPojo` → new `Shipper` | nothing from the input; the result has `id == null` |

The second row matters on `PUT`: `CrudGenericService.update(M, Long)` converts the input to a
**new** entity and then sets the id, so a replacement is a full overwrite of the one field
there is. With a single business attribute that is indistinguishable from a patch — which is
why `PUT` and `PATCH` on this domain are behaviourally near-identical, differing only in
whether the body is validated.

---

## 3. Identity and equality summary

| Concept | Carried by | Enforced where |
|---|---|---|
| Database identity | `shippers.shipper_id` | `@Id`, `IDENTITY` generation |
| **Business identity** | `name` | `unique = true` + `UK_SHIPPER_NAME`, resolved by `ShippersCrudServiceImpl.getExisting` (BR-SHIPPER-001) |
| Java equality | **both** fields, `id` included | Lombok `@EqualsAndHashCode`, consumed by `flushPartialChanges` |
| API identity | **nothing** | `ShipperPojo` has no id |

A blank name is rejected before any query runs (BR-SHIPPER-003) — with the **wrong-domain
message** `"Billing type has no name"`, which is boundary warning W1 and ports verbatim. See
`02` §3 and `errors.md` §1.
