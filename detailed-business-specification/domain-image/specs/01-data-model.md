# Image Registry — Data Model Specification

Domain slug: `image` · Owned table: `images` · Owned entity: `Image`

The domain stores image **metadata only**. No binary content is held, uploaded, resized or
served, and no URL is ever dereferenced. An image record is three strings and a key.

---

## 1. Owned entity: `Image`

| Property | Value |
|---|---|
| Entity class | `Image` |
| Source | `src/main/java/org/trebol/jpa/entities/Image.java` |
| Physical table | `images` |
| Identity strategy | database IDENTITY / AUTO_INCREMENT |
| Serial version UID | `5L` (explicit) |
| Implements | `DBEntity` (shared-kernel marker) |

### 1.1 Field table

Types copied verbatim from `database-documentation/00-global-schema.sql` and cross-checked
against the ORM annotations.

| Field | Column | DB type | App type | Nullable | Default | Constraints | Notes |
|---|---|---|---|---|---|---|---|
| `id` | `image_id` | `bigint(20) NOT NULL AUTO_INCREMENT` | 64-bit integer, nullable object | NOT NULL | generated | PRIMARY KEY | Null until persisted. Never exposed externally (§2.1). |
| `code` | `image_code` | `varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | none | **UNIQUE** (`UK_IMG_CODE`); ORM length rule min 1, max 50 | Business code for the asset. |
| `filename` | `image_filename` | `varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | none | **UNIQUE** (`UK_IMG_FILENAME`); ORM length rule min 1, max 100 | **The domain's operative identity key** — the only field `getExisting` resolves by (BR-IMAGE-001). |
| `url` | `image_url` | `varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL` | string | NOT NULL | none | **UNIQUE** (`UK_IMG_URL`); ORM length rule min 1, max 500 | Absolute URL. Never fetched or validated as a URL (BR-IMAGE-005). |

Column count: 4.

### 1.2 Indexes and the three-way uniqueness problem

| Index | Columns | Unique |
|---|---|---|
| `PRIMARY` | `image_id` | yes |
| `UK_IMG_CODE` | `image_code` | **yes** |
| `UK_IMG_FILENAME` | `image_filename` | **yes** |
| `UK_IMG_URL` | `image_url` | **yes** |

**All three business fields are independently unique.** This is the domain's defining
structural fact and the source of its central defect:

> The service layer's duplicate check resolves by **filename only**
> (`ImagesCrudServiceImpl.getExisting`), while the database enforces **three** independent
> unique constraints. A new image whose filename is free but whose `code` or `url` collides
> with a different row therefore passes the application check and then fails at the
> database with a raw constraint violation — an HTTP 500 — instead of the clean 409 the
> duplicate path is designed to produce.

Recorded as BR-IMAGE-002. A rewrite has two coherent options: widen the existence check to
all three fields (turning those 500s into 409s), or drop two of the three constraints. The
current combination is not coherent.

There are **no non-unique indexes**, so the two substring filters (`codeLike`,
`filenameLike`) cannot use an index for a leading-wildcard pattern and will scan.

### 1.3 ORM / DDL divergences

| # | Divergence | Consequence |
|---|---|---|
| D1 | ORM length rules impose a **minimum of 1** on all three string fields; the DDL imposes no minimum. | The minimum is application-only. It is also redundant with the non-blank transport rules (§2). |
| D2 | The ORM declares uniqueness on all three columns and the DDL agrees — **but the service layer only checks one.** | §1.2, BR-IMAGE-002. |

No other divergence. Unlike several sibling domains, every column here carries an explicit
length rule, and no column has an application-side default.

### 1.4 Abstract base types

None. `Image` extends no abstract base; it implements the `DBEntity` interface, which
contributes no columns.

---

## 2. Transport model: `ImagePojo`

`ImagePojo` (`src/main/java/org/trebol/api/models/ImagePojo.java`) is the external
representation — request body for writes, element shape for the list response.

| Field | Type | Required at transport layer | Maps to |
|---|---|---|---|
| `code` | string | **yes** — must be non-blank | `code` |
| `filename` | string | **yes** — must be non-blank | `filename` |
| `url` | string | **yes** — must be non-blank | `url` |

Serialization: **null fields are omitted** from output. In practice all three are `NOT NULL`
in the database, so a persisted image always serializes all three.

### 2.1 Deliberate asymmetry with the entity

**No `id` field.** The surrogate key is neither accepted nor returned. External identity is
expressed through `filename` for write resolution and through the `id`, `code` and
`filename` *filter* parameters for reads. A caller can filter by `id` but can never learn
one (BR-IMAGE-012).

### 2.2 Validation rules

| Field | Rule | Enforced where |
|---|---|---|
| `code` | present and non-blank | transport layer, on validated request bodies (`POST`, `PUT`) |
| `filename` | present and non-blank | as above |
| `url` | present and non-blank | as above |

**Scope limit.** These fire only on the two routes that validate their body — `POST` and
`PUT`. They do **not** apply to `PATCH`, which accepts a free-form key/value map, nor to
the internal path used by the `product` domain, which passes a model instance directly. On
those paths the only surviving guard is the blank-filename check inside the CRUD service
(BR-IMAGE-003).

**No format validation on `url`.** Nothing checks that the value is a syntactically valid
URL, resolves, or uses a permitted scheme. `"not a url"` is accepted (BR-IMAGE-005).

---

## 3. Shared entity status

`Image` is a **shared entity**. This domain is its `owning_domain`.

Canonical field-level definition: `cross-cutting-documentation/shared-entities.md#image`.

| Consumer domain | Relationship | Direction |
|---|---|---|
| `product` | `product_images.image_id` → `images.image_id` | inbound FK, via a join table owned by `product` |

**Why shared:** the `product` domain both reads images and **creates them**, through
`ProductsCrudServiceImpl.makeProductImageRelationships`. See
`specs/06-integration-points.md` §2 — the behaviour there is unusual and consequential.

### 3.1 Referential integrity notes

- The join table `product_images` is owned by the `product` domain; this domain declares no
  association to it.
- No cascade deletes reach `images` from the product side, and this domain's own delete
  path is reachable but unbounded (BR-IMAGE-013). Deleting an image that a product still
  references will fail on the join-table foreign key, or orphan the relationship, depending
  on the join table's constraint definition — see the `product` domain specification.

---

## 4. Relationships owned by this domain

**None.** `Image` declares zero associations — relationship count is 0. The only
relationship involving `images` is held by the `product` domain's join table.

The domain `depends_on_domains` nothing and is depended on by `product` alone.

---

## 5. Enumerations

**None.** No enum, no constant set, no closed value list. No status field, no type
discriminator, no lifecycle flag — hence no state machine specification
(`specs/00-plan.md` §4).

The only declared constant in the domain's own files is the entity's
`serialVersionUID = 5L`.

---

## 6. Type/feature matrix

Not applicable. `Image` has no subtypes and no type-dependent behaviour.
