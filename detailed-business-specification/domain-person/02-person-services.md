# 02 — Person Services, Repository and Controller

Source files covered:

| File | LOC | Role |
|---|---|---|
| `src/main/java/org/trebol/jpa/repositories/PeopleRepository.java` | 34 | repository |
| `src/main/java/org/trebol/jpa/services/conversion/PeopleConverterService.java` | 29 | service contract |
| `src/main/java/org/trebol/jpa/services/conversion/impl/PeopleConverterServiceImpl.java` | 72 | service implementation |
| `src/main/java/org/trebol/jpa/services/crud/PeopleCrudService.java` | 29 | service contract |
| `src/main/java/org/trebol/jpa/services/crud/impl/PeopleCrudServiceImpl.java` | 64 | service implementation |
| `src/main/java/org/trebol/jpa/services/patch/PeoplePatchService.java` | 29 | service contract |
| `src/main/java/org/trebol/jpa/services/patch/impl/PeoplePatchServiceImpl.java` | 87 | service implementation |
| `src/main/java/org/trebol/jpa/services/predicates/PeoplePredicateService.java` | 29 | service contract |
| `src/main/java/org/trebol/jpa/services/predicates/impl/PeoplePredicateServiceImpl.java` | 88 | service implementation |
| `src/main/java/org/trebol/jpa/sortspecs/PeopleSortSpec.java` | 43 | helper |
| `src/main/java/org/trebol/api/controllers/DataPeopleController.java` | 73 | controller |

---

## 0. How this domain is assembled

The domain follows a fixed five-part pattern used throughout the codebase. Each part is a
narrow interface plus one implementation, wired by dependency injection:

| Part | Responsibility |
|---|---|
| repository | persistence access; the only component that touches the database |
| converter | entity ⇄ transport model translation |
| patch service | apply a key/value change map to a cloned entity |
| CRUD service | orchestrate create/read/update/delete; inherits most behaviour from a shared-kernel generic base |
| predicate service | translate query-string parameters into filter conditions |

Plus a sort-specification helper (an allow-list of sortable fields) and a controller.

**A rewrite must understand where the behaviour actually lives.** The four domain-specific
contracts (`PeopleConverterService`, `PeopleCrudService`, `PeoplePatchService`,
`PeoplePredicateService`) are *empty marker interfaces* — they declare no methods of their
own and exist only to bind the generic shared-kernel contracts to this domain's concrete
model and entity types, so the injection container can resolve them unambiguously. In a
language without this constraint they may collapse away entirely. The real behaviour is
split between the shared-kernel generic base (§3.3–§3.7, §6.2) and the four
domain-specific overrides (§3.1, §4.1, §5.1, §7.1).

Transaction scope: the CRUD service implementation is transactional at class level, and so
is the generic base it extends. Every public CRUD method therefore runs in a transaction.
The converter, patch and predicate services are not transactional — they are pure
in-memory translators.

---

## 1. `PeopleConverterService` / `PeopleCrudService` / `PeoplePatchService` — contracts

Each is an empty interface extending the corresponding shared-kernel generic contract with
this domain's type pair (transport model `PersonPojo`, entity `Person`). They declare no
methods, no constants, and no default implementations. No error paths. They exist purely
for type binding, as described in §0.

Their generic parents are specified in `domain-decomposition-overview/shared-kernel.md`.

---

## 2. `PeopleRepository`

### 2.1 Overview

The persistence contract for `Person`. It extends the shared-kernel generic repository,
inheriting the full standard surface — find by identifier, find all, paged find all,
count, count matching a predicate, find one matching a predicate, find all matching a
predicate, save, save-and-flush, delete, delete all — and adds exactly one domain-specific
query method.

### 2.2 `findByIdNumber(String idNumber)`

**Purpose.** Look up a person by national identification number — the domain's business
key. This single method is the foundation of the domain's central invariant: it is how the
system answers "does this person already exist?".

**Parameters.** `idNumber` — the national identification number to match. Matching is
**exact and case-sensitive**: no trimming, no normalization, no case folding. The query is
derived from the method name, so the generated condition is a simple equality on
`person_id_number`.

**Return value.** An optional container holding the matching person, empty if none matches.
Because the column is unique at the database level, at most one row can match, so the
optional is a safe return type.

**Side effects.** None — read-only.

