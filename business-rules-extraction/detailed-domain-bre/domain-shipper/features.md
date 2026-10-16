# Features — Shipper (`shipper`)

## F-001 Public paged shipper listing

`GET /data/shippers` with no authority requirement and no class-level
authentication requirement. Returns a page envelope of shipper names. Pagination
defaults: index `0`, size from `trebol.api.items-per-page` (10), hard cap
`trebol.api.max-allowed-page-size` (1000).

## F-002 Shipper filtering

`ShippersPredicateServiceImpl` keys: `id` and `name` (both short-circuit and
discard every other filter) plus `nameLike` for partial matching.

## F-003 Shipper sorting

`ShippersSortSpec` whitelists exactly one key: `name`.

## F-004 Register a carrier

`POST /data/shippers`, authority `shippers:create`, responds `201`. The name is
mandatory on the transport model and must be unique.

## F-005 Replace a carrier

`PUT /data/shippers`, authority `shippers:update`, responds `204`. Requires a
non-empty selecting parameter map; the body fully replaces the matched row.

## F-006 Partially update a carrier

`PATCH /data/shippers`, authority `shippers:update`, responds `204`. Applies
`name` only when non-blank — the only patchable field in the domain.

## F-007 Remove a carrier

`DELETE /data/shippers`, authority `shippers:delete`, responds `204`. An empty
parameter map deletes nothing; a non-matching filter is a not-found.

## F-008 Shipper resolution during order creation (internal, driven by `order`)

The order converter looks the shipper up by name while assembling shipping
information for a new order. An unknown name aborts order creation with the
bad-input message `Specified shipper does not exist`.

## F-009 Shipper reassignment on an existing order (internal, driven by `order`)

The order patch service accepts a `shipper` key carrying a carrier name and
re-points the order at the matching row. An unknown name is **silently ignored**
rather than rejected — a deliberate asymmetry against F-008.
