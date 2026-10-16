# Person Registry — Business Rules

Domain code: `PERSON`

Rule identifiers `BR-PERSON-001` … `BR-PERSON-013` are **carried forward unchanged** from
`business-rules-extraction/detailed-domain-bre/domain-person/business-rules.md`. They are
already cross-referenced by the Mode A traceability matrix, so they are not renumbered.
Identifiers `BR-PERSON-014` and above are new in this specification — findings that emerged
from the method-level analysis in documents `01` and `02`.

Each rule is a testable assertion. "Source" names the file and the construct that enforces
(or fails to enforce) it.

---

## Identity and uniqueness

### BR-PERSON-001 — A national ID number identifies at most one person

The system MUST NOT contain two person records with the same national identification
number.

- **Enforced by:** the unique key `UK_PERSON_ID_NO` on `people.person_id_number`
  (database), plus the application-level duplicate check in
  `PeopleCrudServiceImpl.getExisting` consumed by the generic pre-creation validation.
- **Test:** create a person with identification number `X`; create a second with the same
  `X`; the second MUST fail.
- **Note:** the database constraint is the real guarantee. The application check only
  produces a friendlier error and is not race-safe — see BR-PERSON-019.

### BR-PERSON-002 — A person submitted without an ID number is rejected

A creation request whose identification number is null, empty, or whitespace-only MUST be
rejected with a bad-input error carrying the literal message
`"Customer does not have ID card"`, mapped to HTTP 400.

- **Source:** `PeopleCrudServiceImpl.getExisting` — blankness test, not a null test.
- **Test:** attempt creation with `idNumber` of `""`, `"   "` and null; all three MUST
  produce the same 400 and the same message.

### BR-PERSON-003 — Creating a person that already exists is a conflict

When a creation request carries an identification number that already exists, the system
MUST reject it with an entity-already-exists error carrying the literal message
`"The item already exists"`, mapped to HTTP 409 — and MUST NOT modify the existing record.

- **Source:** generic pre-creation validation in the shared-kernel CRUD base, driven by
  `getExisting`.
- **Test:** create person `X`; re-submit a creation for `X` with a different name; expect
  409 and verify the stored name is unchanged.

### BR-PERSON-004 — Mandatory fields are enforced only at the route layer

First name, last name, identification number and e-mail MUST be present and non-blank on
a person submitted as a validated HTTP request body. On the internal creation path (a
consumer domain calling the CRUD service directly) only the identification number is
checked.

- **Source:** non-blank rules on `PersonPojo` fields (route layer) versus the single
  blank check in `PeopleCrudServiceImpl.getExisting` (service layer).
- **Test:** via the internal path, create a person with a valid identification number and a
  null first name — it MUST reach the database and fail there on the `NOT NULL` column,
  **not** with a validation error.
- **Rewrite note:** if these four become true domain invariants, the internal path gains
  validation it does not have today and consumer domains may start failing where they
  previously succeeded.

---

## Field content and validation

### BR-PERSON-005 — E-mail is length-constrained but never format-validated

The system MUST accept any non-blank e-mail value of 5 to 100 characters. It MUST NOT
apply any format check.

- **Source:** `Person.email` length rule (min 5, max 100); non-blank rule on
  `PersonPojo.email`. No format rule exists anywhere in the domain.
- **Test:** store `"abcde"` as an e-mail — it MUST be accepted.

### BR-PERSON-006 — Phone numbers are stored unvalidated and default to empty

Both phone numbers MUST be optional, MUST be stored without format or length validation
beyond the column's 255-character ceiling, and MUST default to the empty string when
omitted on creation.

- **Source:** `Person.phone1` / `phone2` builder defaults of `""`; null-guards in
  `PeopleConverterServiceImpl.convertToNewEntity`; the format rule on `PersonPojo` is
  **commented out and inert**.
- **Test:** create a person omitting both phones; both stored values MUST be `""`, not
  null. Create one with phone `"not-a-phone"`; it MUST be accepted.

### BR-PERSON-012 — Reading the registry requires an explicit authority

Listing people MUST require the authority literal **`people:read`**. An authenticated
caller lacking it MUST receive 403; an unauthenticated caller MUST receive 401.

- **Source:** method-level authority rule on `DataPeopleController.readMany`; class-level
  authentication requirement.
- **Test:** call `GET /data/people` anonymously (401), as an authenticated user without the
  authority (403), and with it (200).

---

## Patch semantics

### BR-PERSON-007 — Blank values never clear a stored name, ID number or e-mail

A patch supplying a blank value for `idNumber`, `firstName`, `lastName` or `email` MUST
leave the stored value unchanged, MUST NOT raise an error, and MUST report success.

