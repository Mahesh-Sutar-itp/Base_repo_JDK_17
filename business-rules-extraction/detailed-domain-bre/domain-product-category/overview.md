# Domain: Product Category Tree (`product-category`)

## Responsibility

Owns the self-referencing category hierarchy used to classify products. A
category has a unique code, a display name and an optional parent. The domain's
distinguishing behaviour is **recursive tree traversal**: resolving all
descendant categories of a given root so that a product search by category can
include everything beneath it.

## Bounded context summary

Hierarchical classification context. Single aggregate root (`ProductCategory`)
whose parent/child relationship it governs transactionally. Kept separate from
`product` because the tree has its own invariants (sibling-name uniqueness,
recursion depth limit, parent preservation on patch) and its own traversal
service that `product` merely consumes.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `product_categories` | `ProductCategory` | `jpa/entities/ProductCategory.java` |

### `ProductCategory` fields

| Field | Column | Nullable | Unique | Size constraint |
|---|---|---|---|---|
| `id` | `product_category_id` | no | — | — |
| `code` | `product_category_code` | no | yes | `@Size(min=1,max=50)` |
| `name` | `product_category_name` | no | no (composite) | `@Size(min=1,max=100)` |
| `parent` | `parent_product_category_id` | yes | — | self-referencing, lazy |

Constraints: `UK_PROD_CAT_CODE` on the code; `UK_PROD_CAT_IF_CHILD` on the
composite `(parent_product_category_id, product_category_name)`;
`FK_PROD_CAT_PARENT` self-referencing foreign key.

`ProductCategory` is the only entity in the codebase that declares value
equality but **no** string representation (it carries `@EqualsAndHashCode` without
`@ToString`) — a deliberate guard against infinite recursion through the parent
chain when logging.

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataProductCategoriesController` | `api/controllers/DataProductCategoriesController.java` |
| crud service | `ProductCategoriesCrudServiceImpl` | `jpa/services/crud/impl/ProductCategoriesCrudServiceImpl.java` |
| tree resolver | `ProductCategoryTreeResolverServiceImpl` | `jpa/services/impl/ProductCategoryTreeResolverServiceImpl.java` |
| converter | `ProductCategoriesConverterServiceImpl` | `jpa/services/conversion/impl/ProductCategoriesConverterServiceImpl.java` |
| patch service | `ProductCategoriesPatchServiceImpl` | `jpa/services/patch/impl/ProductCategoriesPatchServiceImpl.java` |
| predicate service | `ProductCategoriesPredicateServiceImpl` | `jpa/services/predicates/impl/ProductCategoriesPredicateServiceImpl.java` |
| sort spec | `ProductCategoriesSortSpec` | `jpa/sortspecs/ProductCategoriesSortSpec.java` |
| repository | `ProductsCategoriesRepository` | `jpa/repositories/ProductsCategoriesRepository.java` |
| transport model | `ProductCategoryPojo` | `api/models/ProductCategoryPojo.java` |

Note the repository class name is `ProductsCategoriesRepository` (plural
`Products`), inconsistent with every other component in the domain.

## Configuration

| Key | Default | Effect |
|---|---|---|
| `trebol.api.max-category-fetching-recursion-depth` | 20 | ceiling for recursive descendant resolution |

## Dependencies

- **Depends on**: nothing.
- **Depended on by**: `product` — products carry an optional category foreign key,
  the product predicate service calls this domain's tree resolver to expand a
  category filter into a whole branch, and the products repository can orphan
  products from a collection of categories.

## Shared entity status

`ProductCategory` is a **shared entity**: owned by `product-category`,
referenced by `product` through the nullable `products.product_category_id`.
See `cross-cutting-documentation/shared-entities.md`.

## Public read, protected writes

`GET /data/product_categories` carries no authentication or authority
requirement — the category tree is public storefront data. Writes require
`product_categories:create` / `:update` / `:delete`. The seeded
`product_categories:read` authority exists and is granted to Administrator,
Manager and Salesperson, but no route checks it.