**Error paths.** A null argument produces a query for a null identification number, which
matches nothing (the column is `NOT NULL`), returning empty rather than failing. No
exception is declared or thrown. Callers guard against blank input themselves (§3.1).

**Query characteristics.** Single-table, no joins, indexed lookup against both the unique
key and the redundant secondary index on `person_id_number`.

### 2.3 Inherited surface actually exercised

Of the inherited methods, this domain's code paths use: `findByIdNumber` (§3.1),
`findById` (§3.4 via the generic base), `findOne` with a predicate, `findAll` with
pagination, `findAll` with predicate and pagination, `count`, `count` with a predicate,
`saveAndFlush`, `deleteAll`. The remainder are available but unreachable from this
domain's own routes — see §7.3 on why most write and delete capability is unreachable
externally.

---

## 3. `PeopleCrudServiceImpl`

### 3.1 `getExisting(PersonPojo input)` — the only domain-specific override

**Purpose.** Resolve an inbound transport model to the existing person it refers to, if
any. This is the domain's "identifying property" resolution and the hook the generic base
calls to detect duplicates before creating.

**Parameters.** `input` — a transport model. The method reads **only** its `idNumber`
field; every other field is ignored.

**Behaviour, branch by branch.**

1. Read `idNumber` from the input model into a local variable.
2. **If the value is blank** — null, empty, or consisting only of whitespace — throw a
   bad-input exception with the exact message `"Customer does not have ID card"` and
   return nothing. Note that blankness, not merely nullness, is the test: `"   "` is
   rejected.
3. **Otherwise** delegate to the repository's `findByIdNumber` and return its result
   directly — an optional that is present if a person with that identification number
   exists and empty otherwise.

**Return value.** An optional holding the matched entity, or empty when no person carries
that identification number.

**Side effects.** None beyond the read.

**Error paths.**

| Condition | Outcome |
|---|---|
| `idNumber` is null, empty, or whitespace-only | bad-input exception, message `"Customer does not have ID card"` → **HTTP 400** |
| `idNumber` is present but matches no row | no exception; returns empty optional |

**Known quirk — wrong noun in the error message.** The message says *"Customer* does not
have ID card" inside the *person* domain. There is a separate `customer` domain in this
system, so the message is actively misleading: a client receiving it may reasonably look
for a customer-related fault. The message is nonetheless part of the observable API
contract today. A rewrite should either preserve the literal string or treat changing it
as a deliberate breaking change to error output. The literal is pinned by BR-PERSON-002.

**Rewrite note.** This method is the single most important piece of domain-specific logic
in the service layer. Everything else the CRUD service does is inherited generic
behaviour, parameterised by this one definition of "what identifies a person".

### 3.2 `create(PersonPojo input)` — inherited, behaviour as specialised here

Defined on the shared-kernel generic base; its behaviour in this domain is fully determined
by §3.1 and the converter (§4).

**Behaviour, in order.**

1. **Duplicate guard.** Call the generic pre-creation validation routine, which calls
   `getExisting(input)` (§3.1). If it returns a present optional, throw an
   entity-already-exists exception with the shared literal message
   `"The item already exists"`. If `getExisting` throws bad-input (blank identification
   number), that propagates unchanged.
2. **Convert.** Call the converter's `convertToNewEntity` (§4.2) to build a new, unsaved
   entity from the transport model.
3. **Persist.** Save and flush the entity through the repository. The database assigns the
   identifier. The immediate flush means any constraint violation — including the unique
   identification-number constraint — surfaces here rather than at transaction commit.
4. **Convert back.** Call the converter's `convertToPojo` (§4.3) on the saved entity and
   return the result.

**Return value.** A transport model reflecting the persisted state. **It does not include
the newly assigned identifier**, because the transport model has no identifier field
(document `01` §2.1) and the converter does not copy one. A caller that creates a person
therefore cannot learn its key — it must re-query by identification number. This is a real
constraint on any consumer design.

**Side effects.** One row inserted into `people`. No events published, no cache
invalidated, no notification sent, no audit record written.

**Error paths.**

| Condition | Outcome |
|---|---|
| blank identification number | bad-input exception, `"Customer does not have ID card"` → 400 |
| person with that identification number already exists | entity-already-exists exception, `"The item already exists"` → **HTTP 409 Conflict** |
| a phone field is null (reachable only via a non-builder construction path) | database constraint violation at flush |
| name/e-mail exceeding the column length | database constraint violation at flush |

