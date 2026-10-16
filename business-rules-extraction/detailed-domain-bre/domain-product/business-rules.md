# Business Rules — Product Catalog (`product`)

## Identity and uniqueness

### BR-PRODUCT-001 Barcode and name are each globally unique
`products.product_code` carries UNIQUE KEY `UK_PRODUCT_CODE` and
`products.product_name` carries `UK_PRODUCT_NAME`. Asserting: two products cannot
share a barcode, and cannot share a name either — so two genuinely different
items with the same display name cannot both exist.
Source: `jpa/entities/Product.java`,
`database-documentation/00-global-schema.sql`.

### BR-PRODUCT-002 The barcode is the business key for existence checks
`ProductsCrudServiceImpl.getExisting` resolves exclusively through
`ProductsRepository.findByBarcode`. Asserting: creating a product with a new
barcode but a duplicate name passes the service check and fails at the database
name constraint instead.
Source: `jpa/services/crud/impl/ProductsCrudServiceImpl.java`.

### BR-PRODUCT-003 A blank barcode is rejected at the service layer
`getExisting` raises a bad-input condition with the literal message
`Invalid product barcode`.
Source: `jpa/services/crud/impl/ProductsCrudServiceImpl.java`.

### BR-PRODUCT-004 The field name and the column name disagree
The entity field is `barcode`; the column is `product_code`. Filter keys, sort
keys and the transport model all use `barcode`. Asserting: a rewrite must keep
the external name `barcode` while mapping to `product_code`, or change both
deliberately.
Source: `jpa/entities/Product.java`.

### BR-PRODUCT-005 Creating a product that already exists is a conflict
The inherited pre-creation check raises `The item already exists`.
Source: `jpa/services/crud/CrudGenericService.java`.

## Mandatory data and validation

### BR-PRODUCT-006 Name, barcode and price are mandatory; stock levels are not
`ProductPojo` marks `name` and `barcode` non-blank and `price` non-null. The two
stock fields carry **no** transport constraint even though both columns are not
null. Asserting: a create payload omitting stock relies on whatever default the
conversion produces, while omitting price is rejected.
Source: `api/models/ProductPojo.java`, `jpa/entities/Product.java`.

### BR-PRODUCT-007 Price and stock are whole numbers with no lower bound
All three are `int` columns with no minimum constraint anywhere. Asserting: a
**negative** price, a negative current stock and a negative critical threshold
are all accepted and persisted.
Source: `jpa/entities/Product.java`, `api/models/ProductPojo.java`.

### BR-PRODUCT-008 Price has no decimal places
`product_price` is `int(11)` — whole currency units only. Asserting: fractional
prices are unrepresentable, and the order tax calculation truncates accordingly.
A rewrite must not silently widen this without deciding a rounding policy. See
the monetary note in `database-documentation/00-global-schema.sql`.
Source: `database-documentation/00-global-schema.sql`.

### BR-PRODUCT-009 The description is optional and may be long
Nullable, up to 4000 characters — the largest text column in the schema.
Source: `jpa/entities/Product.java`.

### BR-PRODUCT-010 Classification is optional
`products.product_category_id` is nullable with no cascade. Asserting: an
unclassified product is valid and will not appear in any category-filtered
search.
Source: `jpa/entities/Product.java`.

## Image linking

### BR-PRODUCT-011 Image links are rebuilt wholesale on replacement
The overridden identifier-based update deletes every image-link row for the
product and then recreates them from the submitted list. Asserting: a `PUT` whose
body carries no images **removes all existing image links**.
Source: `jpa/services/crud/impl/ProductsCrudServiceImpl.java`,
`jpa/repositories/ProductImagesRepository.java`.

### BR-PRODUCT-012 Images are not patchable
`ProductsPatchServiceImpl` handles only the six scalar fields. Asserting: there
is no way to add or remove a single image link — only a full replacement can
change the image set.
Source: `jpa/services/patch/impl/ProductsPatchServiceImpl.java`.

### BR-PRODUCT-013 An unresolvable image is skipped, not rejected
A blank filename raises a bad-input condition from the `image` domain which the
product service catches, logs at debug level as
`An image was not linked to product with barcode '{}'`, and ignores. Asserting:
a create with one valid and one blank image succeeds with one link, and the
client is never told that an image was dropped.
Source: `jpa/services/crud/impl/ProductsCrudServiceImpl.java`.

### BR-PRODUCT-014 An unknown image produces a placeholder rather than a failure
When the filename is valid but matches no stored image, the service builds an
unsaved image from the submitted fields. Asserting: image data submitted through
the product surface can enter the system without passing the `image` domain's own
create validation.
Source: `jpa/services/crud/impl/ProductsCrudServiceImpl.java`.

### BR-PRODUCT-015 The same image can be linked to the same product twice
`product_images` has no uniqueness constraint on `(image_id, product_id)`.
Asserting: duplicate link rows are accepted by the database.
Source: `database-documentation/00-global-schema.sql`.

### BR-PRODUCT-016 Only the single-item read resolves images
The overridden read uses a join-fetching query on the link table; the paged read
inherited from the generic service does not. Asserting: a product's image list is
populated when fetched individually and not necessarily when listed.
Source: `jpa/services/crud/impl/ProductsCrudServiceImpl.java`,
`jpa/repositories/ProductImagesRepository.java`.

## Category interaction

### BR-PRODUCT-017 A patch can never declassify a product
`ProductsPatchServiceImpl` re-attaches the existing category onto the patched
copy. Asserting: no `PATCH` request can set the category to null or change it.
Source: `jpa/services/patch/impl/ProductsPatchServiceImpl.java`.

