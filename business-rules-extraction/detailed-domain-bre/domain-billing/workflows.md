# Workflows — Billing (`billing`)

## W-BILLING-01 List billing types

1. Any caller, authenticated or not, issues `GET /data/billing_types`. No
   authentication or authority check applies (BR-BILLING-016).
2. Page index and size are resolved from the parameter map (defaults `0` and 10,
   hard cap 1000).
3. If the map is non-empty, `sortBy` / `order` are resolved against the single
   sortable key `name`, and `id` / `name` / `nameLike` become filter conditions;
   `id` short-circuits and discards the rest.
4. A count query and a page query run; each row becomes a billing-type transport
   model and the page envelope is returned.

## W-BILLING-02 Individual-bill branch during order creation

1. The order converter reads the billing type name from the submitted order.
2. The name resolves to `Bill`.
3. No billing company is resolved, no billing address is created.
4. `orders.billing_company_id` and `orders.billing_address_id` stay null.

## W-BILLING-03 Enterprise-invoice branch during order creation

1. The order converter reads the billing type name and resolves
   `Enterprise Invoice`.
2. The submitted company's ID number is resolved against
   `billing_companies`.
3. Found → the stored company is reused, and the submitted company name is
   discarded.
4. Not found → a new company row is created from the submitted ID number and
   name. A duplicate company name with a new ID number aborts on the database
   name constraint.
5. The submitted billing address is resolved by its full field set; found → the
   existing address row is reused, otherwise a new one is created.
6. Both the company and the billing address are attached to the new order.

## W-BILLING-04 Unknown billing type during order creation

1. The submitted billing type name resolves to nothing.
2. The converter raises the bad-input error
   `Specified billing type does not exist`.
3. No order row, company row or address row is written.

## W-BILLING-05 Billing-type maintenance (unreachable)

The generic create / replace / patch / delete behaviour is inherited and wired,
but `DataBillingTypesController` exposes only the read contract. Billing types can
therefore be changed only by direct database access. Reaching them would follow
the standard generic flow: blank-name rejection, already-exists conflict,
single-row selection guards, no-op short-circuit on unchanged patches.

## W-BILLING-06 Billing-company maintenance (unreachable)

No controller wires any billing-company bean. The full service stack exists:
blank-ID-number rejection, lookup by ID number, already-exists conflict on
create, non-blank-only patching of `idNumber` and `name`, and the standard
single-row update guards. None of it is reachable over HTTP.

## State model

Neither `BillingType` nor `BillingCompany` has a status column or lifecycle
state. Billing types are fixed seed data in practice; billing companies are
append-only in practice, since the only write path is creation during enterprise
checkout and no route exists to modify or remove them.
