# 01 — Product Category Entity and Transport Model

Source files covered:

| File | Role |
|---|---|
| `src/main/java/org/trebol/jpa/entities/ProductCategory.java` | persisted entity |
| `src/main/java/org/trebol/api/models/ProductCategoryPojo.java` | transport model |

This document specifies **behaviour**. Column layout is restated here because no
porting-depth data-model document existed for this domain.

---

## 1. `ProductCategory` — persisted entity

### 1.1 Class overview

A node in a single-parent category tree: a business `code`, a `name`, an optional
`parent` reference and a database-generated surrogate key. A plain mutable data holder
— no validation logic, no computed properties, no business methods. All behaviour
acting on a category lives in the service layer (document `02`).

Serializable via the shared-kernel `DBEntity` contract with an explicit
`serialVersionUID` of `11L` — relevant only if binary compatibility with existing
serialized instances must be preserved.

### 1.2 Table and columns

Table `product_categories`, with one composite table-level unique constraint:

```
uniqueConstraints = { @UniqueConstraint(columnNames = {"parent_product_category_id", "product_category_name"}) }
```

| Field | Column | Type | Nullable | Unique | Validation |
|---|---|---|---|---|---|
| `id` | `product_category_id` | `Long`, `IDENTITY` generated | no | primary key | — |
| `code` | `product_category_code` | `String` | no | **yes, column-level** | `@Size(min = 1, max = 50)` |
| `name` | `product_category_name` | `String` | no | only within the composite constraint above | `@Size(min = 1, max = 100)` |
| `parent` | `parent_product_category_id` | FK → `product_category_id` | **yes** | — | — |

Neither string column declares `length` or `columnDefinition`; the width comes from
the `@Size` bound via Hibernate DDL and from `schema-mariadb.sql` when that script is
used.

**Two different uniqueness scopes, and they matter.** `code` is globally unique;
`name` is unique only among siblings (BR-PRODCAT-001, BR-PRODCAT-002). Two categories
may therefore share a name under different parents, and a rewrite that promotes `name`
to a global unique key will reject data the current system accepts.

**Null parent means root.** There is no root sentinel row and no depth column.

### 1.3 The `parent` relationship

```
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_product_category_id", referencedColumnName = "product_category_id")
```

- **No cascade of any kind.** Saving a child never saves or updates its parent;
  deleting a parent never deletes children — the database FK blocks it instead
  (BR-PRODCAT-016).
- **Not `optional = false`**, so the column is nullable and a root category is valid.
- **LAZY**, so touching `getParent()` outside an open session throws. The converter
  never emits the parent (document `02` §4.1), which is why this never surfaces on the
  read path.
- **There is no inverse `children` collection anywhere in the codebase.** Descendants
  are discovered only by querying, which is the entire reason the tree-resolver service
  exists.

### 1.4 Generated members

| Generated member | Behaviour |
|---|---|
| No-argument constructor | All four fields null. Required for ORM materialization. |
| All-arguments constructor | `id`, `code`, `name`, `parent` positionally in declaration order; assigned verbatim with no validation. |
| Builder | Fluent construction. **No field defaults** — unset fields remain null. Terminal build performs no validation. |
| Getters | One per field, returning the stored reference unmodified. |
| Setters | One per field. No validation, no trimming, no null rejection. A setter will store null into a `NOT NULL` column; the failure surfaces later at flush, from the database. |
| Equality / hash | Lombok `@EqualsAndHashCode` with **no exclusions** — see §1.5. |
| String representation | **Hand-written.** Prints `id`, `code`, `name` and only the *immediate* parent's `id`, `code` and `name`, explicitly to avoid walking the whole ancestor chain. |

### 1.5 Equality semantics — load-bearing, and a hazard

Equality covers `id`, `code`, `name` **and `parent`**, and the parent is compared by
its own `equals`, i.e. **transitively up the ancestor chain**. Consequences:

1. A loaded entity and a patched clone are equal exactly when the patch changed
   nothing. The CRUD override exploits this to skip the write (document `02` §3.3),
   which is what implements the no-op patch behaviour.
2. Because the comparison recurses through parents, a category **cycle** (W1,
   BR-PRODCAT-009) can make a single `equals` call recurse until the stack is
   exhausted. `toString` was deliberately written to avoid exactly this; `equals` was
   not.

A rewrite defining equality by identifier alone — otherwise reasonable for a persisted
entity — **breaks the no-op patch optimisation**: every clone shares its source's `id`
and would compare equal, so no patch would ever be written. The all-fields comparison
must be preserved, or the flush logic rewritten to compare field by field explicitly.

