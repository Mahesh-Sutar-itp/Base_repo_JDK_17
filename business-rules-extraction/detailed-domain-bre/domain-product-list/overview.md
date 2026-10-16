# Domain: Product List (`product-list`)

## Responsibility

Owns curated, named collections of products — the mechanism a storefront uses for
"featured", "on sale" or any other merchandising grouping. A list has a code, a
name and a disabled flag; its membership is held in a join table that this domain
owns and governs.

## Bounded context summary

Merchandising context. Single aggregate root (`ProductList`) that transactionally
governs its membership rows (`ProductListItem`): deleting a list deletes its
items first, and replacing a list's contents deletes and rebuilds them wholesale.
Products themselves are referenced across a boundary and are owned by `product`.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `product_lists` | `ProductList` | `jpa/entities/ProductList.java` |
| `product_list_items` | `ProductListItem` | `jpa/entities/ProductListItem.java` |

### `ProductList` fields

| Field | Column | Nullable | Unique | Constraint |
|---|---|---|---|---|
| `id` | `product_list_id` | no | — | — |
| `name` | `product_list_name` | no | yes | `@Size(min=2,max=50)` |
| `code` | `product_list_code` | no | yes | `@Size(min=2,max=25)` |
| `disabled` | `product_list_disabled` | no | — | `bit(1)` boolean |
| `items` | — | — | — | one-to-many, mapped by the item's list reference, lazy, no cascade |

Both business columns have independent UNIQUE KEYs
(`UK_PLIST_NAME`, `UK_PLIST_CODE`). The minimum length of 2 on both is unusual —
every other coded entity in the system allows a single character.

### `ProductListItem` fields

Pure join entity with its own surrogate key: `product_list_id` and `product_id`,
both mandatory and eagerly fetched. The table has **no** uniqueness constraint on
the pair; duplicate membership is prevented in application code instead.

## Two controllers, two surfaces

This is the only domain in the system with **two** controllers:

| Controller | Route | Concern |
|---|---|---|
| `DataProductListsController` | `/data/product_lists` | the lists themselves — read, create, replace, patch, delete |
| `DataProductListContentsController` | `/data/product_list_contents` | list membership — read, add, replace-all, remove |

The contents controller is the most hand-written controller in the codebase: it
does **not** extend either generic controller base class, builds its own
pagination and sorting, queries two repositories directly and calls the `product`
domain's crud service. Every one of its four operations requires a `listCode`
query parameter.

## Components

| Role | Component | File |
|---|---|---|
| controller | `DataProductListsController` | `api/controllers/DataProductListsController.java` |
| controller | `DataProductListContentsController` | `api/controllers/DataProductListContentsController.java` |
| crud service | `ProductListsCrudServiceImpl` | `jpa/services/crud/impl/ProductListsCrudServiceImpl.java` |
| converter | `ProductListConverterServiceImpl` | `jpa/services/conversion/impl/ProductListConverterServiceImpl.java` |
| converter | `ProductListItemsConverterServiceImpl` | `jpa/services/conversion/impl/ProductListItemsConverterServiceImpl.java` |
| patch service | `ProductListPatchServiceImpl` | `jpa/services/patch/impl/ProductListPatchServiceImpl.java` |
| predicate service | `ProductListsPredicateServiceImpl` | `jpa/services/predicates/impl/ProductListsPredicateServiceImpl.java` |
| predicate service | `ProductListItemsPredicateServiceImpl` | `jpa/services/predicates/impl/ProductListItemsPredicateServiceImpl.java` |
| sort spec | `ProductListsSortSpec` | `jpa/sortspecs/ProductListsSortSpec.java` |
| sort spec | `ProductListItemsSortSpec` | `jpa/sortspecs/ProductListItemsSortSpec.java` |
| repository | `ProductListsRepository` | `jpa/repositories/ProductListsRepository.java` |
| repository | `ProductListItemsRepository` | `jpa/repositories/ProductListItemsRepository.java` |
| transport model | `ProductListPojo` | `api/models/ProductListPojo.java` |
| transport model | `ProductPojo` (shared with `product`) | `api/models/ProductPojo.java` |

Note the naming inconsistency: the converter and patch implementations are
singular (`ProductListConverterServiceImpl`, `ProductListPatchServiceImpl`) while
the interfaces they implement are plural.

## Dependencies

- **Depends on**: `product` — membership resolves products by barcode through the
  product crud service.
- **Depended on by**: nothing. No other domain reads lists or membership.

## Shared entity status

Neither `ProductList` nor `ProductListItem` is shared. `Product` (owned by
`product`) is used here.
See `cross-cutting-documentation/shared-entities.md`.

## Exposure

`GET` on both routes is **public** (no authentication, no authority). List writes
require `product_lists:create`, `product_lists:update` and
`product_lists:delete`; **all three** contents writes require the single
authority `product_lists:contents`. The seeded `product_lists:read` authority
exists, is granted to Administrator and Manager, and is never checked.