- **Source:** `PeoplePatchServiceImpl.patchExistingEntity` — blank guard on each of the
  four fields.
- **Test:** patch `{"email": ""}` on an existing person; response MUST be successful and
  the stored e-mail MUST be unchanged.
- **Hazard:** this is silent. A client clearing a field believes it succeeded. Preserve the
  behaviour or make the rejection explicit — but do not change it accidentally.

### BR-PERSON-008 — Blank values DO clear a stored phone number

A patch supplying a blank value for `phone1` or `phone2` MUST overwrite the stored value
with that blank value.

- **Source:** same method — the two phone branches have **no** blank guard.
- **Test:** patch `{"phone1": ""}`; the stored phone MUST become `""`.
- **Note:** patching a phone to null instead produces a database constraint violation,
  because the column is `NOT NULL`. Blank-but-not-null is the only safe clearing value.

### BR-PERSON-009 — A patch that changes nothing performs no write

When the patched clone is field-for-field equal to the stored entity, the system MUST
return the stored entity without issuing a database write.

- **Source:** `flushPartialChanges` in the shared-kernel CRUD base — full-field equality
  comparison before save; depends on `Person` equality covering all seven fields.
- **Test:** patch a person with its own current values; no UPDATE statement MUST be issued.
- **Rewrite note:** redefining entity equality by identifier alone **silently disables all
  patching**. See document `01` §1.5.

### BR-PERSON-014 — Unrecognised patch keys are silently ignored

A patch map containing keys that are not one of the six known field names MUST be accepted,
with the unknown keys having no effect and producing no error or warning.

- **Source:** `PeoplePatchServiceImpl.patchExistingEntity` — the method tests only for the
  six known keys and never inspects the remainder of the map.
- **Test:** patch `{"nickname": "bob"}`; the call MUST succeed and change nothing (and, by
  BR-PERSON-009, MUST perform no write).

### BR-PERSON-015 — A non-string patch value fails as an unhandled error

A patch value that is not a string MUST fail. It MUST NOT produce a clean validation
error.

- **Source:** unchecked string casts in `PeoplePatchServiceImpl.patchExistingEntity`; the
  resulting cast failure is further wrapped by `partialUpdate` in the CRUD base.
- **Test:** patch `{"firstName": 42}`; expect an unhandled failure (HTTP 500), not a 400.
- **Rewrite note:** this is a defect worth fixing, but fixing it changes an observable
  status code from 500 to 400.

---

## Search and query semantics

### BR-PERSON-010 — Person identity filters discard all other filters

When the query parameters include `id` or `idNumber`, the system MUST filter on that
parameter alone and MUST ignore every other filter parameter in the request.

- **Source:** `PeoplePredicateServiceImpl.parseMap` — the `id` and `idNumber` cases return
  immediately from inside the iteration, discarding both accumulated and unvisited
  conditions.
- **Test:** request `?idNumber=X&email=nonexistent@example.com`; the person with
  identification number `X` MUST be returned despite the non-matching e-mail filter.
- **Hazard:** if both `id` and `idNumber` are supplied, which one wins depends on map
  iteration order and is **not deterministic**. A rewrite MUST choose a defined precedence.

### BR-PERSON-011 — A non-numeric value for a numeric filter is ignored, not rejected

A non-numeric `id` parameter MUST NOT fail the request. The parse failure MUST be logged at
info level and the parameter skipped, with the remaining parameters still applied.

- **Source:** `PeoplePredicateServiceImpl.parseMap` — number-format failure caught inside
  the loop, logged with parameter name and value, iteration continues.
- **Test:** request `?id=abc`; expect HTTP 200 with an unfiltered first page, not a 400.

### BR-PERSON-016 — Exact-match string filters are case-sensitive; partial-match filters are not

The filters `idNumber`, `firstName`, `lastName` and `email` MUST match exactly and
case-sensitively. The filters `nameLike`, `firstNameLike`, `lastNameLike`, `idNumberLike`
and `emailLike` MUST match as case-insensitive substring searches.

- **Source:** `PeoplePredicateServiceImpl.parseMap` — equality comparisons versus
  case-insensitive wildcard pattern matches.
- **Test:** for a person named `"Ana"`, `?firstName=ana` MUST return nothing while
  `?firstNameLike=ana` MUST return the person.

### BR-PERSON-017 — Pattern metacharacters in partial-match filters are not escaped

A user-supplied value for any `*Like` filter MUST be interpolated into the match pattern
without escaping, so metacharacters in the value act as wildcards.