**Concurrency.** The duplicate check and the insert are two separate statements inside one
transaction with no lock taken between them. Two concurrent creations of the same
identification number can both pass the check; the second insert then fails on the unique
constraint. The surfaced error differs by race outcome — a clean 409 if serialized, a raw
constraint violation if interleaved. The unique database constraint is what actually
guarantees the invariant; the application check only produces the friendlier error.

**Reachability.** No HTTP route reaches this method in this domain (§7.3), **and no
in-process caller reaches it either — `create` has no call site anywhere in the codebase.**
Consumer domains write person rows by combining this domain's converter with the repository
directly, each applying its own duplicate guard with its own error message. See
BR-PERSON-026 for the full call-site census. The behaviour above is specified because a
rewrite that adds a write API will need it, not because it governs current traffic.

### 3.3 `readMany(int pageIndex, int pageSize, Sort order, Predicate filters)` — inherited

**Purpose.** Return one page of people, optionally filtered and sorted.

**Parameters.** `pageIndex` — zero-based page number. `pageSize` — items per page.
`order` — optional sort specification; null means unsorted. `filters` — optional filter
condition; null means unfiltered.

**Behaviour, branch by branch.**

1. Build a pagination request from index and size; include the sort specification only if
   `order` is non-null.
2. **Count.** If `filters` is null, count all rows; otherwise count rows matching the
   filter. This count is the reported total, and it is a **second database round trip**
   independent of the page fetch.
3. **Fetch.** If `filters` is null, fetch the page unfiltered; otherwise fetch the page
   with the filter applied.
4. **Convert.** Iterate the fetched entities in order and convert each to a transport
   model via §4.3, accumulating into a list.
5. Return a page container holding the converted list, the page index, the total count and
   the page size.

**Return value.** A page container. Note the total count is the count of *matching* rows,
not of returned rows.

**Side effects.** None — read-only. Two queries per call (count, then fetch).

**Error paths.** No domain exception. A page index beyond the end yields an empty list with
a non-zero total rather than an error. A negative index or non-positive size is rejected by
the pagination framework before this method is reached.

**Note.** The count-then-fetch pair is not executed atomically with respect to concurrent
writes, so a page can in principle report a total inconsistent with its contents. Benign
here, since nothing in this domain writes frequently.

### 3.4 `partialUpdate(Map<String,Object> changes, Long id)` — inherited

**Purpose.** Apply a sparse set of field changes to one person identified by key.

**Behaviour.**

1. Find the person by identifier. **If absent, return an empty optional** — no exception.
   The caller decides how to interpret a miss.
2. If present, call the shared partial-change flush (§3.6) with the change map and the
   loaded entity.
3. Convert the flush result to a transport model and return it wrapped in an optional.

**Error paths.** A bad-input exception raised by the patch service is caught and
**rethrown wrapped in an unchecked runtime exception**. This is a deliberate consequence of
the functional mapping style used in the implementation, and it means patch validation
failures do not surface as clean 400 responses from this path — they surface as unhandled
runtime failures, typically **HTTP 500**. For this domain the patch service never actually
throws (§5.1), so the path is currently unreachable; a rewrite that adds patch validation
must fix the wrapping or it will emit 500s where 400s are intended. See BR-PERSON-015 for
the one reachable case (a non-string patch value).

### 3.5 `partialUpdate(Map<String,Object> changes, Predicate filters)` — inherited

**Purpose.** Apply sparse changes to the single person matching a filter.

**Behaviour.**

1. Count rows matching the filter.
2. **If the count exceeds one**, throw a plain runtime exception with the message
   `"Cannot update more than one item at a time"`. This is an unchecked exception with no
   mapped status, so it surfaces as **HTTP 500** despite describing what is really a client
   error. Recorded as BR-PERSON-025.
3. Find the one matching row. If absent, return an empty optional.
4. Flush partial changes (§3.6), convert, return — identical to §3.4 from here, including
   the same bad-input wrapping behaviour.

**Note.** A count of zero falls through to step 3 and returns empty. Only a count of two or
more is treated as an error.

### 3.6 `flushPartialChanges(...)` — inherited, protected

**Purpose.** Apply a change map and persist only if something actually changed.

**Behaviour.**

1. Call the patch service's `patchExistingEntity` (§5.1) with the change map and the
   existing entity. It returns a **clone** carrying the requested changes.
