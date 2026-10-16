# Business Rules — Product List (`product-list`)

## List identity

### BR-PRODLIST-001 List code and list name are each globally unique
`product_lists` carries independent UNIQUE KEYs `UK_PLIST_CODE` and
`UK_PLIST_NAME`. Asserting: two lists cannot share a code, and cannot share a
name either.
Source: `jpa/entities/ProductList.java`,
`database-documentation/00-global-schema.sql`.

### BR-PRODLIST-002 Code and name both require at least two characters
`@Size(min=2,max=25)` on the code and `@Size(min=2,max=50)` on the name.
Asserting: a single-character code or name is rejected by the ORM even though the
database would accept it. This minimum is unique to this domain — every other
coded entity allows one character.
Source: `jpa/entities/ProductList.java`.

### BR-PRODLIST-003 The name is the business key for existence checks
`ProductListsCrudServiceImpl.getExisting` resolves through
`ProductListsRepository.findByName`. Asserting: creating a list with a new name
but a duplicate code passes the service check and fails at the database code
constraint instead — note this is the **opposite** key choice from every other
coded domain, which resolves by code.
Source: `jpa/services/crud/impl/ProductListsCrudServiceImpl.java`.

### BR-PRODLIST-004 A blank list name is rejected at the service layer
`getExisting` raises a bad-input condition with the literal message
`The specified list has no name`.
Source: `jpa/services/crud/impl/ProductListsCrudServiceImpl.java`.

### BR-PRODLIST-005 The transport model constrains the code but not the name
`ProductListPojo.code` is marked non-empty; `name` and `totalCount` carry **no**
constraint. Asserting: the transport layer guards the code while the service layer
guards the name — the two protections are split across layers and neither covers
both fields.
Source: `api/models/ProductListPojo.java`.

### BR-PRODLIST-006 The predicate service has no identity key
`ProductListsPredicateServiceImpl` supports name and code aliases and their
partial variants but no `id`. Asserting: a list cannot be selected by its
surrogate key through any route, so every update, patch and delete must be
addressed by code or name.
Source: `jpa/services/predicates/impl/ProductListsPredicateServiceImpl.java`.

### BR-PRODLIST-007 Lists are sortable by their item count
`ProductListsSortSpec` exposes `totalCount` mapped to the size of the item
collection. Asserting: this is the only aggregate-derived sort key in the system
and it requires the database to evaluate a subquery per row.
Source: `jpa/sortspecs/ProductListsSortSpec.java`.

## The disabled flag

### BR-PRODLIST-008 The disabled flag is mandatory, settable only by replacement
`product_list_disabled` is `bit(1)` not null. `ProductListPatchServiceImpl`
handles only `name` and `code`. Asserting: `PATCH` cannot toggle the flag; only a
full `PUT` can set it.
Source: `jpa/entities/ProductList.java`,
`jpa/services/patch/impl/ProductListPatchServiceImpl.java`.

### BR-PRODLIST-009 The disabled flag is never read by any code path
No predicate service, query, controller or converter references it. Asserting:
disabling a list has **no observable effect** — the list and its contents remain
fully readable through both public endpoints. The flag is stored state with no
behaviour attached.
Source: repo-wide absence of any `disabled` reader outside the entity and its
transport mapping.

### BR-PRODLIST-010 The flag is not even exposed on the transport model
`ProductListPojo` carries `name`, `code` and `totalCount` — no disabled field.
Asserting: a client cannot discover whether a list is disabled, and a `PUT` body
cannot meaningfully set it either, since the field does not exist in the contract.
Combined with BR-PRODLIST-008 this makes the flag effectively unreachable from
the API in both directions.
Source: `api/models/ProductListPojo.java`.

## Membership

### BR-PRODLIST-011 Every contents operation requires a list code
All four operations call the same private resolver. A blank or missing `listCode`
raises the bad-input message `listCode query param is required`.
Asserting: there is no cross-list contents query and no way to ask "which lists
contain this product".
Source: `api/controllers/DataProductListContentsController.java`.

### BR-PRODLIST-012 An unresolvable list code is a not-found
The same resolver raises the not-found message `Requested item(s) not found` when
the code matches no list. Asserting: all four operations fail consistently on an
unknown list.
Source: `api/controllers/DataProductListContentsController.java`.

### BR-PRODLIST-013 Contents are returned as products, not membership rows
The read converts each membership row to a product model. Asserting: the
membership surrogate key is never exposed, and the response shape is identical to
a product search result.
Source: `api/controllers/DataProductListContentsController.java`.

### BR-PRODLIST-014 Duplicate membership is prevented only in application code
`product_list_items` has no uniqueness constraint on
`(product_list_id, product_id)`. Both the add and the replace operations check
for an equivalent row by example matching before saving. Asserting: the guard is
a read-then-write sequence with no constraint behind it, so concurrent adds can
both pass and insert duplicates.
Source: `api/controllers/DataProductListContentsController.java`,
`database-documentation/00-global-schema.sql`.

### BR-PRODLIST-015 An unresolvable product is silently skipped
Both the add and the replace operations resolve the submitted product and act
only when it is present. Asserting: adding a non-existent barcode returns `201`
having added nothing, and a replace whose collection contains five unknown
products returns `204` having emptied the list. **No client-visible signal
distinguishes these from success.**
Source: `api/controllers/DataProductListContentsController.java`.