- **Source:** `PeoplePredicateServiceImpl.parseMap` — the value is wrapped in leading and
  trailing wildcards with no escaping.
- **Test:** `?idNumberLike=%` MUST return every person.
- **Rewrite note:** values are bound as query parameters, so this is not SQL injection; it
  is a search-semantics flaw that lets a caller broaden a search. Escaping metacharacters
  is the correct fix.

### BR-PERSON-018 — Sorting is restricted to an allow-list, and an unknown key is a server error

Sorting MUST be permitted only on `idNumber`, `firstName`, `email`, `phone1`, `phone2`,
`name` and `lastName`, where `name` and `lastName` are aliases for the same ordering. There
MUST be no sort key for the surrogate identifier.

An unknown `sortBy` value, or a `sortBy` supplied without an `order` value, currently
produces an unhandled server error rather than being ignored or defaulted.

- **Source:** the immutable allow-list in `PeopleSortSpec`; the unchecked lookup result and
  unchecked direction switch in the shared-kernel sort parser.
- **Test:** `?sortBy=unknown` → 500; `?sortBy=email` with no `order` → 500;
  `?sortBy=email&order=asc` → 200.
- **Scope:** this defect is shared-kernel behaviour affecting every list endpoint, not only
  this domain. A rewrite SHOULD treat an unknown key as ignorable or a 400, and a missing
  direction as the declared ascending default.

---

## Lifecycle and data integrity

### BR-PERSON-013 — The registry has no delete or write route

The domain MUST expose exactly one HTTP route — `GET /data/people`. There MUST be no
create, update, patch, delete or single-fetch route.

- **Source:** `DataPeopleController` extends the **read-only** generic data controller and
  declares only the list operation.
- **Test:** enumerate the domain's routes; exactly one MUST be present.
- **Consequence:** no running code path deletes a person row, and no consumer domain
  cascades deletion, so orphaned person rows accumulate permanently. This is the current
  data-lifecycle behaviour, not a transient gap.
- **Extraction consequence:** write access exists today only as in-process calls from
  `user`, `customer` and `salesperson`. Extracting this domain into a service **requires
  adding a write API** that does not exist today.

### BR-PERSON-019 — The duplicate check is not race-safe

Two concurrent creations of the same identification number MAY both pass the application
duplicate check; the database unique constraint MUST reject the second write.

- **Source:** in `create`, the `getExisting` check and the insert are separate statements in
  one transaction with no lock between them.
- **Test:** issue two concurrent creations with the same identification number; exactly one
  MUST succeed, and the failure MAY surface as a raw constraint violation rather than the
  clean 409 of BR-PERSON-003.
- **Rewrite note:** keep the unique database constraint. It — not the application check —
  is what enforces BR-PERSON-001.

### BR-PERSON-020 — A person's surrogate identifier is never exposed externally

No response from this domain MUST include the person's surrogate key, and no external
caller MUST be able to supply it in a request body.

- **Source:** `PersonPojo` declares no identifier field;
  `PeopleConverterServiceImpl.convertToPojo` does not copy one.
- **Test:** inspect any `GET /data/people` response element; it MUST contain only
  `firstName`, `lastName`, `idNumber`, `email` and (when non-null) `phone1` / `phone2`.
- **Note:** the `id` *filter* parameter does accept an identifier (BR-PERSON-010), so the
  key is usable as an input filter but never returned. A caller that does not already know
  a key cannot discover one.

### BR-PERSON-021 — Full-record update replaces rather than merges

If a full-record update path is used, every field absent from the input MUST be overwritten
with null or its builder default, because the input is converted as a brand-new entity
before the identifier is forced onto it.

- **Source:** `update(input, id)` in the shared-kernel CRUD base — converts via
  `convertToNewEntity`, then sets the identifier.
- **Test:** not reachable over HTTP in this domain (BR-PERSON-013) and unused by consumer
  domains; verify by inspection.
- **Rewrite note:** if a write API is added during extraction, prefer the patch semantics
  (BR-PERSON-007/008/009) over this replace behaviour.

### BR-PERSON-022 — Deletion by filter has no upper bound

If a delete-by-filter path is used, it MUST reject an empty match with a not-found error
but MUST NOT limit how many records a non-empty match may remove.

- **Source:** `delete(filters)` in the shared-kernel CRUD base — guards only against a zero
  count, then deletes all matches as a batch.
- **Test:** unreachable in the running system (BR-PERSON-013); verify by inspection.
- **Rewrite note:** do not expose this behaviour on a new write API without bounding it.

---

## Error-reporting rules

### BR-PERSON-023 — This domain's not-found error is generic; its own exception type is raised only by the `user` domain