2. **Compare** the original entity to the returned clone using full-field equality.
3. **If equal, return the original immediately without any database write.**
4. Otherwise save and flush the clone and return the saved result.

This is the implementation of BR-PERSON-009 (a no-op patch performs no write) and it
depends entirely on the entity's all-fields equality semantics — see document `01` §1.5 for
why redefining equality breaks this.

**Error paths.** Propagates bad-input from the patch service to the caller, which wraps it
(§3.4). Constraint violations surface at flush.

### 3.7 `readOne(Predicate filters)` — inherited

**Purpose.** Fetch exactly one person matching a filter.

**Behaviour.** Find one matching row. **If absent, throw the framework's generic
entity-not-found exception with the shared literal message `"Requested item(s) not
found"`** → HTTP 404. If present, convert and return.

**Known quirk.** This is where the domain's 404 originates — **not** from
`PersonNotFoundException`, which is never thrown (document `01` §3.4). The message is the
generic shared one, with no mention of a person.

**Note.** If more than one row matches, the underlying find-one call fails rather than
returning the first match. This domain's filters include an exact identification-number
case that can match at most one row, but name-based filters can match many, making this
reachable in principle.

### 3.8 `update(PersonPojo input, Long id)` and `update(PersonPojo input, Predicate)` — inherited

**Purpose.** Full-record replacement.

**Behaviour (by identifier).** Convert the input model to a *new* entity, force the given
identifier onto it, save and flush, convert back, return wrapped in an optional.

**⚠ Destructive semantics.** Because the input is converted as a brand-new entity, any
field absent from the input model becomes null or the builder default on the saved row
— this is a true replace, not a merge. Combined with the transport model's lack of an
identifier field, a caller cannot even express "update person 5" through the public model;
the identifier has to be supplied out of band. For this domain the method is unreachable
externally (§7.3) and no internal caller uses it.

**Behaviour (by filter).** Count matches; if more than one, throw the same plain runtime
exception as §3.5; if none, return empty; otherwise take the matched row's identifier and
delegate to the by-identifier variant above.

### 3.9 `update(PersonPojo input)` — inherited, unsupported

Single-argument update. The generic base **throws an unsupported-operation exception with
the message `"This method signature has been deprecated"`** unconditionally. It is retained
for interface compatibility only. A rewrite should omit it entirely rather than reproduce a
method that always fails.

### 3.10 `delete(Predicate filters)` — inherited

**Behaviour.** Count matching rows. **If zero, throw entity-not-found** with the shared
message `"Requested item(s) not found"` → 404. Otherwise fetch all matching rows and delete
them as a batch.

**⚠ Mass-deletion hazard.** The guard checks only that the match is non-empty — there is no
upper bound. A filter matching many people deletes all of them in one call. A permissive
filter would delete the entire registry.

**Reachability.** No route exposes this (§7.3), and no consumer domain calls it. **Nothing
in the running system ever deletes a person row.** Combined with the absence of delete
cascades in the three consumer domains, orphaned person rows accumulate permanently. This
is the known data-lifecycle gap recorded in the domain's manifest notes and as
BR-PERSON-013.

### 3.11 Constructor

Takes the repository, the converter service and the patch service. Passes all three to the
generic base, and additionally retains the repository in its own field — the base keeps it
privately, so the override in §3.1 needs its own reference to call the domain-specific
query method. No other side effects.

---

## 4. `PeopleConverterServiceImpl`

### 4.1 Overview

Bidirectional translation between entity and transport model. Stateless, non-transactional,
no injected dependencies, no logging. Three methods, one of which always fails.

### 4.2 `convertToNewEntity(PersonPojo source)`

**Purpose.** Build a new, unsaved entity from a transport model, for the creation path.

**Behaviour, branch by branch.**

1. Build an entity via its builder, setting `firstName`, `lastName`, `idNumber` and
   `email` from the source. **The identifier is not set** — it stays null so the database
   assigns it on insert. Because the builder is used, both phone fields receive the empty
   string default at this point.
2. **If the source's `phone1` is non-null**, overwrite `phone1` with it. If null, the
   empty-string default stands.
3. **If the source's `phone2` is non-null**, overwrite `phone2` with it. If null, the
   empty-string default stands.
4. Return the entity.

**Return value.** An unsaved entity with a null identifier.

