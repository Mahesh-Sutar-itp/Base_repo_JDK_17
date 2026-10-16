# 01 — Product Entities and Transport Model

Source files covered:

| File | Role |
|---|---|
| `src/main/java/org/trebol/jpa/entities/Product.java` | persisted entity |
| `src/main/java/org/trebol/jpa/entities/ProductImage.java` | persisted entity (join) |
| `src/main/java/org/trebol/api/models/ProductPojo.java` | transport model |

`Param` and `CompanyDetailsPojo` are specified in the appendix at `00-domain-index.md` §6.
This document specifies **behaviour**; column layout is restated because no
porting-depth data-model document existed for this domain.

---

## 1. `Product` — persisted entity

### 1.1 Class overview

A flat sellable-goods record with one optional classification reference. Lombok
`@Builder @NoArgsConstructor @AllArgsConstructor @Getter @Setter @EqualsAndHashCode
@ToString`; implements `org.trebol.jpa.DBEntity`; `serialVersionUID = 10L`.

A plain mutable data holder: no validation logic, no computed properties, no business
methods, no lifecycle callbacks. All behaviour lives in the service layer (document
`02`).

### 1.2 Table and columns

`@Table(name = "products", indexes = { @Index(columnList = "product_name") })`

| Field | Column | Type | Nullable | Unique | Validation |
|---|---|---|---|---|---|
| `id` | `product_id` | `Long`, `IDENTITY` generated | no | primary key | — |
| `name` | `product_name` | `String` | no | **yes** | `@Size(max = 200)` |
| `barcode` | **`product_code`** | `String` | no | **yes** | `@Size(max = 50)` |
| `description` | `product_description` | `String` | **yes** | no | `@Size(max = 4000)` |
| `price` | `product_price` | **primitive `int`** | no | no | **none** |
| `stockCurrent` | `product_stock_current` | **primitive `int`** | no | no | **none** |
| `stockCritical` | `product_stock_critical` | **primitive `int`** | no | no | **none** |

**The field name and the column name disagree** for the barcode: the Java field is
`barcode`, the column is `product_code` (BR-PRODUCT-004). Both names are contract —
the field name appears in JSON and in filter parameters, the column name in SQL and in
the schema script. A rewrite must keep both, not harmonise them.

**Two global unique constraints**, on `product_name` and `product_code`
(BR-PRODUCT-001). Only the barcode is pre-checked by the service layer
(document `02` §4.4), so a **name** collision bypasses the guard and fails at the
database as a 500 — the same defect class as D7 in the `image` domain, and **not** in
the D7 fix scope.

**The index on `product_name`** exists because `nameLike` search is the storefront's
main query. It ports with the DDL.

**Price and stock are primitive `int`.** Consequences a rewrite must preserve:
- **No decimal places anywhere in the system** (BR-PRODUCT-008). The price is a whole
  number of currency units. Introducing a decimal type changes every total the order
  domain computes.
- **No lower bound** (BR-PRODUCT-007). Negative values are accepted at every layer.
- Being primitive, an absent value becomes **0**, not null — which is how the converter's
  conditional stock assignment (document `02` §5.2) silently produces zero stock.

### 1.3 The `productCategory` relationship

```
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "product_category_id", referencedColumnName = "product_category_id")
```

- **No cascade, no `orphanRemoval`, optional (nullable).** Classification is optional
  (BR-PRODUCT-010); a product may have no category.
- Saving a product never creates or updates a category. The converter resolves an
  existing one by code or leaves the product uncategorised.
- The FK is what makes products **block category deletion** (BR-PRODUCT-020): the
  orphaning query that would make deletion safe (`orphanizeByCategories`) exists on the
  repository but has no caller.
- **LAZY**, so `getProductCategory()` outside an open session throws. The converter
  touches it inside a `@Transactional` service, so this never surfaces today.

### 1.4 Generated members

| Generated member | Behaviour |
|---|---|
| No-argument constructor | Reference fields null, the three `int`s **0**. Required for ORM materialization. |
| All-arguments constructor | `id`, `name`, `barcode`, `description`, `price`, `stockCurrent`, `stockCritical`, `productCategory` positionally in declaration order; assigned verbatim with no validation. |
| Builder | Fluent construction. **No field defaults** — unset references stay null, unset `int`s are 0. Terminal build performs no validation. |
| Getters | One per field, returning the stored value unmodified. |
| Setters | One per field. No validation, no trimming, no null rejection, no range check. A setter will store null into a `NOT NULL` column; the failure surfaces at flush, from the database. |
| Equality / hash | Lombok, **no exclusions** — see §1.6. |
| String representation | Lombok `@ToString`, all fields. Includes the lazy `productCategory`, so logging a product can trigger a load or a lazy-initialization failure. Benign for privacy: product data is not personal data. |

