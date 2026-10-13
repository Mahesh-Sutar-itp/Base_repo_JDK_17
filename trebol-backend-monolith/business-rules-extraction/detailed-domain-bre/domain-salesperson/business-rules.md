# Business Rules — Salesperson (`salesperson`)

## BR-SALESPERSON-001 A salesperson cannot exist without a person

`salespeople.person_id` is not null and `Salesperson.person` is
`optional=false`. Asserting: there is no placeholder or unnamed salesperson.
Source: `jpa/entities/Salesperson.java`,
`database-documentation/00-global-schema.sql`.

## BR-SALESPERSON-002 Several salesperson rows may share one person

`salespeople.person_id` has **no** unique constraint and the association is
many-to-one. Asserting: the database accepts two salesperson rows pointing at the
same person — in direct contrast to the customer domain, where the equivalent
column is unique. Only the service-level existence check stands in the way, and
it is not atomic.
Source: `jpa/entities/Salesperson.java`,
`database-documentation/00-global-schema.sql`.

## BR-SALESPERSON-003 Creating a salesperson creates or reuses a person in the same transaction

The association cascades every persistence operation. Asserting: a single
salesperson create writes both rows atomically.
Source: `jpa/entities/Salesperson.java`.

## BR-SALESPERSON-004 The salesperson has no attributes of its own

`Salesperson` declares exactly one non-identifier field. Asserting: there is no
employee number, hire date, commission rate, quota, territory or status. Any such
concept in a rewrite is new functionality.
Source: `jpa/entities/Salesperson.java`.

## BR-SALESPERSON-005 The identification number is the business key

`SalespeopleCrudServiceImpl.getExisting` resolves exclusively through the
join-fetching finder on the person's identification number.
Source: `jpa/services/crud/impl/SalespeopleCrudServiceImpl.java`,
`jpa/repositories/SalespeopleRepository.java`.

## BR-SALESPERSON-006 A blank identification number is rejected

`getExisting` raises a bad-input condition with the literal message
`Salesperson does not have an ID card`. Asserting: a 400-class error.
Source: `jpa/services/crud/impl/SalespeopleCrudServiceImpl.java`.

## BR-SALESPERSON-007 Registering an existing salesperson is a conflict

The inherited pre-creation check raises the already-exists condition
`The item already exists` when the identification number resolves.
Asserting: this check — not a database constraint — is the only thing preventing
BR-SALESPERSON-002 in practice.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-SALESPERSON-008 The API is typed on the person model, not a salesperson model

The whole CRUD stack is parameterised on `PersonPojo`; no salesperson transport
model exists. Asserting: responses never carry the salesperson identifier, and
the salesperson API is indistinguishable in shape from the person and customer
APIs.
Source: `api/controllers/DataSalespeopleController.java`.

## BR-SALESPERSON-009 Person-level mandatory fields apply to salesperson creation

Because the payload is a person model, first name, last name, identification
number and e-mail are all required by transport validation on `POST` and `PUT`.
Source: `api/models/PersonPojo.java`.

## BR-SALESPERSON-010 There is no partial-update route

`DataSalespeopleController` implements read, create, replace and delete only.
Asserting: `PATCH /data/salespeople` is not routed, so correcting one field of a
salesperson's person data requires a full replacement that re-sends every
mandatory field. The same omission applies to `/data/customers`, `/data/users`
and `/data/user_roles`.
Source: `api/controllers/DataSalespeopleController.java`,
`reference-documentation/api-endpoint-catalog.md`.

## BR-SALESPERSON-011 The patch service exists but is unreachable

`SalespeoplePatchServiceImpl` is implemented and wired, delegating nested person
changes using the literal `person.` prefix. Asserting: the capability is present
in the service layer and absent from the REST surface — a rewrite can expose it
without writing new logic.
Source: `jpa/services/patch/impl/SalespeoplePatchServiceImpl.java`.

## BR-SALESPERSON-012 The patch service hardcodes the prefix literal

