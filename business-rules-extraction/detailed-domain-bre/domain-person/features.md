# Features — Person Registry (`person`)

## F-001 Paged person search

Operators with the `people:read` authority can page through the person registry.
Exposed as `GET /data/people` (`DataPeopleController`, class-level
`@PreAuthorize("isAuthenticated()")`, method-level
`@PreAuthorize("hasAuthority('people:read')")`). Request parameters do double duty
as pagination (`pageIndex`, `pageSize`), sorting (`sortBy`, `order`) and filters.
Response is a page envelope carrying items, page index, page size and total count.

## F-002 Person filtering by exact and partial match

`PeoplePredicateServiceImpl` translates query parameters into a filter expression.
Exact keys: `id`, `idNumber`, `name` (first name OR last name), `firstName`,
`lastName`, `email`. Partial keys: `nameLike`, `firstNameLike`, `lastNameLike`,
`idNumberLike`, `emailLike`. `id` and `idNumber` short-circuit — when either is
present the remaining parameters are discarded and the filter resolves to that
single identity match.

## F-003 Person sorting

`PeopleSortSpec` whitelists the sortable keys: `idNumber`, `firstName`,
`lastName`, `name` (aliases to last name), `email`, `phone1`, `phone2`. Any other
`sortBy` value is not in the map. Direction comes from `order` = `asc` / `desc`.

## F-004 Person lookup by national ID number (internal)

`PeopleCrudServiceImpl.getExisting` resolves a person from a submitted
`PersonPojo` using `PeopleRepository.findByIdNumber`. This is the de-duplication
entry point used by the registration and profile flows in the `user` domain and
by customer/salesperson creation.

## F-005 Person creation (internal, no REST route)

The generic create path (`CrudGenericService.create`) is inherited and functional —
convert to a new entity, reject if an equivalent already exists, persist, convert
back — but `DataPeopleController` exposes no `POST`, so persons are only created as
a side effect of `user` registration, `user` profile update, `customer` creation
and `salesperson` creation.

## F-006 Person partial update (internal, no REST route)

`PeoplePatchServiceImpl` applies a sparse change map onto a copy of the existing
person. `idNumber`, `firstName`, `lastName` and `email` are applied only when the
submitted value is non-blank; `phone1` and `phone2` are applied unconditionally.
Used by the `user` domain's profile-update feature and by the customer/salesperson
patch services through the `person.` key prefix.

## F-007 Nested person patching through a prefix convention

Customer and salesperson patch payloads address nested person fields with the
`person.` key prefix. The prefix is stripped by a shared utility and the remaining
map is delegated to this domain's patch service. The person sub-patch is skipped
entirely when the unprefixed map is empty.
