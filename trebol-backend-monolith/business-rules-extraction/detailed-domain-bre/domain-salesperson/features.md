# Features — Salesperson (`salesperson`)

## F-001 Paged salesperson search

`GET /data/salespeople`, class-level authentication plus authority
`salespeople:read`. Returns a page envelope whose items are person models; the
salesperson surrogate key is not exposed.

## F-002 Salesperson filtering through person attributes

`SalespeoplePredicateServiceImpl` builds every condition by traversing the person
association, with the same key set as the customer domain. Exact keys: `id` (the
**salesperson** identifier), `idNumber`, `name` (first name OR last name),
`firstName`, `lastName`, `email`. Partial keys: `nameLike`, `firstNameLike`,
`lastNameLike`, `idNumberLike`, `emailLike`. `id` and `idNumber` short-circuit,
discarding every other filter.

## F-003 Salesperson sorting through person attributes

`SalespeopleSortSpec` whitelists the same seven person-backed keys as the
customer domain: `idNumber`, `firstName`, `lastName`, `name` (aliases to last
name), `email`, `phone1`, `phone2`.

## F-004 Register a salesperson

`POST /data/salespeople`, authority `salespeople:create`, responds `201`. The
body is a person payload. The cascading association persists the person and the
salesperson together.

## F-005 Replace a salesperson

`PUT /data/salespeople`, authority `salespeople:update`, responds `204`. Full
overwrite through the generic replace path, selected by a non-empty request
parameter map.

## F-006 Delete a salesperson

`DELETE /data/salespeople`, authority `salespeople:delete`, responds `204`. Empty
parameter map deletes nothing; a non-matching filter is a not-found. The linked
person row is **not** deleted.

## F-007 Salesperson lookup by identification number (internal)

`SalespeopleCrudServiceImpl.getExisting` resolves a salesperson through the
join-fetching finder on the person's identification number.

## F-008 Order attribution (consumed by `order`)

An order may carry a salesperson reference, recorded through the nullable
`orders.salesperson_id` column. The association is mapped with a cascade set that
includes merge and persist, so an order carrying salesperson data can create or
update the referenced row as a side effect of order persistence.

## Features deliberately absent

| Expected capability | Status |
|---|---|
| partial update of a salesperson | **absent from the REST surface** — `SalespeoplePatchServiceImpl` exists and is wired as a bean, but no `PATCH` route exposes it (the same is true of customers, users and user roles) |
| link a salesperson to a login account | **absent** — nothing connects `salespeople` to `app_users`; the seeded `Salesperson` *role* is unrelated to any `salespeople` row |
| commission, quota or target tracking | **absent** — the entity has no attributes |
| assign a salesperson to an order after creation | **absent** — the order patch service exposes no `salesperson` key, and the column mapping is not update-restricted but no route sets it |
| salesperson performance reporting | **absent** — orders are sortable by customer and shipper but there is no salesperson aggregate anywhere |
