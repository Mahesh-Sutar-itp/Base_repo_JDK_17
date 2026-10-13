# Features — Billing (`billing`)

## F-001 Paged billing-type listing

`GET /data/billing_types` — **public**. `DataBillingTypesController` carries no
authentication requirement and no authority requirement; the only annotations on
the read method are the route mapping and the API documentation summary. Returns
a page envelope of billing-type names. This is the only exposed route in the
domain. (Note the controller is grouped under the `Params management`
documentation tag, alongside the company-profile endpoint.)

## F-002 Billing-type filtering

`BillingTypesPredicateServiceImpl` keys: `id` (short-circuits, discarding other
filters), `name` and `nameLike`.

## F-003 Billing-type sorting

`BillingTypesSortSpec` whitelists exactly one key: `name`.

## F-004 Billing-type lookup by name (internal)

`BillingTypesCrudServiceImpl.getExisting` resolves a billing type through
`BillingTypesRepository.findByName`. The order converter uses the same repository
method directly to decide which billing branch applies to a new order.

## F-005 Billing-type create / replace / patch / delete (service only, unreachable)

The inherited generic CRUD behaviour is fully implemented and the beans exist,
but `DataBillingTypesController` implements only the read contract, so none of
these operations is reachable over HTTP. Billing types are effectively fixed at
the two seeded rows.

## F-006 Billing-company registration during enterprise checkout

When an order declares billing type `Enterprise Invoice`, the order converter
resolves the submitted company by its ID number and creates the row if it does
not exist, then resolves or creates the company's billing address. This is the
only way a `billing_companies` row is ever written.

## F-007 Billing-company lookup by ID number (internal)

`BillingCompaniesCrudServiceImpl.getExisting` resolves a company through
`BillingCompaniesRepository.findByIdNumber`. A blank ID number is rejected with
the bad-input message `Billing company has no id number`.

## F-008 Billing-company filtering (service only, unreachable)

`BillingCompaniesPredicateServiceImpl` supports `id` and `idNumber` (both
short-circuit), `name`, `idNumberLike` and `nameLike`. No controller exposes it.

## F-009 Billing-company patching (service only, unreachable)

`BillingCompaniesPatchServiceImpl` applies `idNumber` and `name` when non-blank.
No controller exposes it.

## F-010 Billing-branch selection for an order

The billing type name on an order selects between two behaviours in the order
converter: the individual branch records no company and no billing address, while
the enterprise branch requires both. This is the domain's single most important
business behaviour and it is executed inside the `order` domain.
