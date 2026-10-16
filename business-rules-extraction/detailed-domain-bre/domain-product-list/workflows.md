# Workflows — Product List (`product-list`)

## W-PRODLIST-01 Browse lists (public)

1. Any caller issues `GET /data/product_lists` with an optional parameter map.
2. Page index and size are resolved (defaults `0` and 10, hard cap 1000).
3. If the map is non-empty, `sortBy` / `order` are resolved against `name`,
   `code` and `totalCount`, and the remaining keys become filters.
4. `name` / `listName` or `code` / `listCode` short-circuits to a single match.
   There is no `id` key.
5. Each matched list is converted to a list model carrying code, name and the
   item count.

## W-PRODLIST-02 Create a list

1. Operator `POST`s a list payload; authority `product_lists:create`.
2. Transport validation enforces a non-empty code. The name is **not** validated
   at this layer.
3. The service rejects a blank name with `The specified list has no name`.
4. The name is looked up; a hit raises `The item already exists`.
5. Otherwise the list is converted, saved and flushed, and returned with status
   `201`. A duplicate code with a fresh name fails here at the database
   constraint, and a code or name shorter than two characters fails ORM
   validation.

## W-PRODLIST-03 Rename or re-code a list

1. Operator `PATCH`es with a non-empty selecting parameter map (a code or a
   name); authority `product_lists:update`.
2. Standard single-row guards apply.
3. The patch service copy-constructs the list and applies `name` and `code` when
   non-blank. The disabled flag and the membership are untouched.
4. An unchanged result issues no write.
5. Responds `204`.

## W-PRODLIST-04 Replace a list

Full overwrite through the generic replace path — the only way to set the
disabled flag, although the flag is absent from the transport model
(BR-PRODLIST-010), so in practice replacement cannot set it either.

## W-PRODLIST-05 Delete a list with membership cleanup

1. Operator `DELETE`s with a parameter map; authority `product_lists:delete`.
2. Empty map → nothing deleted. Zero matches → `Requested item(s) not found`.
3. For each matched list the override removes every membership row through the
   item repository.
4. The list rows themselves are then deleted.

This is the only pre-delete child cleanup in the system; every other domain
leaves its child rows to block the delete at the constraint.

## W-PRODLIST-06 Browse a list's contents (public)

1. Any caller issues
   `GET /data/product_list_contents?listCode=FEATURED` plus optional filters.
2. The list is resolved from `listCode`: blank raises
   `listCode query param is required`; unknown raises
   `Requested item(s) not found`.
3. Page index and size are resolved.
4. Sorting is applied **only** when a `sortBy` parameter is present, against
   `name` and `barcode` through the product association.
5. The membership filter is built from `listName`, `listCode`, `productName`,
   `productCode`, `productNameLike` and `productCodeLike`.
6. Membership rows are fetched (with their products eagerly loaded) and each is
   converted to a **product** model.
7. The total count is computed separately as the number of membership rows for
   the resolved list — not the number matching the filter. A filtered page
   therefore reports the list's full size, which is an inconsistency between the
   items and the count in the envelope.

## W-PRODLIST-07 Add one product to a list

1. Operator `POST`s a product payload with `listCode`; authority
   `product_lists:contents`.
2. The list is resolved as in W-PRODLIST-06.
3. The product body is validated (non-blank name and barcode, non-null price).
4. The product is resolved through the `product` domain by barcode.
5. **Not resolvable → the operation returns `201` having added nothing.**
6. Resolvable → a membership row is built, and saved only if no equivalent row
   already exists.

## W-PRODLIST-08 Replace a list's contents

1. Operator `PUT`s a collection of product payloads with `listCode`; authority
   `product_lists:contents`. The collection body is **not** validated.
2. The list is resolved.
3. **Every** membership row for the list is deleted.
4. For each submitted product: resolve by barcode, skip silently if unresolvable,
   otherwise create the membership row if an equivalent does not already exist.
5. Responds `204`.

Consequences: an empty collection empties the list, and a collection of entirely
unknown products also empties it, both reported as success.

## W-PRODLIST-09 Remove products from a list

1. Operator `DELETE`s with `listCode` and optionally product filters; authority
   `product_lists:contents`.
2. The list is resolved.
3. The membership filter is built from the same keys as the read.
4. Every matching membership row is deleted.

With only `listCode` supplied the filter matches the whole list, so the
operation is a full clear — the inverse of the generic CRUD convention where an
empty filter deletes nothing.

## W-PRODLIST-10 Product deletion effect (driven by `product`)

Deleting a product causes the database cascade on the membership table to remove
it from every list silently. The repository method that would do the same thing
in application code exists and is never called.

## State model

`ProductList` carries a `disabled` boolean, which is the only thing in this
domain that resembles state — and it is inert:

```text
disabled = false   list is readable through both public endpoints
disabled = true    list is STILL readable through both public endpoints
```

No filter, query or controller reads the flag; it is absent from the transport
model; and the patch service cannot set it. A rewrite must decide whether to make
it meaningful (hide disabled lists from the public read) or remove it.

`ProductListItem` has no state at all: rows are created and deleted, never
modified.
