# 01 — Image Entity and Transport Model

Source files covered:

| File | LOC | Role |
|---|---|---|
| `src/main/java/org/trebol/jpa/entities/Image.java` | 73 | persisted entity |
| `src/main/java/org/trebol/api/models/ImagePojo.java` | 45 | transport model |

Column definitions are in `specs/01-data-model.md`. This document specifies **behaviour**.

---

## 1. `Image` — persisted entity

### 1.1 Class overview

A flat record of image asset metadata: a business `code`, a `filename`, and an absolute
`url`, plus a database-generated surrogate key. It holds no associations and no binary
content.

The class is a plain mutable data holder with no validation logic, no computed properties,
no lifecycle callbacks and no business methods. All behaviour acting on an image lives in
the service layer (document `02`). A reimplementation should treat it as a value-carrying
record with generated identity.

It is serializable via the shared-kernel `DBEntity` contract, with an explicit serial
version identifier of `5L` — relevant only if binary compatibility with existing
serialized instances must be preserved.

### 1.2 Constants

| Constant | Value | Visibility | Purpose |
|---|---|---|---|
| `serialVersionUID` | `5L` | private static | Serialization marker. Not business data. Read by nothing. |

No other constants.

### 1.3 Generated members

| Generated member | Behaviour |
|---|---|
| No-argument constructor | All four fields null. Required for ORM materialization. |
| All-arguments constructor | Takes `id`, `code`, `filename`, `url` positionally in declaration order; assigns verbatim with no validation. |
| Builder | Fluent construction. **No field defaults** — unset fields remain null. Terminal build performs no validation. |
| Getters | One per field, returning the stored reference unmodified. |
| Setters | One per field. No validation, no trimming, no null rejection. A setter will store null into a `NOT NULL` column; the failure surfaces later at flush, from the database. |
| Equality / hash | Computed over **all four fields, including `id`**. Load-bearing — see §1.5. |
| String representation | Includes all four values. Benign here: image metadata is not personal data, unlike several sibling domains. |

### 1.4 Copy constructor `Image(Image source)`

**Purpose.** Produce a detached duplicate. This is what makes patching non-destructive: the
patch service clones the loaded entity, mutates the clone, and leaves the original intact
so the "did anything change?" comparison in §1.5 is meaningful.

**Parameters.** `source` — required.

**Behaviour.** Assigns all four fields one by one: `id`, `code`, `filename`, `url`.
Field-for-field and exhaustive; no field is skipped.

**`id` is copied.** The clone therefore denotes *the same* database row. Saving it updates
that row rather than inserting a new one. A rewrite that omits the `id` copy turns every
patch into an insert, which will then fail against all three unique constraints.

**Return / side effects.** A new in-memory instance. No persistence interaction; `source`
is not mutated.

**Error paths.** A null `source` causes a null dereference on the first field read. The
parameter is unchecked — an unguarded programming error, not a handled condition.

### 1.5 Equality semantics and their effect on writes

Equality covers all four fields including the identifier. Consequences:

1. A loaded entity and a patched clone are equal exactly when the patch changed nothing.
2. The shared-kernel CRUD base exploits this: its partial-change flush compares original to
   clone and, when equal, returns the original **without any database write** (document
   `02` §3.5). This implements BR-IMAGE-009.

A rewrite defining equality by identifier alone — an otherwise reasonable choice for a
persisted entity — **breaks this**: every clone would compare equal to its source because
they share an `id`, so no patch would ever be written. Defining it by `filename` alone
would suppress any patch that does not change the filename. The all-fields comparison must
be preserved, or the flush logic rewritten to compare explicitly.

### 1.6 Lifecycle callbacks

**None.** No pre-persist, post-load, pre-update, post-update, pre-remove or post-remove
hook. No auditing, no timestamps, no normalization-on-save, no AOP-intercepted handler
chain. What the service layer assembles is exactly what is persisted.

### 1.7 Save / delete overrides and static finders

**None** on the entity. All persistence behaviour comes from the shared-kernel repository
contract; query entry points live on the repository interface (document `02` §2).

---

## 2. `ImagePojo` — transport model

### 2.1 Class overview

The external representation: request body for `POST` and `PUT`, element shape for the list
response. A flat mutable data holder with three fields and no behaviour.

Its consequential property is an omission: **no identifier field**. Callers can neither
read nor supply the surrogate key. Write resolution is keyed on `filename`
(BR-IMAGE-001), which is why that field — not the primary key, and not `code` — is the
domain's operative business key.

### 2.2 Constants

None.

### 2.3 Generated members

| Generated member | Behaviour |
|---|---|
| No-argument constructor | All three fields null. Required for request-body deserialization. |
| All-arguments constructor | `code`, `filename`, `url` positionally in declaration order, assigned verbatim. |
| Builder | Fluent construction, no defaults. Used by the converter in both directions (document `02` §4). |
| Getters / setters | One pair per field. Direct access, no validation or transformation. |
| Equality / hash / string representation | Over all three fields. |

### 2.4 Validation rules

| Field | Rule | Enforced where |
|---|---|---|
| `code` | present and not blank | transport layer, on `POST` and `PUT` only |
| `filename` | present and not blank | as above |
| `url` | present and not blank | as above |

**Scope limit — three paths escape these rules:**

1. **`PATCH`** takes a free-form key/value map, not this model. Its per-field blank guards
   live in the patch service instead (document `02` §5.1).
2. **The internal path** from the `product` domain passes a model instance directly to
   `getExisting`, bypassing route-layer validation entirely
   (`specs/06-integration-points.md` §2).
3. **`DELETE`** takes no body.

On paths 1 and 2 the only surviving guard is the blank-**filename** check in the CRUD
service. A model with a valid filename but a blank `code` or `url` passes it and fails at
the database. BR-IMAGE-003.

**No URL format validation.** Nothing checks scheme, syntax, reachability or host
allow-listing. The field is an opaque string of up to 500 characters. BR-IMAGE-005.

### 2.5 Known quirks

**Serialization inclusion differs from the sibling `shipper` domain.** This model omits
null fields from output; `ShipperPojo` in the sibling domain is annotated to always
include them. The difference is invisible here because all three columns are `NOT NULL`, so
a persisted image never has a null field to omit — but a rewrite unifying serialization
policy across domains should know the two are not currently the same.

**No field corresponds to the asset itself.** The model describes where an image is
supposed to live without any guarantee that it does. There is no checksum, size, MIME type,
dimension, upload timestamp or existence flag. BR-IMAGE-005 and
`reference/integrations.md`.
