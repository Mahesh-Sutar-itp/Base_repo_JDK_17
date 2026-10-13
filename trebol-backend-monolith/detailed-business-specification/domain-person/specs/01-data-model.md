# Person Registry — Data Model Specification

Domain slug: `person` · Owned table: `people` · Owned entity: `Person`

This domain owns exactly one physical table. It has no foreign keys of its own —
every relationship involving `people` is held by *another* domain pointing inward.

---

## 1. Owned entity: `Person`

| Property | Value |
|---|---|
| Entity class | `Person` |
| Source | `src/main/java/org/trebol/jpa/entities/Person.java` |
| Physical table | `people` |
| Identity strategy | database IDENTITY / AUTO_INCREMENT |
| Serial version UID | `9L` (explicit, declared constant) |
| Implements | `DBEntity` (shared-kernel marker, see §5) |

### 1.1 Field table

Column types below are copied verbatim from `database-documentation/00-global-schema.sql`
(authoritative physical DDL) and cross-checked against the ORM annotations. Where the
two disagree, both are stated — do **not** normalize these.

| Field | Column | DB type | App type | Nullable | Default | Constraints | Notes |
|---|---|---|---|---|---|---|---|
| `id` | `person_id` | `bigint(20) NOT NULL AUTO_INCREMENT` | 64-bit integer, nullable object | NOT NULL | generated | PRIMARY KEY | Assigned by the database on insert. Null on a not-yet-persisted instance. |
| `firstName` | `person_first_name` | `varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | none | ORM length rule: min 1, max 200 | Indexed (`IDX_PERSON_FIRSTNAME`). |
| `lastName` | `person_last_name` | `varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | none | ORM length rule: min 1, max 200 | Indexed (`IDX_PERSON_LASTNAME`). |
| `idNumber` | `person_id_number` | `varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | none | **UNIQUE** (`UK_PERSON_ID_NO`); ORM length rule: min 1, max 20 | National identification number. The domain's only business key. Indexed twice — unique key *and* a separate non-unique index (`IDX_PERSON_ID_NO`), which is redundant but present in the DDL. |
| `email` | `person_email` | `varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | none | ORM length rule: min 5, max 100 | Indexed (`IDX_PERSON_EMAIL`). **No format validation anywhere** — see BR-PERSON-005. |
| `phone1` | `person_phone1` | `varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | `""` (empty string, set by the entity builder) | none | Default applied in application code, **not** by a DDL `DEFAULT` clause. See §1.3. |
| `phone2` | `person_phone2` | `varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | `""` (empty string, set by the entity builder) | none | Same as `phone1`. |

Column count: 7.

### 1.2 Indexes

| Index | Columns | Unique | Declared in |
|---|---|---|---|
| `PRIMARY` | `person_id` | yes | DDL + ORM `@Id` |
| `UK_PERSON_ID_NO` | `person_id_number` | **yes** | DDL + ORM column uniqueness flag |
| `IDX_PERSON_ID_NO` | `person_id_number` | no | DDL + ORM index declaration |
| `IDX_PERSON_FIRSTNAME` | `person_first_name` | no | DDL + ORM index declaration |
| `IDX_PERSON_LASTNAME` | `person_last_name` | no | DDL + ORM index declaration |
| `IDX_PERSON_EMAIL` | `person_email` | no | DDL + ORM index declaration |

A reimplementation MUST keep `person_id_number` unique at the database level. It is the
only structural guarantee behind the domain's central invariant, and three other domains
depend on it.

### 1.3 Divergences between ORM declarations and physical DDL

These are real, and a rewrite must decide deliberately rather than inherit by accident.

| # | Divergence | Consequence |
|---|---|---|
| D1 | `phone1` / `phone2` are `varchar(255)` in DDL but carry **no** length rule in the ORM, while all four other string fields do carry one. | Phone input is length-checked only by the database's 255-char ceiling. |
| D2 | `phone1` / `phone2` are `NOT NULL` in DDL with no DDL-level `DEFAULT`. The empty-string default exists **only** in the entity builder. | Any write path that bypasses the builder and leaves a phone null fails at the database, not with a readable validation error. The transport model permits null phones (§2), so this path is reachable. |
| D3 | The ORM length rule on `email` is min 5 / max 100; the DDL is `varchar(100)` with no minimum. | The 5-character minimum is application-only. |
| D4 | `person_id_number` carries both a unique key and a redundant non-unique index. | Harmless, but do not reproduce the duplicate index without reason. |

