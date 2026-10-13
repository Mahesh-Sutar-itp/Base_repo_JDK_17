# Domain: Product Catalog (`product`)

## Responsibility

Owns the sellable item catalogue: barcode, name, description, price, current
stock level and critical stock threshold, plus the product-to-image links. It is
the most behaviourally complex of the catalogue domains because its create, read
and replace paths are all overridden to manage the image-link join table
alongside the product row.

## Bounded context summary

Catalogue context. Single aggregate root (`Product`) that transactionally governs
its image-link rows (`ProductImage`) — those rows are deleted and rebuilt
wholesale whenever a product is replaced, which is the definition of an aggregate
boundary. Category classification is a reference across a boundary (optional,
no cascade) and therefore stays in `product-category`.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `products` | `Product` | `jpa/entities/Product.java` |
| `product_images` | `ProductImage` | `jpa/entities/ProductImage.java` |

### `Product` fields

| Field | Column | Nullable | Unique | Constraint |
|---|---|---|---|---|
| `id` | `product_id` | no | — | — |
| `name` | `product_name` | no | yes | `@Size(max=200)`, indexed |
| `barcode` | **`product_code`** | no | yes | `@Size(max=50)` |
| `description` | `product_description` | yes | — | `@Size(max=4000)` |
| `price` | `product_price` | no | — | `int` — whole currency units |
| `stockCurrent` | `product_stock_current` | no | — | `int` |
| `stockCritical` | `product_stock_critical` | no | — | `int` |
| `productCategory` | `product_category_id` | yes | — | lazy, optional, no cascade |

**Field-to-column mismatch worth noting:** the field is called `barcode` but the
column is `product_code`. Filter and sort keys use `barcode`; the DDL and the
unique key `UK_PRODUCT_CODE` use `product_code`.

### `ProductImage` fields

Pure join entity with its own surrogate key: `image_id` and `product_id`, both
mandatory, lazy and `updatable=false`. The table has **no** uniqueness constraint
on the pair, so the same image can be linked to the same product twice.

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataProductsController` | `api/controllers/DataProductsController.java` |
| crud service | `ProductsCrudServiceImpl` | `jpa/services/crud/impl/ProductsCrudServiceImpl.java` |
| converter | `ProductsConverterServiceImpl` | `jpa/services/conversion/impl/ProductsConverterServiceImpl.java` |
| patch service | `ProductsPatchServiceImpl` | `jpa/services/patch/impl/ProductsPatchServiceImpl.java` |
| predicate service | `ProductsPredicateServiceImpl` | `jpa/services/predicates/impl/ProductsPredicateServiceImpl.java` |
| sort spec | `ProductsSortSpec` | `jpa/sortspecs/ProductsSortSpec.java` |
| repository | `ProductsRepository` | `jpa/repositories/ProductsRepository.java` |
| repository | `ProductImagesRepository` | `jpa/repositories/ProductImagesRepository.java` |
| transport model | `ProductPojo` | `api/models/ProductPojo.java` |

`ProductsCrudServiceImpl` is the largest crud implementation in the codebase and
the only one that overrides four extension points (`create`, the
identifier-based `update`, `readOne` and `getExisting`).

## Dependencies

- **Depends on**: `image` (resolves images by filename when linking),
  `product-category` (consumes the tree resolver to expand category filters into
  whole branches).
- **Depended on by**: `order` (order lines resolve products by barcode),
  `product-list` (list items reference products).

## Shared entity status

`Product` is a **shared entity**: owned by `product`, referenced by `order`
through `order_details.product_id` and by `product-list` through
`product_list_items.product_id`. `Image` and `ProductCategory` are shared
entities used here. `ProductImage` is **not** shared.
See `cross-cutting-documentation/shared-entities.md`.

## Exposure

Full surface at `/data/products` — read, create, replace, patch and delete.
`GET` carries **no** authentication or authority requirement (storefront data);
the write verbs require `products:create`, `products:update` and
`products:delete`. The seeded `products:read` authority exists and is granted to
Administrator, Manager and Salesperson but is never checked.
