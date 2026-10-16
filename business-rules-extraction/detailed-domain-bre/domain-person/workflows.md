# Workflows — Person Registry (`person`)

## W-PERSON-01 Paged person search

1. Caller issues `GET /data/people` with an optional parameter map.
2. The generic read controller asks the pagination service for the page index
   (parameter `pageIndex`, default `0`) and page size (parameter `pageSize`,
   default from `trebol.api.items-per-page` = 10, capped by
   `trebol.api.max-allowed-page-size` = 1000).
3. If the parameter map is non-empty, the sort-spec parser resolves `sortBy` /
   `order` against the person sort whitelist and the predicate service builds the
   filter expression.
4. The crud service counts matching rows, fetches the page, converts each entity
   to its transport model and returns the page envelope.

Failure modes: a non-numeric `pageIndex`/`pageSize` propagates a number-format
failure (uncaught, 500-class); an unrecognised `sortBy` resolves to a null order
specifier inside the sort parser and fails with a null-dereference; an absent
`order` key with a present `sortBy` does the same.

## W-PERSON-02 De-duplicating a person during registration (driven by `user`)

1. The registration service converts the submitted profile into a new person.
2. It queries for an existing person with the same ID number.
3. If one exists, registration aborts with the already-exists error
   `That ID number is already registered and associated to an account.`
4. Otherwise the person row is saved and flushed, then linked to the new account.

## W-PERSON-03 Attaching an existing person to an account (driven by `user`)

1. The profile-update service loads the account by user name.
2. The account has no person yet: it looks for an existing person matching the
   submitted profile by ID number.
3. Found, and some other account already owns that ID number → reject with
   `Person profile is associated to another account. Cannot use it.`
4. Found and unowned → attach it to this account.
5. Not found → create, save and attach a new person row.

## W-PERSON-04 Patching the person behind an account (driven by `user`)

1. The account already has a person.
2. The person patch service copy-constructs the existing person, applies the
   non-blank name/ID/e-mail fields and the unconditional phone fields.
3. The account (and its cascaded person) is saved.

## W-PERSON-05 Nested person patch through a customer or salesperson

1. Caller patches `/data/customers` or `/data/salespeople` with keys prefixed
   `person.` (for example `person.email`).
2. The owning patch service strips the prefix into a sub-map.
3. If the sub-map is empty the person is left untouched; otherwise this domain's
   patch service is invoked with it.

Caveat: the prefix-stripping utility replaces **every** occurrence of the prefix
in the key, not only the leading one, so a key such as `person.person.email`
collapses unexpectedly.

## State model

`Person` has no status column and no lifecycle states. Rows are created once and
mutated in place; nothing in the codebase deletes a person row.