### 1.5 Copy constructor `Product(Product source)`

**Purpose.** Produce a detached duplicate so the patch service can mutate a clone and
leave the original intact for the comparison in §1.6.

**Behaviour.** Copies `id`, `name`, `barcode`, `description`, `price`, `stockCurrent`
and `stockCritical`, then **explicitly sets `this.productCategory = null`.** The javadoc
records the omission: it "does not include a Product's relationship to a
ProductCategory".

**This is the mechanism behind BR-PRODUCT-017.** Because the clone has no category, the
patch service puts it straight back (document `02` §7.1), so **a patch can never
declassify a product**. Reclassification is possible only through full `PUT`
replacement (BR-PRODUCT-018). A rewrite that copies the category here changes observable
behaviour, because the restore step would become a no-op.

**`id` is copied.** The clone denotes *the same* row, so saving it updates rather than
inserts. Omitting the `id` copy turns every patch into an insert, which then fails
against both unique constraints.

**Error paths.** A null `source` causes a null dereference on the first field read —
unguarded programming error, not a handled condition.

### 1.6 Equality semantics — load-bearing

Equality covers **all eight fields including `id` and `productCategory`**. Consequences:

1. A loaded entity and a patched clone are equal exactly when the patch changed nothing.
   `CrudGenericService.flushPartialChanges` compares them and, when equal, returns the
   original **without any database write**.
2. Because `productCategory` participates, the comparison can **trigger a lazy load** of
   the category on an otherwise read-only no-op patch.

A rewrite defining equality by identifier alone — otherwise reasonable for a persisted
entity — **breaks the no-op optimisation**: every clone shares its source's `id` and
would compare equal, so no patch would ever be written. Preserve the all-fields
comparison, or rewrite the flush logic to compare explicitly.

### 1.7 Lifecycle callbacks, overrides, finders

**None of any kind.** No `@PrePersist`, `@PreUpdate`, `@PostLoad`, `@PreRemove`, no
entity listeners, no save/delete overrides, no static finders. There is no auditing, no
timestamp, no stock adjustment hook and no price normalization. What the service layer
assembles is exactly what is persisted — there is no safety net below it.

---

## 2. `ProductImage` — persisted join entity

### 2.1 Class overview

The link row between a product and an image. `@Entity @Table(name = "product_images")`,
same Lombok set, implements `DBEntity`, `serialVersionUID = 12L`.

**It is owned by the `product` domain, not by `image`.** That ownership is what lets the
whole catalogue move as one unit, and it is why the image-link rebuild lives in
`ProductsCrudServiceImpl`.

### 2.2 Columns and relationships

| Field | Column | Declaration |
|---|---|---|
| `id` | `product_image_id` | `@Id @GeneratedValue(strategy = IDENTITY) @Column(nullable = false)` |
| `image` | `image_id` | `@ManyToOne(optional = false, fetch = LAZY)` + `@JoinColumn(referencedColumnName = "image_id", updatable = false)` |
| `product` | `product_id` | `@ManyToOne(optional = false, fetch = LAZY)` + `@JoinColumn(referencedColumnName = "product_id", updatable = false)` |

- **Both relations are `optional = false`** — a link row must have both ends.
- **Both are `updatable = false`** — a link cannot be re-pointed. This is precisely why
  `PUT` deletes and rebuilds the whole set instead of mutating rows (W3,
  BR-PRODUCT-011).
- **No cascade and no `orphanRemoval` on either side.** Deleting a product does not
  delete its links, so the FK blocks the delete (BR-PRODUCT-028, W11).
- **No unique constraint on `(product_id, image_id)`**, so the same image can be linked
  to the same product twice (BR-PRODUCT-015). The read side masks it with `distinct()`
  (document `02` §5.3).

### 2.3 Copy constructor `ProductImage(ProductImage source)`

Copies **only `id`**, then sets `this.image = null` and `this.product = null`. The
javadoc states it "does NOT include a ProductImage's relationships".

The result is an id-only shell: useless for persistence, since both relations are
`optional = false`. **No production code calls it** — there is no patch service for this
entity. It exists for contract symmetry with the other entities and is a trap for a
rewrite that assumes copy constructors are usable.

### 2.4 Generated members and callbacks

Builder, getters, setters and all-args constructor as generated; equality covers `id`
and both relations, so comparing two link rows can trigger two lazy loads.
**No lifecycle callbacks.**

