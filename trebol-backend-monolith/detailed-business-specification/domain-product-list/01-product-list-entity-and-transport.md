# 01 — Product List Entities and Transport Model

Source files covered:

| File | Role |
|---|---|
| `src/main/java/org/trebol/jpa/entities/ProductList.java` | persisted entity |
| `src/main/java/org/trebol/jpa/entities/ProductListItem.java` | persisted entity (membership) |
| `src/main/java/org/trebol/api/models/ProductListPojo.java` | transport model |

This document specifies **behaviour**; column layout is restated because no
porting-depth data-model document existed for this domain.

---

## 1. `ProductList` — persisted entity

### 1.1 Class overview

A named, curated collection of products. Lombok
`@Builder @NoArgsConstructor @AllArgsConstructor @Getter @Setter @EqualsAndHashCode
@ToString`; implements `DBEntity`; `serialVersionUID = 16L`.

A plain mutable data holder: no validation logic, no computed properties, no business
methods, no lifecycle callbacks.

### 1.2 Table and columns

```
@Table(name = "product_lists", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_list_name"}),
    @UniqueConstraint(columnNames = {"product_list_code"})
})
```

| Field | Column | Type | Nullable | Unique | Validation |
|---|---|---|---|---|---|
| `id` | `product_list_id` | `Long`, `IDENTITY` generated | no | primary key | — |
| `name` | `product_list_name` | `String` | no | **yes**, table-level | `@Size(min = 2, max = 50)` |
| `code` | `product_list_code` | `String` | no | **yes**, table-level | `@Size(min = 2, max = 25)` |
| `disabled` | `product_list_disabled` | **primitive `boolean`** | no | no | **none** |

**Two separate single-column unique constraints**, both declared at table level — no
`unique` attribute appears on any `@Column`, and no column declares `length` or
`columnDefinition`. The `@Size` annotations carry **no custom `message`**, so a violation
returns the framework default text — client-observable under G2.

**`min = 2` on both strings** (BR-PRODLIST-002): a one-character code or name is
rejected. This is stricter than any other catalogue entity and applies at flush, since
the transport model does not mirror it (§3.2).

**`disabled` is a primitive `boolean`**, so it is mandatory and defaults to `false`. Its
story is told in §1.6.

### 1.3 The `items` relationship

```
@OneToMany(mappedBy = "list")
private List<ProductListItem> items;
```

- **No cascade, no `orphanRemoval`, no fetch override** (so LAZY, the `@OneToMany`
  default), no join table — the FK lives on the item side.
- Because there is no cascade and no orphan removal, **deleting a list does not delete
  its membership**. The CRUD service compensates by explicitly deleting children first
  (document `02` §3.1, BR-PRODLIST-021).
- The collection is used for exactly one other thing: the `totalCount` sort key is
  `items.size()`, an aggregate subquery per row (document `02` §8.1).

### 1.4 Generated members

| Generated member | Behaviour |
|---|---|
| No-argument constructor | Reference fields null, `disabled` **false**. Required for ORM materialization. |
| All-arguments constructor | `id`, `name`, `code`, `disabled`, `items` positionally in declaration order; assigned verbatim with no validation. |
| Builder | Fluent construction. **No field defaults** beyond the primitive's `false`. Terminal build performs no validation. |
| Getters / setters | One pair per field. No validation, no trimming, no null rejection. |
| Equality / hash | Lombok, **no exclusions** — includes `id`, `name`, `code`, `disabled` **and `items`**. See §1.5. |
| String representation | Lombok `@ToString`, all fields **including `items`** — logging a list can trigger a lazy load or a lazy-initialization failure. |

### 1.5 Equality semantics — load-bearing, and a hazard

Equality covers all five fields **including the lazy `items` collection**. Consequences:

1. A loaded entity and a patched clone are equal exactly when the patch changed nothing.
   `CrudGenericService.flushPartialChanges` compares them and skips the write when equal.