**Side effects.** None. Does not persist.

**Error paths.** The contract declares a bad-input exception, but **this implementation
never throws it**. A null source fails with a null dereference. Null required fields pass
through silently and fail later at the database. There is no validation in this method
whatsoever.

**Behavioural consequence.** The null-guards in steps 2–3 are precisely what make an
omitted phone become `""` rather than null, keeping the `NOT NULL` columns satisfiable.
They are not cosmetic — removing them reintroduces the null-phone constraint failure
described in document `01` §1.4.2. A null phone can never be *written* through this path.

### 4.3 `convertToPojo(Person source)`

**Purpose.** Produce the external representation of a stored person. Used by every read
path.

**Behaviour, branch by branch.**

1. Build a transport model via its builder from `idNumber`, `firstName`, `lastName` and
   `email`. **The identifier is deliberately not copied** — the transport model has no such
   field.
2. **If the entity's `phone1` is non-null**, set it on the model; otherwise leave the
   model's field null, which means it is **omitted** from serialized output.
3. **If the entity's `phone2` is non-null**, same treatment.
4. Return the model.

**Return value.** A transport model. Never null for a non-null input.

**Side effects.** None.

**Error paths.** None. A null source fails with a null dereference; the contract's
"returns null if not persistent" note is **not** implemented — there is no null check.

**Information-hiding consequence.** Since the identifier is never emitted, no response
anywhere in the system reveals a person's surrogate key. Any rewrite that adds an
identifier to the response changes the external contract and the domain's identity model.

### 4.4 `applyChangesToExistingEntity(PersonPojo source, Person target)` — unsupported

**Behaviour.** **Throws an unsupported-operation exception with the exact message
`"This method is deprecated"`** unconditionally. No parameter is examined.

**Purpose of its existence.** The shared-kernel converter contract still declares the
method, marked for removal; the patch service (§5) superseded it. Every converter in the
codebase stubs it out the same way.

**Rewrite guidance.** Do not implement it as a working method. **But note it is not
unreachable:** `ProfileServiceImpl:104` in the `user` domain calls this overload on the
*patch* service (the structurally identical stub, §5.2), which makes profile updates for
accounts that already have a profile fail unconditionally — BR-PERSON-027. Before deleting
these stubs, check that no live caller depends on reaching them.

---

## 5. `PeoplePatchServiceImpl`

### 5.1 `patchExistingEntity(Map<String,Object> changes, Person existing)`

**Purpose.** Produce a modified **clone** of a stored person from a sparse map of changes.
Cloning (rather than mutating) is what allows the caller to detect "nothing changed" and
skip the write (§3.6).

**Parameters.** `changes` — field name to new value. Keys are **transport-model field
names**, not column names. `existing` — the loaded entity; never mutated.

**Behaviour, branch by branch.** First, clone `existing` via the entity copy constructor
(document `01` §1.4.1) — identifier included. Then, for each of the six fields in this
fixed order:

| # | Key | Behaviour when key present |
|---|---|---|
| 1 | `idNumber` | Cast the value to a string. **If not blank, set it. If blank, leave the clone's existing value untouched.** |
| 2 | `firstName` | Same blank-guarded behaviour. |
| 3 | `lastName` | Same blank-guarded behaviour. |
| 4 | `email` | Same blank-guarded behaviour. |
| 5 | `phone1` | Cast to a string and **set unconditionally — no blank guard**. |
| 6 | `phone2` | Cast to a string and **set unconditionally — no blank guard**. |

Keys absent from the map are not touched. Unrecognised keys are **silently ignored** — no
error, no warning, no log entry. Finally, return the clone.

**Return value.** A clone carrying the applied changes, with the same identifier as the
original. Never null.

**Side effects.** None. Does not persist; does not modify `existing`.

**Error paths.**

| Condition | Outcome |
|---|---|
| a value is not a string | **unchecked class-cast failure** — not the declared bad-input exception. Surfaces as HTTP 500, and from the `partialUpdate` path is additionally wrapped (§3.4). |
| a key is unrecognised | silently ignored |
| a value is blank for one of the four guarded fields | silently ignored, previous value retained |
| `existing` is null | null dereference in the copy constructor |

The declared bad-input exception is **never thrown** by this implementation.

**The asymmetry is the important part.** Four fields cannot be blanked; two can. So:

