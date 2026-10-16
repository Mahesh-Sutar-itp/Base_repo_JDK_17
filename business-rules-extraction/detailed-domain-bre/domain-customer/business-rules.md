# Business Rules — Customer (`customer`)

## BR-CUSTOMER-001 One customer per person, enforced by the database

`customers.person_id` carries UNIQUE KEY `UK_CUSTOM_PERSON_ID` and
`Customer.person` is mapped one-to-one with `optional=false`. Asserting: a second
customer row for the same person is rejected at the database level, not merely by
application code.
Source: `jpa/entities/Customer.java`,
`database-documentation/00-global-schema.sql`.

## BR-CUSTOMER-002 A customer cannot exist without a person

The association is mandatory and the column is not null. Asserting: there is no
anonymous or placeholder customer.
Source: `jpa/entities/Customer.java`.

## BR-CUSTOMER-003 Creating a customer creates or reuses a person in the same transaction

The association cascades every persistence operation. Asserting: a single
customer create writes both rows atomically — unlike registration in the `user`
domain, which performs three unwrapped writes.
Source: `jpa/entities/Customer.java`,
`jpa/services/crud/impl/CustomersCrudServiceImpl.java`.

## BR-CUSTOMER-004 The customer has no attributes of its own

`Customer` declares exactly one non-identifier field, the person reference.
Asserting: there is no credit limit, no customer tier, no registration date, no
status and no notes. Any such concept in a rewrite is new functionality.
Source: `jpa/entities/Customer.java`.

## BR-CUSTOMER-005 The identification number is the business key

`CustomersCrudServiceImpl.getExisting` resolves exclusively through the
join-fetching finder on the person's identification number. Asserting: two
callers submitting the same identification number with different names resolve to
the same customer, and the second caller's names are discarded.
Source: `jpa/services/crud/impl/CustomersCrudServiceImpl.java`,
`jpa/repositories/CustomersRepository.java`.

## BR-CUSTOMER-006 A blank identification number is rejected

`getExisting` raises a bad-input condition with the literal message
`Customer does not have an ID card`. Asserting: a 400-class error, not a 500.
Source: `jpa/services/crud/impl/CustomersCrudServiceImpl.java`.
Note the wording differs from the person domain's near-identical check
(`Customer does not have ID card`, without the article) — two similar but
distinct literals that a rewrite should unify deliberately.

## BR-CUSTOMER-007 Registering an existing customer is a conflict

The inherited pre-creation check raises the already-exists condition
`The item already exists`. Asserting: `POST /data/customers` with a known
identification number yields that conflict, and this is the condition the guest
filter catches and tolerates.
Source: `jpa/services/crud/CrudGenericService.java`,
`security/JwtGuestAuthenticationFilter.java`.

## BR-CUSTOMER-008 The API is typed on the person model, not a customer model

The whole CRUD stack is parameterised on `PersonPojo`; no customer-specific
transport model exists. Asserting: responses never carry the customer identifier,
and a client cannot distinguish a customer payload from a person payload. A
rewrite that introduces a proper customer model changes the wire contract.
Source: `api/controllers/DataCustomersController.java`,
`jpa/services/crud/impl/CustomersCrudServiceImpl.java`.

## BR-CUSTOMER-009 Person-level mandatory fields apply to customer creation

Because the payload is a person model, first name, last name, identification
number and e-mail are all required by transport validation on `POST` and `PUT`.
Asserting: a customer cannot be created with only an identification number.
Source: `api/models/PersonPojo.java`.

## BR-CUSTOMER-010 There is no partial-update route

`DataCustomersController` maps only read, create, replace and delete. Asserting:
`PATCH /data/customers` is not routed, so correcting one field of a customer's
person data requires a full replacement that re-sends every mandatory field.
Source: `api/controllers/DataCustomersController.java`.

## BR-CUSTOMER-011 The nested-person patch capability exists but is unreachable

