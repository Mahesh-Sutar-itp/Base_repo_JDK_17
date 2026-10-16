# Features — Product List (`product-list`)

**List management (`/data/product_lists`)**

## F-001 Public paged list browsing
`GET /data/product_lists` with no authentication or authority requirement.
Returns a page envelope of lists, each carrying code, name and a total item
count.

## F-002 List filtering
`ProductListsPredicateServiceImpl` keys: `name` and `listName` (aliases, both
short-circuit), `code` and `listCode` (aliases, both short-circuit), plus
`nameLike` and `codeLike`. Notably there is **no `id` key** — the only predicate
service in the codebase without one.

## F-003 List sorting including a derived count
`ProductListsSortSpec` whitelists `name`, `code` and `totalCount`, where the last
sorts on the size of the item collection — the only aggregate-derived sort key in
the system.

## F-004 Create a list
`POST /data/product_lists`, authority `product_lists:create`, responds `201`.
Name is the business key; code and name must both be unique.

## F-005 Replace a list
`PUT /data/product_lists`, authority `product_lists:update`, responds `204`.
Full overwrite of code, name and the disabled flag.

## F-006 Partially update a list
`PATCH /data/product_lists`, authority `product_lists:update`, responds `204`.
Only `name` and `code` are patchable, each when non-blank. The disabled flag and
the membership are **not** patchable.

## F-007 Delete a list and cascade its membership
`DELETE /data/product_lists`, authority `product_lists:delete`, responds `204`.
The crud service overrides the delete to remove every membership row for each
matched list **before** deleting the lists themselves — the only domain in the
system that performs pre-delete cleanup of its own child table.

**Contents management (`/data/product_list_contents`)**

## F-008 Public paged contents browsing
`GET /data/product_list_contents?listCode=...` with no authentication or
authority requirement. Returns a page envelope of **products** (not membership
rows), built by converting each membership row to a product model.

## F-009 Mandatory list selection on every contents operation
All four contents operations resolve the target list from the `listCode` query
parameter. A blank or missing value raises the bad-input message
`listCode query param is required`; an unresolvable code raises the not-found
message `Requested item(s) not found`.

## F-010 Contents filtering
`ProductListItemsPredicateServiceImpl` keys: `listName`, `listCode`,
`productName`, `productCode` (mapped to the product's barcode),
`productNameLike` and `productCodeLike`. The service is null-map safe, unlike
most others.

## F-011 Contents sorting
`ProductListItemsSortSpec` whitelists `name` and `barcode`, both resolved through
the product association. Sorting is applied only when a `sortBy` parameter is
present.

## F-012 Add a single product to a list
`POST /data/product_list_contents`, authority `product_lists:contents`, responds
`201`. The submitted product is resolved through the `product` domain; a
membership row is created only if an equivalent row does not already exist.

## F-013 Replace a list's entire contents
`PUT /data/product_list_contents`, authority `product_lists:contents`, responds
`204`. Deletes **every** membership row for the list, then re-adds one row per
resolvable product in the submitted collection.

## F-014 Remove products from a list
`DELETE /data/product_list_contents`, authority `product_lists:contents`,
responds `204`. The same filter keys as F-010 select which membership rows to
remove; with only `listCode` supplied, **every** row in the list is removed.

## F-015 Duplicate-membership prevention in application code
Before saving a membership row the controller checks for an equivalent existing
row by example matching. The database has no uniqueness constraint on the pair,
so this check is the only protection.

## F-016 Tolerant product resolution
A submitted product that does not resolve is **silently skipped** in both the
add and the replace operations — the request succeeds having added nothing.

**Features deliberately absent**

| Expected capability | Status |
|---|---|
| toggle a list's disabled flag | **absent from the patch surface** — the flag exists on the entity and in the DDL but is not patchable; only a full replacement can set it |
| honour the disabled flag anywhere | **absent** — no filter, no query and no controller reads it |
| ordered or ranked membership | **absent** — membership rows carry no position |
| nested or hierarchical lists | **absent** |
| per-list metadata (description, validity dates, audience) | **absent** |