- Sending `{"email": ""}` is a **silent no-op** — the stored e-mail survives, and because
  the clone then equals the original, no write occurs at all and the caller gets a success
  response. A client attempting to clear a field will believe it succeeded. This is
  BR-PERSON-007, and it is the most likely source of surprise in the domain.
- Sending `{"phone1": ""}` **does** clear the stored phone. This is BR-PERSON-008.

The asymmetry is defensible — the four guarded fields are `NOT NULL` business-critical
data, the phones are optional — but it is undocumented in the source and silent in
operation. A rewrite should preserve the behaviour and, ideally, make the rejection
explicit rather than silent.

**Null handling for phones.** A null value for `phone1` or `phone2` is cast to a null
string and set unconditionally, producing a null in a `NOT NULL` column and a constraint
violation at flush. A null for one of the four guarded fields is caught by the blank test
and ignored. So `{"phone1": null}` fails at the database while `{"email": null}` is a
no-op — another asymmetry worth preserving deliberately or fixing deliberately.

### 5.2 `patchExistingEntity(PersonPojo changes, Person existing)` — unsupported, but called

**Throws an unsupported-operation exception with the exact message `"This method signature
has been deprecated"`** unconditionally. The map-based overload above replaced it.

**⚠ This stub has a live caller.** `ProfileServiceImpl:104` in the `user` domain invokes
exactly this overload when an account already has an associated person. The consequence is
that **profile updates for any user who already has a profile fail with HTTP 500** — a live
defect in the running system, recorded as BR-PERSON-027. The working map-based overload is
used correctly elsewhere, by `CustomersPatchServiceImpl:56` and
`SalespeoplePatchServiceImpl:54` with the `person.` key prefix (BR-PERSON-028).

A rewrite should omit this overload **and** fix the profile path to use the map-based one.
Omitting it without fixing the caller moves the failure from a runtime exception to a
compile error, which is an improvement but still a change the `user` domain must absorb.

---

## 6. `PeoplePredicateService` and `PeoplePredicateServiceImpl`

### 6.1 Contract

Unlike the other three contracts, `PeoplePredicateService` is not entirely empty: it
declares a single constant, the QueryDSL base path for the person entity, which the
implementation uses to construct conditions. This is an idiom for sharing the query root
through the interface. In a rewrite, this is simply "the table/alias the conditions are
built against" and needs no separate declaration.

### 6.2 `parseMap(Map<String,String> queryParamsMap)`

**Purpose.** Translate raw query-string parameters into a database filter condition. This
is the domain's entire external search capability.

**Parameters.** `queryParamsMap` — the raw query parameters, including ones this method
does not recognise (such as pagination and sort keys).

**Behaviour.** Create an empty condition accumulator, then iterate the entries. **Iteration
order is the map's, which is not guaranteed** — this matters, see the short-circuit note
below. For each entry, match the parameter name:

| Parameter | Condition | Combination |
|---|---|---|
| `id` | `person_id` equals the value parsed as a 64-bit integer | **returns immediately** |
| `idNumber` | `person_id_number` equals the value exactly | **returns immediately** |
| `name` | `person_first_name` equals the value **OR** `person_last_name` equals the value | AND-ed into the accumulator |
| `firstName` | `person_first_name` equals the value | AND-ed |
| `lastName` | `person_last_name` equals the value | AND-ed |
| `email` | `person_email` equals the value | AND-ed |
| `nameLike` | `person_first_name` contains the value (case-insensitive) **OR** `person_last_name` contains the value (case-insensitive) | AND-ed |
| `firstNameLike` | `person_first_name` contains the value, case-insensitive | AND-ed |
| `lastNameLike` | `person_last_name` contains the value, case-insensitive | AND-ed |
| `idNumberLike` | `person_id_number` contains the value, case-insensitive | AND-ed |
| `emailLike` | `person_email` contains the value, case-insensitive | AND-ed |
| anything else | no condition | silently ignored |

"Contains, case-insensitive" is implemented as a case-insensitive pattern match with the
value wrapped in leading and trailing wildcards. The four exact-match string comparisons
(`idNumber`, `firstName`, `lastName`, `email`) are **case-sensitive**; their `*Like`
counterparts are not. A rewrite must preserve this distinction per parameter — it is not
uniform.

After the loop, return the accumulated condition. An accumulator with nothing added yields
a condition that matches everything, so an unfiltered request returns the whole registry
one page at a time.