Unlike the customer patch service, which references the shared prefix constant,
the salesperson patch service uses the literal string directly. Asserting: a
consistency defect, behaviourally identical today, that would silently diverge if
the constant were ever changed.
Source: `jpa/services/patch/impl/SalespeoplePatchServiceImpl.java`,
`jpa/services/patch/impl/CustomersPatchServiceImpl.java`,
`config/Constants.java`.

## BR-SALESPERSON-013 Replacement is a full overwrite

The generic replace path builds a new entity from the body and assigns the
existing identifier. Asserting: `PUT` re-creates the person data wholesale; any
field omitted from the body is lost, though transport validation prevents the
four mandatory fields from being omitted.
Source: `jpa/services/crud/CrudGenericService.java`.

## BR-SALESPERSON-014 An order may have no salesperson

`orders.salesperson_id` is nullable and `Order.salesperson` has no
`optional=false`. Asserting: self-service orders placed through public checkout
carry no salesperson, and attribution is entirely optional.
Source: `jpa/entities/Order.java`,
`database-documentation/00-global-schema.sql`.

## BR-SALESPERSON-015 No route ever sets an order's salesperson

Public checkout does not populate the field, and the order patch service exposes
no `salesperson` key. Asserting: with the shipped API surface
`orders.salesperson_id` is **always null** — order attribution is a modelled but
unreachable capability.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`,
`jpa/services/patch/impl/OrdersPatchServiceImpl.java`.

## BR-SALESPERSON-016 An order carrying salesperson data can create or modify the row

`Order.salesperson` is mapped with a cascade set including persist and merge.
Asserting: were a route to populate the field, order persistence would create or
update the salesperson row as a side effect — a cross-domain write that bypasses
this domain's own validation.
Source: `jpa/entities/Order.java`.

## BR-SALESPERSON-017 Orders are sortable by customer and shipper but not by salesperson

The order sort specification whitelists buy-order identifier, date, status,
customer, shipper and the four monetary fields. Asserting: there is no
salesperson sort key and no salesperson filter on the order search, so even if
attribution were populated it could not be reported on.
Source: `jpa/sortspecs/OrdersSortSpec.java`,
`jpa/services/predicates/impl/OrdersPredicateServiceImpl.java`.

## BR-SALESPERSON-018 Deleting a salesperson does not delete its person

No removal cascade is configured. Asserting: person rows accumulate after
salesperson deletion.
Source: `jpa/entities/Salesperson.java`.

## BR-SALESPERSON-019 Deleting a salesperson referenced by an order is blocked by the database

`orders.salesperson_id` carries foreign key `FK_ORDER_SPERSON` with no cascade,
and this domain performs no pre-delete reference check. Asserting: the constraint
exists, but because no route ever sets the column (BR-SALESPERSON-015) it is
never triggered in a shipped deployment.
Source: `database-documentation/00-global-schema.sql`.

## BR-SALESPERSON-020 A salesperson record is not a login account

Nothing links `salespeople` to `app_users`. The seeded `Salesperson` **role** and
the seeded `salesperson` **account** are part of the `user` domain and have no
relationship to any `salespeople` row. Asserting: creating a salesperson record
grants no access, and granting the Salesperson role creates no salesperson
record. The two concepts are entirely disconnected.
Source: `jpa/entities/User.java`, `jpa/entities/Salesperson.java`,
`database-documentation/00-seed-data.json`.

## BR-SALESPERSON-021 Salesperson administration requires explicit authorities

`salespeople:read`, `salespeople:create`, `salespeople:update` and
`salespeople:delete`, all behind class-level authentication. Seeded holders:
Administrator all four; Manager create, update and read (not delete);
Salesperson read only; Customer none.
Source: `api/controllers/DataSalespeopleController.java`,
`database-documentation/00-seed-data.json`.

## BR-SALESPERSON-022 Update is strictly single-row

Empty selecting parameters → `Missing request params`; more than one match →
`Cannot update more than one item at a time`; no match →
`No element was found to update`.
Source: `api/DataCrudGenericController.java`,
`jpa/services/crud/CrudGenericService.java`.

## BR-SALESPERSON-023 The person association is eagerly fetched

The many-to-one association uses the default eager strategy, unlike most
associations in the codebase. Asserting: every salesperson read also loads its
person.
Source: `jpa/entities/Salesperson.java`.
