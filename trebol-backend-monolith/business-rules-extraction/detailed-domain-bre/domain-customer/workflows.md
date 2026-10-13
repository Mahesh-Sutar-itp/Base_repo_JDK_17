# Workflows — Customer (`customer`)

## W-CUSTOMER-01 Search customers

1. Operator issues `GET /data/customers` with an optional parameter map;
   authentication plus `customers:read` required.
2. Page index and size are resolved (defaults `0` and 10, hard cap 1000).
3. If the map is non-empty, `sortBy` / `order` are resolved against the seven
   person-backed sort keys and the remaining keys become filter conditions, all
   traversing the person association.
4. `id` or `idNumber` short-circuits to a single-identity match.
5. Each matched customer is converted to a **person** model; the customer
   identifier is not included.

## W-CUSTOMER-02 Register a customer directly

1. Operator `POST`s a person payload; authority `customers:create`.
2. Transport validation enforces non-blank first name, last name, identification
   number and e-mail.
3. The service rejects a blank identification number with
   `Customer does not have an ID card`.
4. The identification number is resolved through the join-fetching finder; a hit
   raises `The item already exists`.
5. Otherwise the payload is converted to a new customer carrying a new person,
   and the cascading association persists both rows in one transaction.
6. The saved customer is converted back to a person model and returned with
   status `201`.

## W-CUSTOMER-03 Correct a customer's person data (replacement only)

1. Operator `PUT`s with a non-empty selecting parameter map plus a full person
   body; authority `customers:update`.
2. Empty parameters → `Missing request params`. More than one match →
   `Cannot update more than one item at a time`. Zero matches →
   `No element was found to update`.
3. The body becomes a new entity, the existing identifier is assigned and the row
   is saved — a full overwrite of the person data.
4. Responds `204`.

Because there is no `PATCH` route (BR-CUSTOMER-010), this is the only correction
path and it requires re-sending every mandatory person field.

### The unreachable partial-update path

The patch service is fully implemented: copy-construct the customer, extract the
`person.`-prefixed entries, strip the prefix, and — only when the sub-map is
non-empty — delegate to the person patch service with its non-blank-only
semantics for names, identification number and e-mail and its unconditional
semantics for the two phone fields. An unchanged result would issue no write. No
controller route invokes any of it.

## W-CUSTOMER-04 Delete a customer

1. Operator `DELETE`s with a parameter map; authority `customers:delete`.
2. Empty map → nothing deleted, success response.
3. Zero matches → `Requested item(s) not found`.
4. Matching rows are deleted. Any customer referenced by an order aborts on the
   foreign-key constraint. The linked person row is left behind.

## W-CUSTOMER-05 Customer resolution during checkout (driven by `order`)

1. The order converter reads the customer block from the submitted order.
2. It resolves the customer by identification number through this domain's crud
   service.
3. Found → the existing customer is attached to the new order and the submitted
   names are discarded.
4. Not found → a new customer (and person) is created and attached.
5. The order's customer reference is mandatory, so this step cannot be skipped.

## W-CUSTOMER-06 Customer creation during self-registration (driven by `user`)

The registration flow's third write creates a customer for the newly persisted
person. Because registration is **not** transactional, a failure here leaves a
person and an account with no customer row — the account exists but cannot be
associated with orders through the normal resolution path until a customer is
created by some other route.

## W-CUSTOMER-07 Guest session (driven by `user`)

1. Client `POST`s a person profile to the guest route.
2. The guest filter calls this domain's create.
3. Already-exists → logged and tolerated; the pre-existing customer stands.
4. Bad input (for example a blank identification number) → surfaced as a
   bad-credentials condition with the message
   `Insufficient or invalid profile data for guest`.
5. The filter then authenticates as the configured guest account and issues a
   token.

## State model

`Customer` has no status column and no lifecycle states. Its only meaningful
distinction is **referenced versus unreferenced by an order**, which determines
whether it can be deleted:

```text
created            customer + person rows exist
referenced         at least one order points at it -> permanently undeletable
deleted            only possible while unreferenced; the person row survives
```

There is no deactivation, blocking or archival mechanism, so a customer who
should no longer be able to buy cannot be stopped at this level — only by
removing the associated account's role or the account itself.
