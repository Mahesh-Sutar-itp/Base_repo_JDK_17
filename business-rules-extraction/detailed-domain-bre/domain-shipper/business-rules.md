# Business Rules — Shipper (`shipper`)

## BR-SHIPPER-001 Carrier names are unique

`shippers.shipper_name` carries UNIQUE KEY `UK_SHIPPER_NAME` and `Shipper.name`
is `@Column(unique=true)`. Asserting: registering a second carrier with an
existing name is rejected.
Source: `jpa/entities/Shipper.java`, `database-documentation/00-global-schema.sql`.

## BR-SHIPPER-002 The carrier name is the only business attribute

`Shipper` declares exactly one non-identifier field. Asserting: there is no way
through the API to record a carrier's contact details, tracking URL, pricing or
coverage. A rewrite that needs those must add them as new behaviour, not restore
something that exists.
Source: `jpa/entities/Shipper.java`.

## BR-SHIPPER-003 A blank carrier name is rejected at the service layer

`ShippersCrudServiceImpl.getExisting` throws a bad-input error when the submitted
name is blank. **The literal message is `Billing type has no name`** — a
copy-paste defect carried over from the billing-type service. Asserting: the
rejection happens with a 400-class error and the current (wrong) message.
Source: `jpa/services/crud/impl/ShippersCrudServiceImpl.java`.
A rewrite should correct the wording; the control flow must stay the same.

## BR-SHIPPER-004 The carrier name is mandatory on the transport model

`ShipperPojo.name` is `@NotBlank`. Asserting: a `POST` or `PUT` body without a
name is rejected with the validation envelope (`REJECTED_02`) before service code
runs. `PATCH` takes a raw change map and is not covered.
Source: `api/models/ShipperPojo.java`.

## BR-SHIPPER-005 Carrier name length is bounded only by the database

No `@Size` annotation exists on `Shipper.name`; the column is `varchar(255)`.
Asserting: a 256-character name is accepted by every Java-side check and fails
only at the database as an infrastructure error.
Source: `jpa/entities/Shipper.java`, `database-documentation/00-global-schema.sql`.

## BR-SHIPPER-006 Registering a carrier that already exists is a conflict

The inherited pre-creation check raises the already-exists error
`The item already exists` when `getExisting` finds a row.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-SHIPPER-007 A blank value never clears a stored carrier name

`ShippersPatchServiceImpl` applies `name` only when non-blank. Asserting:
patching `name` to `""` leaves the row unchanged, and because `name` is the only
patchable field, such a patch is a guaranteed no-op write-free request.
Source: `jpa/services/patch/impl/ShippersPatchServiceImpl.java`.

## BR-SHIPPER-008 The carrier list is world-readable

`DataShippersController` has no class-level authentication requirement and its
read method carries no authority requirement. Asserting: an unauthenticated
caller can enumerate every carrier.
Source: `api/controllers/DataShippersController.java`.

## BR-SHIPPER-009 The seeded `shippers:read` authority is never enforced

`shippers:read` exists in the authority catalogue and is granted to Administrator
and Manager, but no code path checks it. Asserting: revoking it from a role
changes nothing observable.
Source: `database-documentation/00-seed-data.json`,
`api/controllers/DataShippersController.java`.

## BR-SHIPPER-010 Write operations require explicit authorities

`shippers:create`, `shippers:update` (shared by `PUT` and `PATCH`) and
`shippers:delete`. Seeded holders: Administrator and Manager. Salesperson and
Customer hold none.
Source: `api/controllers/DataShippersController.java`,
`database-documentation/00-seed-data.json`.

## BR-SHIPPER-011 Update and patch are strictly single-row

Empty selecting parameters → bad input `Missing request params`; more than one
match → `Cannot update more than one item at a time`; no match → not-found
`No element was found to update`.
Source: `api/DataCrudGenericController.java`,
`jpa/services/crud/CrudGenericService.java`.

## BR-SHIPPER-012 An empty delete filter deletes nothing

Asserting: `DELETE /data/shippers` with no query parameters removes no rows. A
non-matching filter raises `Requested item(s) not found`.
Source: `api/DataCrudGenericController.java`,
`jpa/services/crud/CrudGenericService.java`.

## BR-SHIPPER-013 Deleting a carrier referenced by an order is blocked by the database

`orders.shipper_id` has foreign key `FK_ORDER_SHIPPER_ID` with no cascade, and
this domain performs no pre-delete reference check. Asserting: deleting a carrier
that any order points at aborts on the constraint and surfaces as a 500-class
error, not a business rule.
Source: `database-documentation/00-global-schema.sql`, absence of any order lookup
in `ShippersCrudServiceImpl`.

## BR-SHIPPER-014 An order may have no carrier at all

`orders.shipper_id` is nullable and `Order.shipper` has no `optional=false`.
Asserting: an order can be created and completed with no shipper assigned —
shipping information is optional in the checkout payload.
Source: `jpa/entities/Order.java`, `database-documentation/00-global-schema.sql`.

## BR-SHIPPER-015 Unknown carrier names fail on create but pass silently on patch

Order creation rejects an unknown carrier with `Specified shipper does not exist`;
the order patch service resolves the name and applies it only if present,
discarding unknown names without error. Asserting: `PATCH /data/orders` with
`shipper` = `NoSuchCarrier` succeeds and leaves the existing carrier in place.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`,
`jpa/services/patch/impl/OrdersPatchServiceImpl.java`.