### 1.4 Abstract base types

None. `Person` extends no abstract base class. It implements the `DBEntity` interface
(§5), which contributes no columns.

---

## 2. Transport model: `PersonPojo`

`PersonPojo` (`src/main/java/org/trebol/api/models/PersonPojo.java`) is the external
representation. It is **not** a persisted type and owns no table, but its shape is part
of the domain contract.

| Field | Type | Required at transport layer | Maps to entity field |
|---|---|---|---|
| `firstName` | string | **yes** — must be non-blank | `firstName` |
| `lastName` | string | **yes** — must be non-blank | `lastName` |
| `idNumber` | string | **yes** — must be non-blank | `idNumber` |
| `email` | string | **yes** — must be non-blank | `email` |
| `phone1` | string | no | `phone1` |
| `phone2` | string | no | `phone2` |

Serialization rule: **null fields are omitted** from the serialized output rather than
emitted as explicit nulls.

### 2.1 Deliberate asymmetries with the entity

1. **No `id`.** The transport model has no identifier field at all. External callers can
   never see or set a person's surrogate key. Identity is expressed exclusively through
   `idNumber`. This is a deliberate design property of the domain, not an omission.
2. **Phone format validation is present but disabled.** Both phone fields carry a
   commented-out format rule in the source:
   `^(((\(\+?[0-9]{3}\))|(\+?[0-9]{3})) ?)?[0-9]{3,4}[ -]?[0-9]{4}$`
   It is inert. Phone values are accepted in any shape. A rewrite that enables this
   pattern changes observable behaviour and will reject data the current system accepts —
   see BR-PERSON-006.
3. **Non-blank rules are transport-layer only.** They are enforced when the model arrives
   as a request body on a validated route. They do **not** apply to the internal creation
   path used by other domains, which passes model instances directly. See BR-PERSON-004.

---

## 3. Shared entity status

`Person` is a **shared entity**. This domain is its `owning_domain`.

Canonical field-level definition: `cross-cutting-documentation/shared-entities.md#person`
— the table above is the owning domain's authoritative copy; consumers MUST read the
canonical section rather than redefining fields.

| Consumer domain | Relationship | Direction |
|---|---|---|
| `user` | `app_users.person_id` → `people.person_id` | inbound FK |
| `customer` | `customers.person_id` → `people.person_id` | inbound FK |
| `salesperson` | `salespeople.person_id` → `people.person_id` (constraint `FK_SPERSON_PERSON_ID`) | inbound FK |

All three consumers **write** person rows — `user` during registration and profile
editing, `customer` and `salesperson` through cascading associations. This domain
therefore does not have exclusive write authority over its own table, which is the single
most important fact for anyone splitting it out as a service. See
`specs/05-api-contract.md` §4 and `00-domain-index.md` §Boundary warnings.

### 3.1 Referential integrity notes

- `salespeople.person_id` is `NOT NULL` and carries a declared FK constraint.
- No consumer cascades deletion, and no code path in any domain deletes a person row.
  Orphan accumulation is the steady-state behaviour, not an error condition.

---

## 4. Relationships owned by this domain

**None.** `Person` declares zero associations — relationship count is 0. Every
relationship touching `people` is declared on the far side, in the consumer domain.

This is why the domain is a clean leaf in the dependency graph: it depends on no other
domain (`depends_on_domains` is empty) while three depend on it.

---

## 5. Shared-kernel types referenced

Documented here for completeness of the rewrite contract; these are **not** owned by this
domain and are specified in `domain-decomposition-overview/shared-kernel.md`.

| Type | Role in this domain | Contributes columns |
|---|---|---|
| `DBEntity` | Marker interface requiring a nullable 64-bit `id` getter/setter pair, plus serializability. Enables the generic repository and generic CRUD service to treat `Person` uniformly. | no |
| `Repository<E>` | Generic persistence contract: standard CRUD/paging plus predicate-based query execution. `PeopleRepository` extends it. | no |

---

## 6. Enumerations

**None.** This domain defines no enum, no constant set, and no closed list of valid
values. There is no status field, no type discriminator, and no lifecycle flag — which is
also why no state machine specification exists (see `specs/00-plan.md` §Omitted specs).

The only declared constant anywhere in the domain's own files is the entity's
`serialVersionUID = 9L`.

---

## 7. Type/feature matrix

Not applicable. `Person` has no subtypes and no type-dependent behaviour.