A not-found condition arising **within this domain** MUST surface the shared literal
message `"Requested item(s) not found"` mapped to HTTP 404, via the framework's generic
entity-not-found exception.

The domain's own `PersonNotFoundException` type MUST be raised at exactly one site, which
is **outside this domain**: `ProfileServiceImpl` in the `user` domain, with the literal
message `"The account does not have an associated profile"`, when an account exists but its
person association is null.

- **Source:** `readOne` and `delete` in the shared-kernel CRUD base throw the framework
  exception; the sole `PersonNotFoundException` throw site is
  `src/main/java/org/trebol/api/services/impl/ProfileServiceImpl.java:74`.
- **Test:** grep the codebase for `new PersonNotFoundException` — there MUST be exactly one
  match, and it MUST be in the `user` domain.
- **Rewrite note:** the type is a cross-domain contract. Extracting `person` as a service
  requires deciding where it lives, since the only code that throws it belongs to a
  different bounded context, and the condition it reports is a `user` invariant violation.

### BR-PERSON-024 — Two legacy operations always fail

The single-argument full update, and the model-based patch overload, MUST throw an
unsupported-operation error unconditionally with the literal messages
`"This method signature has been deprecated"`; the converter's apply-changes-to-existing
method MUST do the same with the literal message `"This method is deprecated"`.

- **Source:** `CrudGenericService.update(M)`,
  `PeoplePatchServiceImpl.patchExistingEntity(PersonPojo, Person)`,
  `PeopleConverterServiceImpl.applyChangesToExistingEntity`.
- **Test:** invoke each; all MUST fail.
- **Rewrite note:** omit all three from a rewrite. They are legacy interface surface whose
  only behaviour is failure.

## Cross-domain integration rules

### BR-PERSON-026 — Person writes bypass this domain's CRUD service entirely

Consumer domains MUST NOT be assumed to write person records through this domain's CRUD
service. In the current system **`PeopleCrudServiceImpl.create` has no caller anywhere** —
consumers write by combining this domain's converter with the repository directly.

| Write path | Mechanism | Duplicate guard used |
|---|---|---|
| Self-registration (`user`) | `PeopleConverterService.convertToNewEntity` then `PeopleRepository.saveAndFlush`, after its **own** existence check | `peopleRepository.exists(idNumber eq …)` with its own literal message `"That ID number is already registered and associated to an account."` |
| Profile update creating a profile (`user`) | `PeopleConverterService.convertToNewEntity` then `PeopleRepository.saveAndFlush` | `PeopleCrudService.getExisting` — the only live CRUD-service call in the system |
| Profile update linking an existing person (`user`) | `UsersRepository` association assignment | `usersRepository.findByPersonIdNumber`, message `"Person profile is associated to another account. Cannot use it."` |
| Customer / salesperson creation | `PeopleConverterService.convertToNewEntity` via the consumer's own converter, persisted by the consumer's cascade | none in this domain |

- **Source:** the complete call-site census — `peopleService.getExisting` at
  `ProfileServiceImpl:87`; `peopleRepository.saveAndFlush` at `ProfileServiceImpl:99,105`
  and `RegistrationServiceImpl:92`; `peopleRepository.exists` at
  `RegistrationServiceImpl:89`; `peopleRepository.findByIdNumber` at
  `UsersConverterServiceImpl:101`; converter calls at `ProfileServiceImpl:76,98`,
  `RegistrationServiceImpl:86`, `CustomersConverterServiceImpl:46,51`,
  `SalespeopleConverterServiceImpl:46,51`, `UsersConverterServiceImpl:74`.
- **Test:** grep for a call to `create(` on the people CRUD service — there MUST be none.
- **Consequence:** BR-PERSON-002 and BR-PERSON-003 (the `"Customer does not have ID card"`
  and `"The item already exists"` messages) govern **almost nothing in practice**. The
  duplicate-detection message a client actually sees on registration is the `user` domain's
  own. Only `getExisting` is genuinely live, and only from the profile-update path.
- **Rewrite note:** this is the most important structural finding for an extraction. The
  domain's nominal service API is largely unused; the real integration surface is the
  **converter plus the repository**, used directly by three other domains. Replacing
  in-process access with a service boundary therefore breaks consumers at the *repository*
  level, not at the service level, and each consumer's own duplicate check must be
  reconciled with the service's.

### BR-PERSON-027 — Updating an existing profile always fails

When an account already has an associated person, the profile-update path MUST fail
unconditionally with an unsupported-operation error carrying the literal message
`"This method signature has been deprecated"`, surfacing as HTTP 500.