**⚠ The identity short-circuit.** The `id` and `idNumber` cases **return immediately from
inside the loop**, discarding both any conditions already accumulated and every parameter
not yet visited. So `?idNumber=X&email=Y` filters **only** by identification number; the
e-mail constraint is silently dropped. And because map iteration order is not guaranteed,
a request supplying both `id` and `idNumber` resolves to whichever the iteration happens to
reach first — genuinely non-deterministic.

This is intentional in spirit (an identity lookup needs no further narrowing) but unsound
in form. It is BR-PERSON-010. A rewrite should preserve the *outcome* for single-identity
queries while deciding explicitly what to do when identity is combined with other filters
— the current behaviour is to ignore them.

**⚠ Wildcard injection in pattern matches.** User-supplied values are interpolated into
the pattern without escaping. A value containing a pattern metacharacter is interpreted as
a wildcard rather than matched literally — so `idNumberLike=%` matches every person. This
is a query-semantics flaw, not an injection vulnerability (values are still bound as
parameters, not concatenated into SQL), but it lets a caller broaden a search beyond what
the parameter implies. A rewrite should escape metacharacters in the user value before
wrapping it in wildcards.

**Error paths.**

| Condition | Outcome |
|---|---|
| `id` is not parseable as a number | The number-format failure is **caught and logged at info level** with the parameter name and value, and the loop **continues to the next parameter**. The request is not rejected. |
| any other parameter is unrecognised | silently ignored |
| a value is empty | treated as a legitimate value — an empty-string equality match, or a pattern of two adjacent wildcards that matches everything |

The swallow-and-continue behaviour means `?id=abc` returns the unfiltered first page with
HTTP 200 rather than a 400. Callers cannot distinguish "filter applied" from "filter
ignored". This is BR-PERSON-011.

**Note on the catch placement.** The exception handler wraps the whole switch inside the
loop, so a parse failure on `id` aborts only that one parameter's handling. Conditions
accumulated from earlier parameters survive.

**Side effects.** None, other than the info-level log line on a parse failure. The method
builds a condition; it does not execute a query.

---

## 7. `DataPeopleController` and `PeopleSortSpec`

### 7.1 `PeopleSortSpec` — sortable-field allow-list

A non-instantiable holder (private constructor) exposing one constant: a map from a public
sort key to an ordering over a person column.

| Sort key | Orders by | Direction as declared |
|---|---|---|
| `idNumber` | `person_id_number` | ascending |
| `firstName` | `person_first_name` | ascending |
| `email` | `person_email` | ascending |
| `phone1` | `person_phone1` | ascending |
| `phone2` | `person_phone2` | ascending |
| `name` | `person_last_name` | ascending |
| `lastName` | `person_last_name` | ascending |

Seven keys, six distinct orderings: **`name` and `lastName` are aliases** — both sort by
last name. There is deliberately no `id` key, consistent with the identifier never being
exposed. The declared ascending direction is a default that the sort parser may invert
(§7.2).

The map is an immutable allow-list: a sort key outside it cannot reach the database.

### 7.2 Sort and pagination resolution (shared-kernel behaviour)

Specified here because it determines this endpoint's observable behaviour.

**Page index.** Absent `pageIndex` parameter → `0`. Present → parsed as an integer; a
non-numeric value raises a number-format failure that is **not** caught here (unlike the
`id` filter in §6.2) and surfaces as an error response.

**Page size.** Absent `pageSize` → the application's configured default items-per-page.
Present → parsed as an integer and **capped**: if the requested size is less than the
configured maximum it is honoured, otherwise the maximum is used. A caller cannot force an
unbounded page.

**Sort.** Absent `sortBy` parameter → unsorted. Present → look the value up in the allow-
list (§7.1), then read the `order` parameter: `asc` → ascending, `desc` → descending,
anything else → the ordering's declared default direction.

**⚠ Known defect.** If `sortBy` names a key **not** in the allow-list, the lookup yields
nothing and the code proceeds to build a sort from it without a presence check, producing a
null dereference → **HTTP 500**. A separate defect: if `sortBy` is supplied but `order` is
not, switching on the absent direction value also dereferences null → 500. So
`?sortBy=unknown` and `?sortBy=email` (without `order`) both fail with 500 rather than
being ignored or defaulted.

