# Validations — Product List (`product-list`)

## Transport-model validation

### `ProductListPojo` (list management)

| Field | Constraint | Violation outcome |
|---|---|---|
| `code` | `@NotEmpty` | validation envelope, code `REJECTED_02` |
| `name` | **none** | accepted, including blank — caught later by the service |
| `totalCount` | none | outbound-only value |
| `disabled` | **field does not exist on the model** | the flag cannot be read or set through the API |

Source: `api/models/ProductListPojo.java`. Applied on `POST` and `PUT`; `PATCH`
is uncovered.

Note the annotation is the non-empty variant rather than the non-blank variant
used everywhere else in the codebase, so a whitespace-only code passes transport
validation and is then rejected only by the ORM minimum length of 2.

### `ProductPojo` (contents management)

| Operation | Body | Validated? |
|---|---|---|
| add one product | single product model | **yes** |
| replace all contents | collection of product models | **no** |

Asserting: the stricter of the two paths is the single-item one. A replace
collection may contain entirely blank product payloads, which are then silently
skipped during resolution.

Source: `api/controllers/DataProductListContentsController.java`.

## Persistence-layer validation

| Column | Constraint |
|---|---|
| `product_list_code` | not null, unique, max 25, **ORM min 2** |
| `product_list_name` | not null, unique, max 50, **ORM min 2** |
| `product_list_disabled` | not null, `bit(1)` |
| `product_list_items.product_list_id` | not null, foreign key |
| `product_list_items.product_id` | not null, foreign key, `ON DELETE CASCADE` |

No uniqueness constraint exists on the membership pair.

## Service- and controller-layer validation

| Check | Component | Failure |
|---|---|---|
| list name present | `ProductListsCrudServiceImpl.getExisting` | bad input, `The specified list has no name` |
| list must not already exist on create | `CrudGenericService.validateInputPojoBeforeCreation` | already exists, `The item already exists` |
| selecting parameters present on list update/patch | `DataCrudGenericController` | bad input, `Missing request params` |
| at most one list selected on update/patch | `CrudGenericService` | runtime failure, `Cannot update more than one item at a time` |
| at least one list selected on update/patch | `DataCrudGenericController` | not found, `No element was found to update` |
| at least one list selected on delete | `ProductListsCrudServiceImpl.delete` | not found, `Requested item(s) not found` |
| `listCode` present on every contents operation | `DataProductListContentsController` | bad input, `listCode query param is required` |
| `listCode` resolvable on every contents operation | `DataProductListContentsController` | not found, `Requested item(s) not found` |
| membership row not already present | `DataProductListContentsController` | **no failure** — the save is simply skipped |

## Validation that is deliberately suppressed

| Condition | Handling |
|---|---|
| submitted product not resolvable on add | silently skipped; the operation still returns `201` |
| submitted product not resolvable on replace | silently skipped; the list may end up empty, reported as `204` |
| duplicate membership row | save skipped with no signal |

## Gaps

- **The disabled flag is unreachable and inert.** It is mandatory in the
  database, absent from the transport model, unpatchable, and read by no code.
  Three independent layers each prevent it from having any effect.
- **Partial success is reported as success.** Adding or replacing contents with
  unresolvable products returns a success status having changed less — or more
  destructively — than the caller intended. This is the most likely source of
  silent data loss in the domain.
- **Destructive-before-constructive replacement.** Contents replacement deletes
  everything first, so any failure or resolution gap after that point leaves the
  list smaller than both its old and its intended new state.
- **Inverted delete convention.** A contents delete with only `listCode` empties
  the whole list, whereas the generic CRUD delete with an empty filter does
  nothing. Two different conventions in one domain.
- **The contents read reports an inconsistent total count.** The count is the
  number of membership rows in the list, not the number matching the applied
  filter, so a filtered page's item list and total disagree.
- **Duplicate membership is guarded only in application code**, as a
  read-then-write sequence with no constraint behind it.
- **Name and code validation is split across layers**: the code is guarded at the
  transport layer and the name at the service layer, so neither field is
  protected consistently.
- **No `id` filter key**, so every list write must be addressed by code or name —
  which are themselves the mutable fields being changed.
- **Both read routes are unauthenticated** while `product_lists:read` exists, is
  granted to two roles and is never checked.
- **One authority covers all three contents writes**, so "may add products" and
  "may empty the list" cannot be separated.
