# Workflows — Product Catalog (`product`)

## W-PRODUCT-01 Public product search

1. Any caller issues `GET /data/products` with an optional parameter map.
2. Page index and size are resolved (defaults `0` and 10, hard cap 1000).
3. If the map is non-empty, `sortBy` / `order` are resolved against `name`,
   `barcode`, `price` and `category`, and the remaining keys become filters.
4. `id`, `barcode` or `name` short-circuits to a single-identity match.
5. `categoryCode` is expanded through the `product-category` tree resolver into
   the set of descendant category identifiers, and the condition becomes
   "category code matches **or** category identifier is in the set".
6. A count query and a page query run; each row is converted to a product model.
   The paged path does **not** join-fetch image links.

## W-PRODUCT-02 Create a product with images

1. Operator `POST`s a product payload; authority `products:create`.
2. Transport validation enforces non-blank name and barcode and a non-null price.
   Stock fields are unconstrained.
3. The override runs the pre-creation existence check: a blank barcode raises
   `Invalid product barcode`; a known barcode raises `The item already exists`.
4. The product is converted and persisted.
5. For each submitted image the service asks the `image` domain for an existing
   match:
   - blank filename → the bad-input condition is caught, logged at debug level as
     `An image was not linked to product with barcode '{}'`, and the loop
     continues;
   - valid but unknown filename → an unsaved placeholder image is built from the
     submitted fields;
   - found → the stored image is used.
6. A join row is built for each resolved image and the batch is saved.
7. The created product is returned with status `201`.

## W-PRODUCT-03 Read one product with its images

1. A filter resolving to a single product reaches the overridden single-item read.
2. The product is loaded, then the image-link table is queried with a
   join-fetching query so the linked images are materialised.
3. The product model is returned with its image list populated.

## W-PRODUCT-04 Replace a product and rebuild its images

1. Operator `PUT`s with a non-empty selecting parameter map plus a full body;
   authority `products:update`.
2. Empty parameters → `Missing request params`. More than one match →
   `Cannot update more than one item at a time`. Zero matches →
   `No element was found to update`.
3. The body becomes a new entity, the existing identifier is assigned, and the
   product row is saved.
4. **Every** existing image-link row for the product is deleted.
5. The links are recreated from the submitted image list using the same tolerant
   resolution as W-PRODUCT-02.
6. Responds `204`.

Consequence: a `PUT` body with no image list removes all image links
(BR-PRODUCT-011).

## W-PRODUCT-05 Patch a product

1. Operator `PATCH`es with a non-empty selecting parameter map and a change map;
   authority `products:update`.
2. Standard single-row guards apply.
3. The patch service copy-constructs the product and **re-attaches the existing
   category**.
4. `barcode`, `name` and `description` are applied only when non-blank;
   `price`, `currentStock` and `criticalStock` are cast and applied
   unconditionally (an explicit null fails with a null-dereference).
5. Images are not touched.
6. If the patched copy equals the original, no write is issued.
7. Responds `204`.

## W-PRODUCT-06 Delete a product

1. Operator `DELETE`s with a parameter map; authority `products:delete`.
2. Empty map → nothing deleted. Zero matches → `Requested item(s) not found`.
3. Matching rows are deleted, with three different downstream effects:
   - **image links** block the delete on the foreign-key constraint (no cleanup
     is performed);
   - **product-list items** are removed automatically by the cascade on that
     table;
   - **order lines** have their product reference nulled by the
     set-null rule, preserving the historical description and unit value.

## W-PRODUCT-07 Product resolution during checkout (driven by `order`)

1. For each submitted order line the order converter reads the product barcode.
2. A blank barcode raises `Product barcode must be valid`; an unknown barcode
   raises `Unexisting product in sell details`. Both abort the order.
3. The resolved product's **current price** is copied onto the line, overriding
   whatever unit value the client submitted.
4. The line description is generated as the unit count followed by the product
   name.
5. **No stock check and no stock decrement occur at any point.**

## W-PRODUCT-08 Product resolution when adding to a list (driven by `product-list`)

The product-list contents controller resolves the submitted product through this
domain's `getExisting`. A product that does not resolve is **silently skipped** —
the list membership request succeeds having added nothing.

## State model

`Product` has no status column and no lifecycle states. There is no draft,
active, discontinued or archived state, so a product that should no longer be
sold can only be deleted — which is blocked whenever it has image links.

The two stock fields look like state but are inert:

```text
stockCurrent    recorded, returned, editable; never read by any decision
stockCritical   recorded, returned, editable; never compared to anything
```

A rewrite that introduces availability logic is adding behaviour, not porting it.