2. **But the clone's `items` is always null** (§1.6) while a loaded entity's may be a
   populated or uninitialized proxy. The comparison can therefore **trigger a lazy load**,
   and two logically identical lists can compare unequal purely because of collection
   state. In practice the patch always runs inside the CRUD service's transaction, so the
   load succeeds and the comparison is dominated by the scalar fields.

A rewrite defining equality by identifier alone breaks the no-op patch optimisation
(every clone shares its source's `id`); a rewrite that keeps all fields must keep the
collection in the comparison or accept a behavioural change.

### 1.6 Copy constructor `ProductList(ProductList source)`

Copies `id`, `name`, `code` and `disabled`, then leaves **`items = null`** — explicitly
not copied. The javadoc says so verbatim: *"Please note: this copy-constructor does not
include a ProductList's relationship to its items"*, and carries a copy-paste artefact,
`@param source The original Sell`, which ports as-is.

**`disabled` *is* copied.** That is why `PATCH` preserves the flag while `PUT` destroys
it: the patch path clones the entity (flag intact) and the patch service never touches
the key, whereas `PUT` builds a brand-new entity from the converter, which never maps
`disabled` at all, so the primitive default `false` is written (BR-PRODLIST-008, W6).

**`id` is copied**, so the clone denotes the same row and saving it updates rather than
inserts.

**Error paths.** A null `source` causes a null dereference on the first field read —
unguarded programming error, not a handled condition.

### 1.7 The `disabled` flag — mandatory, inert, and unreachable

| Property | Fact |
|---|---|
| Storage | `NOT NULL`, primitive, defaults to `false`. |
| On the transport model | **Absent entirely** (BR-PRODLIST-010). Clients cannot read or set it. |
| On `PATCH` | Not a recognised key; silently ignored. The flag survives because the copy constructor carries it. |
| On `PUT` | **Reset to `false`**, because the converter never maps it (BR-PRODLIST-008). |
| Read by application code | **Never.** Not by the list controller, not by the contents resolver, not by any predicate or sort spec (BR-PRODLIST-009). A disabled list behaves exactly like an enabled one. |

So the only way to set it is a direct database write, and the only way to clear it is any
`PUT`. It is a modelled-but-unintegrated capability and must be ported as-is, column
included.

### 1.8 Lifecycle callbacks, overrides, finders

**None of any kind.** No `@PrePersist`, `@PreUpdate`, `@PostLoad`, `@PreRemove`, no
entity listeners, no save/delete overrides, no static finders.

---

## 2. `ProductListItem` — persisted membership entity

### 2.1 Class overview

One row per product in one list. `@Entity @Table(name = "product_list_items")`, same
Lombok set, implements `DBEntity`, `serialVersionUID = 17L`.

**It carries no payload of its own** — no position, no ordering, no added-at timestamp,
no price override. It is pure membership, which is why the read path returns products
rather than membership rows (BR-PRODLIST-013).

### 2.2 Columns and relationships

| Field | Column | Declaration |
|---|---|---|
| `id` | `product_list_item_id` | `@Id @GeneratedValue(strategy = IDENTITY) @Column(nullable = false)` |
| `list` | `product_list_id` | `@ManyToOne(optional = false)` + `@JoinColumn(nullable = false)` |
| `product` | `product_id` | `@ManyToOne(optional = false)` + `@JoinColumn(nullable = false)` |

**`@Table` declares no `uniqueConstraints` at all.** There is therefore **no database
protection against duplicate membership**; the only guard is an application-level
`exists(Example.of(...))` check with no transaction around it (BR-PRODLIST-014, W3).

**Neither association declares a `fetch` attribute**, and the `@ManyToOne` default is
**EAGER** (BR-PRODLIST-020, W8). Every membership row load therefore pulls a full
`ProductList` and a full `Product` — which is also why the contents read works at all
without a `JOIN FETCH`, and why it is expensive.

**Both are `optional = false` with `nullable = false` join columns**, so a membership row
must have both ends. There is **no cascade and no `orphanRemoval`** on either side; the
`ON DELETE CASCADE` on the product FK in the schema is what silently removes a deleted
product from every list (BR-PRODLIST-023). **That DDL must port**, because the
application-level equivalent (`deleteByProductId`) has no caller (BR-PRODLIST-024).

**Membership rows are immutable in practice** (BR-PRODLIST-019): there is no patch
service, no update path and no transport model for them. The only operations are insert
and delete.

### 2.3 Copy constructor `ProductListItem(ProductListItem source)`

Copies **only `id`**, then leaves `list = null` and `product = null` — both explicitly
not copied.

The result is an id-only shell, useless for persistence since both relations are
`optional = false`. **No production code calls it.** It exists for contract symmetry and
is a trap for a rewrite that assumes copy constructors are usable.

### 2.4 Generated members and callbacks

Builder, getters, setters and all-args constructor as generated. Equality and
`toString` cover `id`, `list` and `product`, so comparing or logging a membership row
walks the **eager** graph into both a list and a product. **No lifecycle callbacks.**

---

## 3. `ProductListPojo` — transport model

### 3.1 Class overview

The external representation of a *list* — not of its contents. `@Data @Builder
@NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)` at class level.

**Three omissions, each a rule:**

1. **No `id` field** — no surrogate-key addressing through the transport model.
2. **No `disabled` field** (BR-PRODLIST-010) — the flag is invisible and unsettable.
3. **No membership model anywhere in the domain.** Contents are transported as
   `ProductPojo`, the *product* domain's model, and
   `ProductListItemsConverterService extends ConverterService<ProductPojo, ProductListItem>`
   converts straight between them.

### 3.2 Fields, validation and serialization

| Field | Type | Validation | JSON inclusion |
|---|---|---|---|
| `name` | `String` | **none** | class default `NON_NULL` |
| `code` | `String` | `@NotEmpty` — `jakarta.validation.constraints.NotEmpty`, **no custom message** | `NON_NULL` |
| `totalCount` | **primitive `long`** | **none** | `@JsonInclude(NON_DEFAULT)` — omitted when 0 |

**The asymmetry is the rule (BR-PRODLIST-005): the transport model constrains the `code`
but not the `name`** — while the *service* layer resolves existence by `name` and
rejects a blank one (document `02` §3.2). So:

- A `POST` with no `name` passes bean validation and then fails in `getExisting` with
  `BadInputException("The specified list has no name")` → 400.
- `@NotEmpty`, not `@NotBlank`, is used on `code`: a value of `" "` (a single space)
  **passes** validation and reaches the entity, where `@Size(min = 2)` then rejects it at
  flush as a 500.
- Neither field mirrors the entity's `@Size` bounds, so over-length and under-length
  input fails at the database layer rather than at the route.

**`totalCount` is output-only.** It is computed by the converter with a `COUNT` query
(document `02` §4.1) and is never read on input — supplying it is silently ignored.
`NON_DEFAULT` means an **empty list reports no `totalCount` field at all** rather than
`0`, which a client must not mistake for "unknown".

### 3.3 Validation scope limits

1. **`PATCH`** takes a free-form `Map<String, Object>`, so no annotation applies. The
   patch service's own guards replace them and ignore blanks rather than rejecting them
   (BR-PRODLIST-008 and document `02` §5).
2. **The contents `PUT`** takes `Collection<ProductPojo>` and is **not annotated
   `@Valid`**, while the contents `POST` single body **is** (BR-PRODLIST-018, W11).
   Element-level validation is therefore skipped on the bulk path — a blank barcode in the
   collection reaches `getExisting` and throws mid-loop, after the destructive delete has
   already committed (W1).
3. **`DELETE`** and **`GET`** take no body.

### 3.4 Known quirks

**A `PUT` built from a read-back list silently re-enables it.** The response never
carries `disabled`, the model has no field for it, and the converter writes the primitive
default — so every full replacement clears the flag (W6).

**`totalCount` is not round-trippable.** It appears in a response, is meaningless in a
request, and its absence means zero.

**The contents API is addressed entirely by `listCode`**, while list existence is checked
by `name`. The two identifiers are used for different purposes in the same domain, which
is the root of W5.
