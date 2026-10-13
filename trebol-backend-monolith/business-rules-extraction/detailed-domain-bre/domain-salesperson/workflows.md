# Workflows — Salesperson (`salesperson`)

## W-SALESPERSON-01 Search salespeople

1. Operator issues `GET /data/salespeople` with an optional parameter map;
   authentication plus `salespeople:read` required.
2. Page index and size are resolved (defaults `0` and 10, hard cap 1000).
3. If the map is non-empty, `sortBy` / `order` are resolved against the seven
   person-backed sort keys and the remaining keys become filter conditions, all
   traversing the person association.
4. `id` or `idNumber` short-circuits to a single-identity match.
5. Each matched salesperson is converted to a **person** model; the salesperson
   identifier is not included.

## W-SALESPERSON-02 Register a salesperson

1. Operator `POST`s a person payload; authority `salespeople:create`.
2. Transport validation enforces non-blank first name, last name, identification
   number and e-mail.
3. The service rejects a blank identification number with
   `Salesperson does not have an ID card`.
4. The identification number is resolved through the join-fetching finder; a hit
   raises `The item already exists`.
5. Otherwise the payload is converted to a new salesperson carrying a new person,
   and the cascading association persists both rows in one transaction.
6. The saved salesperson is converted back to a person model and returned with
   status `201`.

Concurrency note: because `salespeople.person_id` has no unique constraint, two
simultaneous creates for the same identification number can both pass step 4 and
both insert, producing duplicate salesperson rows for one person.

## W-SALESPERSON-03 Replace a salesperson

1. Operator `PUT`s with a non-empty selecting parameter map plus a full person
   body; authority `salespeople:update`.
2. Empty parameters → `Missing request params`.
3. More than one match → `Cannot update more than one item at a time`; zero
   matches → `No element was found to update`.
4. The body becomes a new entity, the existing identifier is assigned, and the
   row is saved — a full overwrite of the person data.
5. Responds `204`.

Because there is no partial-update route (BR-SALESPERSON-010), this is the only
way to correct a salesperson's details, and it requires re-sending every
mandatory person field.

## W-SALESPERSON-04 Delete a salesperson

1. Operator `DELETE`s with a parameter map; authority `salespeople:delete`.
2. Empty map → nothing deleted, success response.
3. Zero matches → `Requested item(s) not found`.
4. Matching rows are deleted. The linked person row is left behind. A row
   referenced by an order would abort on the foreign-key constraint, but since no
   route populates that column the situation does not arise in practice.

## W-SALESPERSON-05 The unreachable partial-update path

The patch service is fully implemented: it copy-constructs the salesperson,
extracts the `person.`-prefixed entries from the change map using the hardcoded
prefix literal, and delegates them to the person patch service with its
non-blank-only semantics for names, identification number and e-mail and its
unconditional semantics for the two phone fields. No controller route invokes it.
Exposing it is a routing change, not a logic change.

## W-SALESPERSON-06 The unreachable order-attribution path

The order entity models a salesperson reference with a cascade set that includes
persist and merge, and the database carries the nullable column and its foreign
key. But:

- public checkout never populates the field;
- the order patch service exposes no key for it;
- no order filter or sort key references it.

So the full attribution chain exists in the data model and is dead at the API
boundary. A rewrite that wants order attribution must add the request field, the
converter logic and — if it is to be reportable — order filter and sort keys. It
should also decide whether to keep the cascade, which currently allows an order
write to create or modify a salesperson row without passing this domain's
validation.

## State model

`Salesperson` has no status column and no lifecycle states. Unlike `customer`,
whose referenced-by-an-order state makes it undeletable, a salesperson is in
practice always deletable because the referencing column is never populated:

```text
created        salesperson + person rows exist
deleted        always possible in a shipped deployment;
               the person row survives as an orphan
```

There is no deactivation or departure concept, so a salesperson who leaves can
only be deleted outright — which, if attribution were ever populated, would be
blocked by the foreign key with no soft-delete alternative.