- **Source:** `ProfileServiceImpl:104` calls
  `peoplePatchService.patchExistingEntity(profile, target)` — the **transport-model
  overload**, which this domain implements as an unconditional throw
  (`PeoplePatchServiceImpl`, see BR-PERSON-024). The map-based overload that actually works
  is never reached from this path.
- **Test:** authenticate as an account that already has a profile, submit a profile update;
  the call MUST fail with 500. Compare with an account that has **no** profile, where the
  create-or-link branch at `ProfileServiceImpl:87–101` succeeds.
- **Assessment:** this is a **live defect in the running system**, not a latent one. Users
  who already have a profile cannot edit it at all. The working map-based path exists and
  is used correctly by `CustomersPatchServiceImpl:56` and
  `SalespeoplePatchServiceImpl:54`; only the profile path calls the dead overload.
- **Rewrite note:** fix by routing the profile path through the map-based overload with the
  `person.` prefix convention (BR-PERSON-028). Note that fixing it *enables* a flow that
  currently always fails, so the patch semantics of BR-PERSON-007/008/009 become observable
  for the first time on that path.

### BR-PERSON-028 — Nested person patches use the `person.` key prefix

A consumer patching a person nested inside its own aggregate MUST pass change-map keys
prefixed with the literal string `"person."`, which the consumer strips before delegating to
this domain's map-based patch service.

- **Source:** the constant `PERSON_DATA_MAP_KEYS_PREFIX = "person."` in
  `src/main/java/org/trebol/config/Constants.java:47`, consumed by
  `CustomersPatchServiceImpl:53` and `SalespeoplePatchServiceImpl` via a map-copy helper
  that selects and unprefixes matching entries.
- **Test:** patch a customer with `{"person.firstName": "Ana"}`; the person's first name
  MUST change. Patch with `{"firstName": "Ana"}`; the person MUST be unchanged, because the
  unprefixed key does not match the convention and the extracted person change map is
  empty.
- **Note:** when the extracted person change map is empty, the consumer skips the person
  patch entirely — so no person write occurs.
- **Rewrite note:** this string prefix is a structural coupling between three domains. A
  service extraction should replace it with a nested object in the request contract rather
  than reproduce prefix-encoded flat keys.

---

## Additional lifecycle rule

### BR-PERSON-025 — A multi-match update or patch by filter is a server error

If a patch-by-filter or update-by-filter path is used and the filter matches more than one
person, the system MUST fail with an unchecked error carrying the literal message
`"Cannot update more than one item at a time"`, which has no mapped status and therefore
surfaces as HTTP 500.

- **Source:** `partialUpdate(changes, filters)` and `update(input, filters)` in the
  shared-kernel CRUD base — a plain runtime exception, not a typed bad-input exception.
- **Test:** unreachable over HTTP in this domain (BR-PERSON-013); verify by inspection.
- **Note:** a match count of **zero** is not an error — it falls through and returns an
  empty result. Only two or more matches fail.
- **Rewrite note:** this describes a client error (an insufficiently selective filter) but
  reports a server error. If a write API is added during extraction, map it to 400 or 409.

---

## Rule coverage summary

| Category | Rules |
|---|---|
| Identity and uniqueness | BR-PERSON-001, 002, 003, 004 |
| Field content and validation | BR-PERSON-005, 006, 012 |
| Patch semantics | BR-PERSON-007, 008, 009, 014, 015 |
| Search and query semantics | BR-PERSON-010, 011, 016, 017, 018 |
| Lifecycle and data integrity | BR-PERSON-013, 019, 020, 021, 022, 025 |
| Error reporting | BR-PERSON-023, 024 |
| Cross-domain integration | BR-PERSON-026, 027, 028 |
| **Total** | **28** |

Of these, 13 are carried forward from the Mode A catalog and 15 are new findings from
method-level and call-site analysis.

## Corrections to the Mode A catalog

Two claims in `detailed-domain-bre/domain-person/business-rules.md` did not survive
call-site verification and are superseded here:

| Mode A claim | Correction | Rule |
|---|---|---|
| `PersonNotFoundException` is never thrown anywhere. | It has exactly one throw site, `ProfileServiceImpl:74` in the `user` domain, with the message `"The account does not have an associated profile"`. | BR-PERSON-023 |
| Person creation flows through this domain's CRUD service (`create`). | `create` has **no caller anywhere**. Consumers use the converter plus the repository directly, each with its own duplicate guard and message. | BR-PERSON-026 |

The second correction also narrows BR-PERSON-002, BR-PERSON-003 and BR-PERSON-019: they
describe the CRUD service's behaviour accurately but that behaviour governs almost no real
traffic, since only `getExisting` is reached from live code.