`CustomersPatchServiceImpl` copies the change-map entries whose keys begin with
the person prefix (referencing the shared prefix constant), strips the prefix and
delegates to the person patch service, skipping the delegation when the
unprefixed map is empty. Asserting: the logic is present and wired as a bean, and
would behave as described if a route invoked it — `person.email` would patch the
linked person's e-mail while a bare `email` key would be ignored. Nothing invokes
it today.
Source: `jpa/services/patch/impl/CustomersPatchServiceImpl.java`,
`common/Utils.java`, `api/controllers/DataCustomersController.java`.

## BR-CUSTOMER-012 The prefix-stripping utility replaces every occurrence

The shared utility removes **all** occurrences of the prefix from each key rather
than only the leading one. Asserting: a key such as `person.person.email`
collapses unexpectedly. Defect, preserved here as observed behaviour.
Source: `common/Utils.java`.

## BR-CUSTOMER-013 Blank values never clear a stored person name, identification number or e-mail

Inherited from the person patch service's non-blank-only semantics for those four
fields. The two phone fields are set unconditionally and **can** be blanked.
Source: `jpa/services/patch/impl/PeoplePatchServiceImpl.java`.

## BR-CUSTOMER-014 Deleting a customer does not delete its person

No removal cascade is configured. Asserting: person rows accumulate after
customer deletion, and the orphaned person can be reused by a later customer or
account.
Source: `jpa/entities/Customer.java`.

## BR-CUSTOMER-015 Deleting a customer referenced by an order is blocked by the database

`orders.customer_id` is not null with foreign key `FK_ORDER_CUSTOM_ID` and no
cascade, and this domain performs no pre-delete reference check. Asserting:
deleting a customer who has ever placed an order aborts on the constraint and
surfaces as a 500-class error. Since every order requires a customer, in practice
any customer with order history is permanently undeletable.
Source: `database-documentation/00-global-schema.sql`,
`jpa/services/crud/impl/CustomersCrudServiceImpl.java` (no override of delete).

## BR-CUSTOMER-016 Every order must reference a customer

`orders.customer_id` is not null and `Order.customer` is `optional=false`.
Asserting: there is no guest-order concept at the data level — guest checkout
works precisely by creating a real customer first.
Source: `jpa/entities/Order.java`,
`security/JwtGuestAuthenticationFilter.java`.

## BR-CUSTOMER-017 Guest checkout produces a permanent customer record

The guest filter persists the guest profile as a customer before authentication.
Asserting: "guest" means *unregistered account*, not *anonymous buyer*; the
person and customer rows are indistinguishable from those of a registered buyer.
Source: `security/JwtGuestAuthenticationFilter.java`.

## BR-CUSTOMER-018 A repeat guest reuses the existing customer

The already-exists condition is caught, logged
(`Guest with idNumber={} is already registered in the database`) and ignored, so
the flow proceeds with the pre-existing customer.
Source: `security/JwtGuestAuthenticationFilter.java`.

## BR-CUSTOMER-019 Checkout reuses an existing customer rather than failing

`OrdersConverterServiceImpl.convertCustomerInformationForEntity` resolves the
submitted customer and reuses it when found, creating a new one otherwise.
Asserting: placing an order never conflicts on an existing customer.
Source: `jpa/services/conversion/impl/OrdersConverterServiceImpl.java`.

## BR-CUSTOMER-020 Customer administration requires explicit authorities

`customers:read`, `customers:create`, `customers:update` (replace only — there
is no patch route) and `customers:delete`, all behind class-level authentication.
Seeded
holders: Administrator all four; Manager and Salesperson read only; the Customer
role none — so a buyer cannot read or edit their own customer record through this
surface and must use the account-profile route instead.
Source: `api/controllers/DataCustomersController.java`,
`database-documentation/00-seed-data.json`.

## BR-CUSTOMER-021 Update is strictly single-row

Empty selecting parameters → `Missing request params`; more than one match →
`Cannot update more than one item at a time`; no match →
`No element was found to update`.
Source: `api/DataCrudGenericController.java`,
`jpa/services/crud/CrudGenericService.java`.

## BR-CUSTOMER-022 The person association is eagerly fetched

`Customer.person` uses the default eager strategy for a one-to-one association,
unlike most associations in the codebase, which are explicitly lazy. Asserting:
every customer read also loads its person, which is why listing customers can
return full person data without an explicit join fetch.
Source: `jpa/entities/Customer.java`.
