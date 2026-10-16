# Features — Customer (`customer`)

## F-001 Paged customer search

`GET /data/customers`, class-level authentication plus authority
`customers:read`. Returns a page envelope whose items are person models — the
customer surrogate key is not exposed.

## F-002 Customer filtering through person attributes

`CustomersPredicateServiceImpl` builds every condition by traversing the person
association. Exact keys: `id` (the **customer** identifier), `idNumber`, `name`
(first name OR last name), `firstName`, `lastName`, `email`. Partial keys:
`nameLike`, `firstNameLike`, `lastNameLike`, `idNumberLike`, `emailLike`.
`id` and `idNumber` short-circuit, discarding every other filter.

## F-003 Customer sorting through person attributes

`CustomersSortSpec` whitelists `idNumber`, `firstName`, `lastName`, `name`
(aliases to last name), `email`, `phone1`, `phone2` — all resolved through the
person association. There is no sort key for the customer identifier itself.

## F-004 Register a customer

`POST /data/customers`, authority `customers:create`, responds `201`. The body is
a person payload. The cascading association persists the person and the customer
together; submitting an identification number that already belongs to a customer
is a conflict.

## F-005 Replace a customer

`PUT /data/customers`, authority `customers:update`, responds `204`. Full
overwrite through the generic replace path, selected by a non-empty request
parameter map.

## F-006 Nested person patching (service only, unreachable)

`CustomersPatchServiceImpl` is implemented and wired: it copy-constructs the
customer, extracts the change-map entries whose keys carry the `person.` prefix
using the shared prefix constant, strips the prefix and delegates the resulting
sub-map to the person patch service, skipping the delegation entirely when the
sub-map is empty.

**No `PATCH` route exposes it.** `DataCustomersController` maps only read,
create, replace and delete, so a single-field correction requires a full
replacement. Exposing the capability is a routing change, not a logic change.

## F-007 Delete a customer

`DELETE /data/customers`, authority `customers:delete`, responds `204`. Empty
parameter map deletes nothing; a non-matching filter is a not-found. The linked
person row is **not** deleted.

## F-008 Customer lookup by identification number (internal)

`CustomersCrudServiceImpl.getExisting` resolves a customer through the
join-fetching finder on the person's identification number. Used by the order
converter to reuse an existing customer at checkout, and by the guest filter to
detect a repeat guest.

## F-009 Customer resolution during checkout (driven by `order`)

The order converter resolves the submitted customer by identification number and
reuses the existing row, or creates a new customer (and person) when none
matches. Every order must end up with a customer.

## F-010 Customer creation during self-registration (driven by `user`)

Registration creates a customer row for the newly registered person as its third
and final write, so every self-registered account is also a buyer.

## F-011 Customer creation during guest authentication (driven by `user`)

The guest filter persists the submitted guest profile through this domain's crud
service before issuing a token. A repeat guest produces an already-exists
condition that the filter logs and tolerates.