### BR-PRODLIST-016 Replacing contents is destructive before it is constructive
The replace operation deletes every membership row for the list **first**, then
re-adds the resolvable submitted products. Asserting: a replace with an empty
collection empties the list, and a replace whose products all fail to resolve
also empties it (BR-PRODLIST-015).
Source: `api/controllers/DataProductListContentsController.java`.

### BR-PRODLIST-017 A contents delete with only a list code empties the whole list
The delete applies the same filter keys as the read; with only `listCode`
supplied the predicate matches every row in the list. Asserting: omitting a
product filter is a full clear, not a no-op — the opposite convention from the
generic CRUD delete, which treats an empty filter as "do nothing".
Source: `api/controllers/DataProductListContentsController.java`,
`api/DataCrudGenericController.java`.

### BR-PRODLIST-018 The contents add operation validates its body
The add operation marks its product body for validation, so a product payload
with a blank name or barcode is rejected before resolution. Asserting: this is
stricter than the replace operation, whose collection body carries **no**
validation marker.
Source: `api/controllers/DataProductListContentsController.java`.

### BR-PRODLIST-019 Membership rows are immutable
`ProductListItem` exposes no patch service and both its join columns are
mandatory. Asserting: a membership row is created or deleted, never re-pointed.
Source: `jpa/entities/ProductListItem.java`.

### BR-PRODLIST-020 Membership associations are eagerly fetched
Both the list reference and the product reference on a membership row use the
default eager strategy. Asserting: reading membership rows loads the full product
for each one, which is why the contents read can return product models without an
explicit join fetch.
Source: `jpa/entities/ProductListItem.java`.

## Deletion and referential behaviour

### BR-PRODLIST-021 Deleting a list first deletes its membership
`ProductListsCrudServiceImpl` overrides the delete: for each matched list it
removes the membership rows through the item repository, then deletes the lists.
Asserting: this is the only domain in the system that cleans up its own child
table before deleting a parent — `product`, `image`, `shipper`,
`product-category` and `user` all leave the child rows to block the delete.
Source: `jpa/services/crud/impl/ProductListsCrudServiceImpl.java`.

### BR-PRODLIST-022 Deleting a list with no matches is a not-found
The override preserves the generic behaviour: a zero count raises
`Requested item(s) not found`.
Source: `jpa/services/crud/impl/ProductListsCrudServiceImpl.java`.

### BR-PRODLIST-023 Deleting a product silently removes it from every list
`product_list_items.product_id` is declared `ON DELETE CASCADE`. Asserting:
product deletion quietly shrinks every list containing it, with no notification
and no record of what was removed.
Source: `database-documentation/00-global-schema.sql`.

### BR-PRODLIST-024 The item repository exposes a product-driven cleanup that is never used
`ProductListItemsRepository.deleteByProductId` exists alongside the
list-driven variant. Only the list-driven one is called; product deletion relies
on the database cascade instead. Asserting: dead code that duplicates a
database-level rule.
Source: `jpa/repositories/ProductListItemsRepository.java`.

## Access

### BR-PRODLIST-025 Both read routes are world-readable
Neither controller declares an authentication requirement and neither read method
declares an authority requirement. Asserting: anonymous callers can enumerate
every list and every list's contents.
Source: `api/controllers/DataProductListsController.java`,
`api/controllers/DataProductListContentsController.java`.

### BR-PRODLIST-026 The seeded `product_lists:read` authority is never enforced
It exists and is granted to Administrator and Manager, but no code path checks
it.
Source: `database-documentation/00-seed-data.json`.

### BR-PRODLIST-027 All three contents writes share one authority
Add, replace and remove all require `product_lists:contents`. Asserting: there is
no way to grant the ability to add products to a list without also granting the
ability to empty it. Seeded holders: Administrator and Manager.
Source: `api/controllers/DataProductListContentsController.java`,
`database-documentation/00-seed-data.json`.

### BR-PRODLIST-028 List writes use the conventional three authorities
`product_lists:create`, `product_lists:update` (shared by replace and patch) and
`product_lists:delete`. Seeded holders: Administrator and Manager.
Source: `api/controllers/DataProductListsController.java`,
`database-documentation/00-seed-data.json`.

### BR-PRODLIST-029 List update and patch are strictly single-row
Empty selecting parameters → `Missing request params`; more than one match →
`Cannot update more than one item at a time`; no match →
`No element was found to update`. Note that because the predicate service has no
`id` key (BR-PRODLIST-006), the selecting filter is always a code or a name.
Source: `api/DataCrudGenericController.java`,
`jpa/services/crud/CrudGenericService.java`.

### BR-PRODLIST-030 The contents controller bypasses the generic framework entirely
It extends neither generic controller base class, builds its own page request,
applies sorting only when a `sortBy` parameter is present, and queries two
repositories plus the `product` crud service directly. Asserting: its behaviour
cannot be inferred from the generic CRUD contract — in particular its delete
semantics invert the generic convention (BR-PRODLIST-017).
Source: `api/controllers/DataProductListContentsController.java`.
