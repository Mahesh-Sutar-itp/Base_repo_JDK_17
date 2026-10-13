# Features — Product Catalog (`product`)

## F-001 Public paged product search

`GET /data/products` with no authentication or authority requirement. Returns a
page envelope of products. Pagination defaults: index `0`, size from
`trebol.api.items-per-page` (10), hard cap
`trebol.api.max-allowed-page-size` (1000).

## F-002 Product filtering by identity, barcode and name

`ProductsPredicateServiceImpl` keys: `id`, `barcode` and `name` all
short-circuit, discarding every other filter; `barcodeLike` and `nameLike`
provide partial matching.

## F-003 Branch-aware category filtering

`categoryCode` resolves to "category code equals the value **or** category
identifier is in the descendant set", where the descendant set comes from the
`product-category` tree resolver. A product classified anywhere beneath the named
category therefore matches. `categoryCodeLike` provides partial matching on the
code alone, with no branch expansion.

## F-004 Product sorting

`ProductsSortSpec` whitelists `name`, `barcode`, `price` and `category`
(resolved through the category's name).

## F-005 Create a product with image links

`POST /data/products`, authority `products:create`, responds `201`. The override
runs the standard pre-creation existence check, persists the product, then builds
and saves a join row for every submitted image.

## F-006 Read a single product with its images eagerly resolved

The overridden single-item read uses a join-fetching query on the image-link
table so that the returned product carries its full image list. The ordinary
paged read does **not** do this.

## F-007 Replace a product and rebuild its image links

`PUT /data/products`, authority `products:update`, responds `204`. The override
deletes **every** existing image-link row for the product and then recreates the
links from the submitted image list — a wholesale rebuild, not a merge.

## F-008 Partially update a product with category preservation

`PATCH /data/products`, authority `products:update`, responds `204`. The patch
service re-attaches the existing category before applying changes, so a patch can
never declassify a product. Patchable keys: `barcode`, `name`, `description`
(non-blank only) and `price`, `currentStock`, `criticalStock` (applied
unconditionally). Images are **not** patchable.

## F-009 Delete a product

`DELETE /data/products`, authority `products:delete`, responds `204`. Standard
generic delete semantics: an empty parameter map deletes nothing, a non-matching
filter is a not-found.

## F-010 Tolerant image linking

When an image cannot be resolved the failure is swallowed: a blank filename
raises a bad-input condition that the product service catches, logs at debug
level as `An image was not linked to product with barcode '{}'`, and then
continues with the next image. When the filename is valid but unknown, an unsaved
placeholder image is built from the submitted fields instead.

## F-011 Product lookup by barcode (internal)

`ProductsCrudServiceImpl.getExisting` resolves a product through
`ProductsRepository.findByBarcode`. Used by the order converter when building
order lines, by the product-list contents controller when adding items, and by
the pre-creation existence check.

## F-012 Stock levels as plain attributes

`stockCurrent` and `stockCritical` are stored and returned, and are settable
through replace and patch. **Nothing in the system reads them to make a
decision** — no availability check, no reorder trigger, no low-stock
notification, and no decrement on order placement.

## F-013 Category reassignment helper (repository only)

`ProductsRepository.setProductCategoryById` re-points a single product at a
category, and `orphanizeByCategories` nulls the category reference for every
product in a collection of categories. Neither is called by any service —
reassignment happens through replace instead, and the orphaning operation that
would make category deletion safe is dead code.

## Features deliberately absent

| Expected capability | Status |
|---|---|
| stock decrement on order placement | **absent** — no code path changes stock |
| availability or oversell check at checkout | **absent** — an out-of-stock product can be ordered |
| low-stock alerting | **absent** — the critical threshold is never compared |
| price history or effective-dated pricing | **absent** — a single current integer price |
| product activation / discontinuation | **absent** — no status column |
| multi-currency or decimal pricing | **absent** — price is a whole-unit integer |