---

## 3. `ProductPojo` — transport model

### 3.1 Class overview

The external representation: request body for `POST` and `PUT`, element shape for list
and single reads. `@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(NON_NULL)` at class level.

**No `id` field at all.** The business key carried over the wire is `barcode`
(BR-PRODUCT-002) — which is also the key the Gate A `ProductLookupContract` publishes
to the order domain, making it the most load-bearing identifier in the conversion.

### 3.2 Fields, validation and serialization

| Field | Type | Validation | JSON inclusion |
|---|---|---|---|
| `name` | `String` | `@NotBlank` — **no `message` attribute, so the framework default applies** | class default `NON_NULL` |
| `barcode` | `String` | `@NotBlank` — same | `NON_NULL` |
| `description` | `String` | **none** | `@JsonInclude(NON_EMPTY)` — an empty string is omitted too |
| `price` | `Integer` | `@NotNull` — no message, **no `@Min` or `@Positive`** | `NON_NULL` |
| `currentStock` | `Integer` | **none** | `NON_NULL` |
| `criticalStock` | `Integer` | **none** | `NON_NULL` |
| `category` | `ProductCategoryPojo` | **none** | `NON_NULL` |
| `images` | `Collection<ImagePojo>` | **none — notably no cascading `@Valid`** | `NON_NULL` |

**Three naming divergences from the entity, all contract:**
`stockCurrent` → `currentStock`, `stockCritical` → `criticalStock`,
`productCategory` → `category`. The converter performs the mapping (document `02` §5).

**`@NotBlank`/`@NotNull` carry no custom messages**, so a validation failure returns the
framework default text — client-observable under G2.

**The three missing validations are each a rule:**
- No `@Min`/`@Positive` on `price`, `currentStock` or `criticalStock` → negative values
  are accepted (BR-PRODUCT-007, W6).
- No `@Size` mirroring the entity's 200/50/4000 limits → over-length input passes the
  route and fails later at the entity/database layer as a 500.
- **No cascading `@Valid` on `images`** → the nested `ImagePojo` elements are **never
  bean-validated**, which is one half of why the image write path can be handed a blank
  filename at all (W1, W2).

**Mandatory on create:** `name`, `barcode`, `price`. **Optional:** everything else,
including both stock levels (BR-PRODUCT-006).

### 3.3 Validation scope limits — three paths escape

1. **`PATCH`** takes a free-form `Map<String, Object>`, not this model, so no annotation
   applies. The patch service's own guards replace them, and they are asymmetric: the
   three string fields ignore blanks, the three numeric fields are cast unconditionally
   (document `02` §7.1, W9).
2. **The internal image path** receives `ImagePojo` instances straight from this model's
   `images` collection and passes them to `getExisting` with no bean validation at all.
3. **`DELETE`** and **`GET`** take no body.

### 3.4 Images are asymmetric by design

| Operation | Images |
|---|---|
| `POST` | Accepted and linked, via the bypassing write path (document `02` §6). |
| `PUT` | **All existing links deleted unconditionally**, then rebuilt from the payload — so an omitted `images` field strips every image (BR-PRODUCT-011, W3). |
| `PATCH` | **Not patchable.** The patch service handles no `images` key; links are left untouched (BR-PRODUCT-012). |
| `GET` single (`readOne`) | Populated, via `deepFindProductImagesByProductId` (BR-PRODUCT-016). |
| `GET` list (`readMany`) | **Never populated** — the field is absent from every element of a list response (BR-PRODUCT-016). |

A client that lists products, picks one and `PUT`s it back **destroys that product's
images**, because the list response never carried them. This is the sharpest
read-modify-write hazard in the catalogue and is preserved behaviour.

### 3.5 Known quirks

**`category` is read-write but shallow.** On output the converter emits a flat
`{code, name}` (the category converter never emits a parent). On input only
`category.code` is read, and an unknown code is **silently ignored** — the product is
created uncategorised with HTTP 201 (BR-PRODUCT-010, W5).

**A `PUT` built from a read-back product loses nothing except images and any field the
reader dropped** — but because `PUT` is a full replacement that nulls absent fields
(W4), any field the client omits is wiped: `description` becomes null, `currentStock`
and `criticalStock` become 0.

**`NON_EMPTY` on `description` only.** The other string fields use the class-level
`NON_NULL`. An empty description is therefore indistinguishable from an absent one in a
response — but an empty description *can* be stored, because the column is nullable and
the patch service's blank guard only refuses to *apply* a blank, it never clears
(BR-PRODUCT-024).
