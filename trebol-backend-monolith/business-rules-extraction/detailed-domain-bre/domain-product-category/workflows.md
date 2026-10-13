# Workflows — Product Category Tree (`product-category`)

## W-PRODCAT-01 Browse the tree level by level (storefront navigation)

1. Caller issues `GET /data/product_categories?parentId=` (blank value) to obtain
   the root level.
2. For each root category the caller re-issues the request with
   `parentId=<id>` (or `parentCode=<code>`) to obtain the next level.
3. Each response is an ordinary page envelope, so deep trees are paged per level.
   There is no single call that returns the whole tree in nested form.

## W-PRODCAT-02 Browse an entire branch at once

1. Caller issues `GET /data/product_categories?rootId=<id>` or `?rootCode=<code>`.
2. The tree resolver loads the direct children, then recurses into each child,
   accumulating descendant identifiers until the configured depth limit.
3. The filter matches any category whose parent is in the accumulated set.
4. If the root code resolves to nothing, the descendant condition is dropped and
   the response is an unfiltered page (BR-PRODCAT-011).

## W-PRODCAT-03 Create a category

1. Operator `POST`s code, name and optionally a parent; authority
   `product_categories:create`.
2. Transport validation enforces non-blank code and name. The submitted parent is
   **not** validated.
3. The service rejects a blank code with `Invalid category code`.
4. The code is looked up; a hit raises `The item already exists`.
5. Otherwise the category is converted, saved and flushed, and returned with
   status `201`. A sibling name collision fails here at the composite database
   constraint.

## W-PRODCAT-04 Rename or re-code a category

1. Operator `PATCH`es with a non-empty selecting parameter map; authority
   `product_categories:update`.
2. Standard single-row guards apply.
3. The patch service copy-constructs the category and applies `code` and `name`
   when non-blank.
4. The crud service's overridden flush step re-attaches the stored parent, then
   short-circuits the save if the result equals the original.
5. Responds `204`.

## W-PRODCAT-05 Move a category in the tree

Only reachable through `PUT`. The body's parent becomes the category's new
parent because replacement builds a fresh entity. No ancestry or cycle check
runs, so this is the operation that can corrupt the hierarchy
(BR-PRODCAT-009).

## W-PRODCAT-06 Delete a category

1. Operator `DELETE`s with a parameter map; authority
   `product_categories:delete`.
2. Empty map → nothing deleted. No match → `Requested item(s) not found`.
3. Matching rows are deleted. Any category with a child, or with any product
   classified under it, aborts on a foreign-key constraint.

Safe deletion would require first orphaning the products (the repository
operation exists) and re-parenting or deleting the children. Neither step is
implemented.

## W-PRODCAT-07 Descendant expansion for a product search (driven by `product`)

1. A product search arrives with `categoryCode`.
2. The product predicate service asks this domain's resolver for the descendant
   identifiers of that code.
3. The resulting product filter is "category code equals the value **or**
   category identifier is in the descendant set".
4. Products directly in the named category and products anywhere beneath it both
   match.

## Recursion and depth

```text
depth 0: the root category itself
depth 1: findByParent(root)            -> children
depth 2: findByParent(child) for each  -> grandchildren
...
depth N: stops when N reaches trebol.api.max-category-fetching-recursion-depth
```

The limit is a silent truncation, not an error. A cyclic hierarchy
(BR-PRODCAT-009) therefore terminates at the limit rather than hanging, which
masks the corruption instead of reporting it.

## State model

`ProductCategory` has no status column and no lifecycle states. Its only
"state" is its position in the hierarchy: root (null parent) or child (non-null
parent). Rows are created, renamed, re-parented through replacement only, and
hard-deleted when unreferenced.