### BR-PRODUCT-018 Reclassification is only possible through replacement
The generic replace path builds a new entity from the body including whatever
category it carries. Asserting: `PUT` is the only way to change or clear a
product's category through the API. The two repository helpers that would do it
directly are never called.
Source: `jpa/services/crud/CrudGenericService.java`,
`jpa/repositories/ProductsRepository.java`.

### BR-PRODUCT-019 Category-filtered search includes the whole branch
The category filter combines an exact code match with a descendant-identifier
match from the tree resolver. Asserting: searching by a parent category's code
returns products in every descendant category too, bounded by the configured
recursion depth.
Source: `jpa/services/predicates/impl/ProductsPredicateServiceImpl.java`.

### BR-PRODUCT-020 Products block category deletion
`products.product_category_id` has foreign key `FK_PRODUCT_CAT_ID` with no
cascade. Asserting: deleting a category with any classified product fails on the
constraint. The `orphanizeByCategories` operation that would prevent this exists
and is never called.
Source: `database-documentation/00-global-schema.sql`,
`jpa/repositories/ProductsRepository.java`.

## Stock

### BR-PRODUCT-021 Stock is recorded but never acted upon
No code path reads `stockCurrent` or `stockCritical` to make a decision.
Asserting: there is no availability check, no reservation, no decrement on order
placement, no oversell prevention and no low-stock notification anywhere in the
codebase. Confirmed by the absence of stock logic in the order process service
and the order converter.
Source: `api/services/impl/OrdersProcessServiceImpl.java`,
`jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

### BR-PRODUCT-022 An out-of-stock product can be ordered and paid for
Follows from BR-PRODUCT-021. Asserting: checkout of a product with zero or
negative current stock succeeds, proceeds to payment and completes with no
warning. **This is the single most significant functional gap in the system** and
a rewrite must decide explicitly whether to reproduce it.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`
(no stock branch in `convertDetailToNewEntity`).

### BR-PRODUCT-023 Stock values are set unconditionally by the patch service
`price`, `currentStock` and `criticalStock` are cast and assigned without a null
guard. Asserting: a change map carrying an explicit null for any of the three
fails with a null-dereference rather than a clean validation error.
Source: `jpa/services/patch/impl/ProductsPatchServiceImpl.java`.

### BR-PRODUCT-024 Blank values never clear a stored barcode, name or description
The patch service applies those three only when non-blank.
Source: `jpa/services/patch/impl/ProductsPatchServiceImpl.java`.

## Access and deletion

### BR-PRODUCT-025 The catalogue is world-readable
`DataProductsController` declares no authentication requirement and its read
method no authority requirement. Asserting: anonymous callers can enumerate the
full catalogue including price and stock levels — stock exposure to the public is
worth flagging.
Source: `api/controllers/DataProductsController.java`.

### BR-PRODUCT-026 The seeded `products:read` authority is never enforced
It exists and is granted to Administrator, Manager and Salesperson, but no code
path checks it.
Source: `database-documentation/00-seed-data.json`,
`api/controllers/DataProductsController.java`.

### BR-PRODUCT-027 Write operations require explicit authorities
`products:create`, `products:update` (shared by replace and patch) and
`products:delete`. Seeded holders: Administrator all three; Manager all three;
Salesperson holds `products:create`, `products:update` and `products:delete`
as well as the unenforced read. The Customer role holds none.
Source: `api/controllers/DataProductsController.java`,
`database-documentation/00-seed-data.json`.

### BR-PRODUCT-028 Deleting a product does not delete its image links
No pre-delete cleanup exists in the delete path, and `product_images` holds a
foreign key to `products` with no cascade. Asserting: deleting a product that has
any image link aborts on the constraint, even though the service that created
those links lives in this same domain.
Source: `database-documentation/00-global-schema.sql`,
`jpa/services/crud/impl/ProductsCrudServiceImpl.java` (no delete override).

### BR-PRODUCT-029 Order history survives product deletion
`order_details.product_id` is declared nullable in the DDL with
`ON DELETE SET NULL`. Asserting: deleting a product blanks the product reference
on historical order lines rather than blocking the delete — but the line's
description and unit value are preserved, so the historical record remains
readable. Note the ORM declares the same association as mandatory, so the ORM and
the DDL disagree; see the divergence note in
`database-documentation/00-global-schema.sql`.
Source: `database-documentation/00-global-schema.sql`,
`jpa/entities/OrderDetail.java`.

### BR-PRODUCT-030 Product-list membership blocks nothing
`product_list_items.product_id` is declared with `ON DELETE CASCADE`. Asserting:
deleting a product silently removes it from every product list.
Source: `database-documentation/00-global-schema.sql`.

### BR-PRODUCT-031 Update and patch are strictly single-row
Empty selecting parameters → `Missing request params`; more than one match →
`Cannot update more than one item at a time`; no match →
`No element was found to update`.
Source: `api/DataCrudGenericController.java`,
`jpa/services/crud/CrudGenericService.java`.

### BR-PRODUCT-032 The deep paged query ignores its filter
`ProductsRepository.deepReadAll(Predicate, Pageable)` uses the same query text as
the unfiltered variant, so the predicate has no effect, and its count query
selects entities rather than a count. Asserting: any caller relying on that
overload would receive unfiltered results. No service calls it today, so the
defect is latent.
Source: `jpa/repositories/ProductsRepository.java`.