This is shared-kernel behaviour affecting every list endpoint in the system, not just this
one; it is recorded in `cross-cutting-documentation/error-handling-patterns.md`. A rewrite
should treat an unknown sort key as ignorable-or-400 and a missing direction as the
declared default. Captured for this domain as BR-PERSON-018.

### 7.3 `DataPeopleController`

**Class-level configuration.**

| Aspect | Value |
|---|---|
| Base path | `/data/people` |
| Documentation tag | `People management` |
| Class-level access rule | authentication required |
| Extends | the shared-kernel generic read-only data controller, bound to this domain's model and entity types |

**Constructor.** Takes the pagination service, the sort-specification parser, this domain's
CRUD service and this domain's predicate service, and passes all four to the generic base.
No other side effects.

**`readMany(Map<String,String> allRequestParams)`**

- **Route:** `GET /data/people`
- **Access:** requires the authority **`people:read`** — the literal string checked at
  runtime. The class-level authentication requirement also applies; the method-level
  authority rule is the operative constraint.
- **Parameters:** the complete raw query string as name/value pairs. Every parameter is
  forwarded untouched — the controller does not whitelist or pre-parse.
- **Behaviour:** delegates to the generic base, which (a) resolves the page index, (b)
  resolves and caps the page size, (c) if any parameters were supplied, parses a sort
  specification via §7.1–7.2, (d) if any parameters were supplied, parses filters via
  §6.2, then (e) calls the CRUD service's `readMany` (§3.3) with all four values.
- **Returns:** a page of transport models with page index, page size and total matching
  count.
- **Error paths:** 401 unauthenticated; 403 lacking `people:read`; 500 from the sort
  defects in §7.2. No 404 — an out-of-range page is an empty list, not an error.

**`getOrderSpecMap()`** — protected override returning the sortable-field allow-list from
§7.1. Called by the generic base during sort resolution. No side effects, no error paths.

### 7.4 The domain exposes exactly one route — read-only

This is the central fact of the domain's external contract. The controller extends the
**read-only** generic base, which contributes only a list operation. There is no create
route, no update route, no patch route, no delete route, and no single-person fetch route.

Consequently the CRUD service's `create`, `update`, `partialUpdate`, `delete` and `readOne`
methods — fully specified above because a rewrite needs them — are **unreachable over HTTP
in this domain**. And with the exception of `getExisting`, they are not reached in-process
either: the complete live call surface of this domain's services is

| Member | Live caller |
|---|---|
| `PeopleCrudService.getExisting` | `ProfileServiceImpl:87` (`user`) |
| `PeopleCrudService.readMany` | this domain's own controller |
| `PeopleConverterService.convertToPojo` | `ProfileServiceImpl:76`, `CustomersConverterServiceImpl:46`, `SalespeopleConverterServiceImpl:46`, `UsersConverterServiceImpl:74`, and this domain's read path |
| `PeopleConverterService.convertToNewEntity` | `ProfileServiceImpl:98`, `RegistrationServiceImpl:86`, `CustomersConverterServiceImpl:51`, `SalespeopleConverterServiceImpl:51` |
| `PeoplePatchService.patchExistingEntity(Map, Person)` | `CustomersPatchServiceImpl:56`, `SalespeoplePatchServiceImpl:54` |
| `PeoplePatchService.patchExistingEntity(PersonPojo, Person)` | `ProfileServiceImpl:104` — **always throws** (BR-PERSON-027) |
| `PeopleRepository.saveAndFlush` | `ProfileServiceImpl:99,105`, `RegistrationServiceImpl:92` |
| `PeopleRepository.exists` | `RegistrationServiceImpl:89` |
| `PeopleRepository.findByIdNumber` | `UsersConverterServiceImpl:101`, and this domain's `getExisting` |

Everything else — `create`, all `update` overloads, `partialUpdate`, `delete`, `readOne` —
has **no caller at all**.

**Implication for a service split.** The real integration surface is the **converter plus
the repository**, not the CRUD service. Three other domains reach past the service layer
directly into this domain's persistence, so a service boundary breaks them at the
repository level. And the current HTTP surface cannot substitute: there is no endpoint to
create or modify a person. A rewrite must therefore *add* a write API as part of extracting
this domain, and reconcile the three consumers' separate duplicate guards into it — it is
not a lift-and-shift. See `specs/05-api-contract.md` §4–§5 and BR-PERSON-026.
