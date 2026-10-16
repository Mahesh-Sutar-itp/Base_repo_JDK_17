# Validations — Product Catalog (`product`)

## Transport-model validation

| Field | Constraint | Violation outcome |
|---|---|---|
| `name` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `barcode` | `@NotBlank` | validation envelope, code `REJECTED_02` |
| `price` | `@NotNull` | validation envelope, code `REJECTED_02` |
| `description` | none | accepted as-is |
| `currentStock` | **none** | accepted, including negative |
| `criticalStock` | **none** | accepted, including negative |
| `category` | **no nested validation** | a submitted category is never validated |
| `images` | **no nested validation** | submitted images are never validated |

Source: `api/models/ProductPojo.java`. Applied on `POST` and `PUT`; `PATCH` takes
a raw change map and is uncovered.

Two notable asymmetries: `price` uses the non-null annotation rather than a
positivity constraint, and the two stock fields — both backed by not-null columns
— carry no transport constraint at all.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `product_code` | not null, unique, max 50 |
| `product_name` | not null, unique, max 200, indexed |
| `product_description` | nullable, max 4000 |
| `product_price` | not null, `int(11)` — no range check |
| `product_stock_current` | not null, `int(11)` — no range check |
| `product_stock_critical` | not null, `int(11)` — no range check |
| `product_category_id` | nullable, foreign key, no cascade |
| `product_images.image_id` | not null, foreign key, `updatable=false` |
| `product_images.product_id` | not null, foreign key, `updatable=false` |

No uniqueness constraint exists on the image-link pair.

## Service-layer validation

| Check | Component | Failure |
|---|---|---|
| barcode present | `ProductsCrudServiceImpl.getExisting` | bad input, `Invalid product barcode` |
| product must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |
| selecting parameters present on update/patch | `DataCrudGenericController` | bad input, `Missing request params` |
| at most one row selected on update/patch | `CrudGenericService` | runtime failure, `Cannot update more than one item at a time` |
| at least one row selected on update/patch | `DataCrudGenericController` | not found, `No element was found to update` |
| at least one row selected on delete | `CrudGenericService.delete` | not found, `Requested item(s) not found` |

## Validation that is deliberately suppressed

| Condition | Handling |
|---|---|
| image with a blank filename during linking | bad-input condition **caught and logged at debug level**, iteration continues |
| image filename valid but unknown | placeholder image built from the submitted fields; no validation of those fields |
| product not resolvable when adding to a product list | silently skipped by the contents controller |

These three suppressions mean a client can receive a success response for a
request that was partially ignored.

## Cross-domain validation on this domain's data

| Check | Where | Failure |
|---|---|---|
| order-line barcode present | `OrdersConverterServiceImpl.convertDetailToNewEntity` | bad input, `Product barcode must be valid` |
| order-line barcode resolvable | same | bad input, `Unexisting product in sell details` |
| order-line unit value | same | **overridden** with the product's current price — the submitted value is ignored |
| order-line stock availability | — | **no check exists** |

## Structural invariants enforced by behaviour rather than constraints

| Invariant | How it is maintained |
|---|---|
| a patch cannot declassify a product | the patch service re-attaches the stored category |
| image links always reflect the last full replacement | the replace override deletes and rebuilds them wholesale |

## Gaps

- **No stock validation of any kind.** Negative stock is storable, and an
  out-of-stock product can be ordered and paid for (BR-PRODUCT-022). This is the
  largest functional gap in the system.
- **No price validation.** A zero or negative price is accepted and will flow
  through the order tax calculation unchanged.
- **Nested category and image objects are unvalidated**, so a replace body can
  carry a category with a blank code and name, or images with blank fields that
  then become placeholder rows.
- **Partial success is reported as success.** Dropped images and skipped list
  additions produce no client-visible signal.
- **Service-level existence check and database constraints disagree**: only the
  barcode is checked in application code, so name collisions surface as
  infrastructure errors.
- **No pre-delete cleanup of image links**, making any product with images
  undeletable through the API even though this domain owns both tables.
- **`PATCH` is uncovered by transport validation** and sets the three numeric
  fields without null guards, so an explicit null produces a null-dereference
  rather than a clean rejection.
- **Read route is unauthenticated** and exposes stock levels publicly, while
  `products:read` exists, is granted to three roles and is never checked.