### 1.6 Copy constructor `ProductCategory(ProductCategory source)`

**Purpose.** Produce a detached duplicate so the patch service can mutate a clone and
leave the original intact for the comparison in §1.5.

**Behaviour.** Copies `id`, `code` and `name`, then **explicitly assigns
`this.parent = null`.** The javadoc states the omission is deliberate: "this
copy-constructor does NOT include a ProductCategory's relationship to its parent".

**This is the single most consequential line in the domain.** Because the clone has no
parent, the CRUD service must put the parent back after patching — and it does,
unconditionally (document `02` §3.3). Together these two facts are what make
BR-PRODCAT-006 true: **a patch can never detach or re-parent a category.** A rewrite
that "helpfully" copies the parent here changes observable behaviour, because the
restore step would then be a no-op and a `parent` key in the patch map — if one were
ever handled — would take effect.

**`id` is copied.** The clone therefore denotes *the same* database row, so saving it
updates rather than inserts. A rewrite omitting the `id` copy turns every patch into an
insert, which then fails against the unique `code` constraint.

**Error paths.** A null `source` causes a null dereference on the first field read. The
parameter is unchecked — an unguarded programming error, not a handled condition.

### 1.7 Lifecycle callbacks

**None.** No pre-persist, post-load, pre-update, post-update, pre-remove or post-remove
hook; no entity listeners. No auditing, no timestamps, no normalization-on-save, no
cycle check at write time. What the service layer assembles is exactly what is
persisted — there is no safety net below it.

### 1.8 Save / delete overrides and static finders

**None** on the entity. All persistence behaviour comes from the shared-kernel
repository contract; query entry points live on the repository interface (document `02`
§2).

---

## 2. `ProductCategoryPojo` — transport model

### 2.1 Class overview

The external representation: request body for `POST` and `PUT`, element shape for the
list response. Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, with a
class-level `@JsonInclude` (default `ALWAYS`).

Two consequential omissions:

1. **No identifier field.** Callers can neither read nor supply the surrogate key.
   Write resolution is keyed on `code` (BR-PRODCAT-003), which is why that field — not
   the primary key and not `name` — is the operative business key.
2. **No `children` field.** The transport model cannot express a subtree in either
   direction. Clients assemble the tree themselves from repeated filtered reads.

### 2.2 Fields and validation

| Field | Type | Validation | JSON inclusion |
|---|---|---|---|
| `code` | `String` | `@NotBlank` — **no `message` attribute, so the default Bean Validation message applies** | class default (always) |
| `name` | `String` | `@NotBlank` — same | class default (always) |
| `parent` | `ProductCategoryPojo` | **none** | `@JsonInclude(NON_NULL)` |

`@NotBlank` carrying no custom message is a porting detail that must be preserved: the
response body for a validation failure contains the framework default text, and that
text is client-observable contract under G2.

**Effective nesting depth.**
- *Outbound:* **0.** The converter never populates `parent` (document `02` §4.1), and
  `NON_NULL` then omits the field entirely. A category response is always a flat
  `{code, name}`.
- *Inbound:* **1.** Only `parent.code` is ever read. A deeper nested structure is
  accepted by deserialization and silently ignored beyond that one field.

### 2.3 Validation scope limits — three paths escape

1. **`PATCH`** takes a free-form `Map<String, Object>`, not this model, so `@NotBlank`
   does not apply. The patch service's own guards replace it, and they *ignore* blank
   values rather than rejecting them (BR-PRODCAT-019).
2. **`DELETE`** takes no body.
3. **`GET`** takes no body.

On the patch path the only surviving guard is the per-field blank check in the patch
service. There is **no service-layer guard on `name`** at all: a blank or missing name
reaching the create path is caught by `@NotBlank` at the route, but nothing below it
would stop it.

### 2.4 Known quirks

**Parent is write-only in practice.** The field exists, is accepted on input, and is
never produced on output. A client that reads a category and writes it straight back
with `PUT` therefore **silently moves it to the root**, because the absent `parent`
resolves to null. This is the read-modify-write hazard of BR-PRODCAT-008 and is
preserved behaviour, not a defect to fix during the port.

**`@JsonInclude(ALWAYS)` at class level but `NON_NULL` on `parent`.** The narrower
field-level annotation wins for that field only; `code` and `name` are always emitted.
Both columns are `NOT NULL`, so in practice neither is ever null anyway.
