# Features — Product Category Tree (`product-category`)

## F-001 Public paged category listing

`GET /data/product_categories` with no authentication or authority requirement.
Returns a page envelope of categories, each carrying its code, name and parent.

## F-002 Root-level category browsing

Passing `parentId` with a blank value filters for categories whose parent is
null, i.e. the top level of the tree. This is the storefront's entry point into
the hierarchy.

## F-003 Direct-children browsing

Passing `parentId` with a numeric value, or `parentCode` with a non-blank value,
filters for the immediate children of that category.

## F-004 Whole-branch browsing by root identifier

Passing `rootId` expands the given category into **all** its descendant
identifiers via the tree resolver and matches any category whose parent is in
that set.

## F-005 Whole-branch browsing by root code

Passing `rootCode` resolves the category by code, then expands it the same way.
When the resolved descendant list is empty the condition is skipped entirely
rather than matching nothing.

## F-006 Category filtering by code and name

Keys: `id` and `code` (both short-circuit, discarding every other filter), `name`
for an exact match and `nameLike` for a partial match.

## F-007 Category sorting

`ProductCategoriesSortSpec` whitelists `name` and `code`.

## F-008 Create a category

`POST /data/product_categories`, authority `product_categories:create`, responds
`201`. Code is the business key; a duplicate code is a conflict.

## F-009 Replace a category

`PUT /data/product_categories`, authority `product_categories:update`, responds
`204`. Full-overwrite semantics through the generic replace path.

## F-010 Partially update a category with parent preservation

`PATCH /data/product_categories`, authority `product_categories:update`, responds
`204`. `code` and `name` are applied when non-blank. The crud service overrides
the flush step to **re-attach the existing parent** before saving, so a patch can
never detach a category from its parent.

## F-011 Delete a category

`DELETE /data/product_categories`, authority `product_categories:delete`,
responds `204`. Standard generic delete: empty parameter map deletes nothing,
no match is a not-found.

## F-012 Descendant resolution as a reusable service

`ProductCategoryTreeResolverService` exposes three operations: resolve descendant
entities from a root entity, resolve descendant identifiers from a root
identifier, and resolve descendant identifiers from a root code. All three
recurse, bounded by the configured maximum depth.

## F-013 Branch-aware product search (consumed by `product`)

The product predicate service calls this domain's resolver so that a
`categoryCode` filter on a product search matches products in the named category
**or** in any category beneath it.

## F-014 Product orphaning by category (consumed by `product`)

`ProductsRepository.orphanizeByCategories` sets the category reference to null
for every product in a given collection of categories — the mechanism that would
allow categories to be removed without deleting their products. No service in
the codebase currently calls it.
