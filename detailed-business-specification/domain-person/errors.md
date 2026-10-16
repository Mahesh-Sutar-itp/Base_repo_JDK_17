# Person Registry — Error Codes

Application-wide error shapes, the exception-to-status mapping mechanism, and errors shared
across domains are specified in
`cross-cutting-documentation/error-handling-patterns.md` and
`cross-cutting-documentation/common-error-codes.md`. This document covers only what is
specific to this domain — including two findings that matter for a rewrite: the domain's own
exception type is raised only from *another* domain (§1), and the error messages clients
actually see on person writes come from the consumers, not from here (§2.2).

---

## 1. Domain-specific exception types

| Type | Source | Extends | Mapped status | Thrown where |
|---|---|---|---|---|
| `PersonNotFoundException` | `src/main/java/org/trebol/jpa/exceptions/PersonNotFoundException.java` | framework entity-not-found exception | 404 (inherited) | exactly one site, **in the `user` domain** — `ProfileServiceImpl:74` |

### 1.1 Declared here, thrown there

`PersonNotFoundException` has exactly one throw site in the codebase, and it is **not in
this domain**:

| Throw site | Message (literal) | Condition |
|---|---|---|
| `src/main/java/org/trebol/api/services/impl/ProfileServiceImpl.java:74` | `"The account does not have an associated profile"` | An account is found but its person association is null. |

So the type is a **cross-domain contract**. Two consequences:

- A 404 arising **inside** this domain carries the generic shared message
  `"Requested item(s) not found"`, not a person-specific one — the shared-kernel CRUD base
  throws the framework's generic exception, not this type.
- A 404 carrying `"The account does not have an associated profile"` originates from the
  `user` domain, even though the exception type is declared here.

A rewrite extracting `person` as a service must decide where this type lives, since the
only code that raises it belongs to a different bounded context and the condition it
reports — an account with no linked person — is a `user` invariant violation.

Recorded as BR-PERSON-023.

---

## 2. Errors this domain actually produces

Every error below originates either from this domain's one override
(`PeopleCrudServiceImpl.getExisting`) or from the shared-kernel base acting on this
domain's types. Message strings are literals and are part of the observable contract.

| # | Message (literal) | Exception type | Status | Trigger | Related rule |
|---|---|---|---|---|---|
| E1 | `Customer does not have ID card` | bad-input | **400** | The submitted identification number is null, empty, or whitespace-only. Raised by `getExisting`, reached via `create`. | BR-PERSON-002 |
| E2 | `The item already exists` | entity-already-exists | **409** | A creation request carries an identification number that already exists. Raised by the generic pre-creation validation. Shared literal, not person-specific. | BR-PERSON-003 |
| E3 | `Requested item(s) not found` | framework entity-not-found | **404** | `readOne` or `delete` matches no row. Shared literal, not person-specific. | BR-PERSON-023 |
| E4 | `Cannot update more than one item at a time` | plain runtime | **500** | A patch-by-filter or update-by-filter matches more than one person. Unchecked, so unmapped. | BR-PERSON-025 |
| E5 | `This method signature has been deprecated` | unsupported-operation | **500** | The single-argument full update (dead), or the model-based patch overload — **the latter has a live caller at `ProfileServiceImpl:104`, making profile updates for existing profiles always fail** (BR-PERSON-027). | BR-PERSON-024, BR-PERSON-027 |
| E6 | `This method is deprecated` | unsupported-operation | **500** | The converter's apply-changes-to-existing method is invoked. Always. | BR-PERSON-024 |
| E7 | *(no message — persistence layer)* | class-cast failure, wrapped in a runtime exception | **500** | A patch change map contains a non-string value. | BR-PERSON-015 |
| E8 | *(no message — persistence layer)* | constraint violation | **500** | A null phone reaches the database, or a name/e-mail exceeds its column length, or a concurrent create loses the unique-constraint race. | BR-PERSON-019 |
| E9 | *(no message — null dereference)* | unchecked null failure | **500** | `sortBy` names a key outside the allow-list, or `sortBy` is supplied without `order`. Shared-kernel defect. | BR-PERSON-018 |

### 2.1 Error message quirks worth preserving deliberately

**E1 names the wrong noun.** The message says *"Customer* does not have ID card" inside the
*person* domain, and there is a separate `customer` domain in this system. A client
receiving it may reasonably look for a customer-related fault. The string is nonetheless
part of today's API contract — changing it is a breaking change to error output, not a typo
fix.

**E2 and E3 are shared literals.** Neither mentions a person. A client cannot tell from the
message which domain rejected its request. If the extracted service wants
person-specific messages, that is a new contract.

**E4, E5, E6 and E9 are 500s describing non-server faults.** E4 describes an insufficiently
selective filter (a client error); E5 and E6 describe invoking a method that always fails;
E9 describes an invalid sort parameter (a client error). All surface as 500 because the
exceptions are unchecked and unmapped. A rewrite should map E4 and E9 to 4xx and omit the
E5/E6 methods entirely.

---

## 2.2 Errors raised by consumers, not by this domain

A client writing a person never sees E1 or E2 in practice, because the write paths bypass
this domain's CRUD service (BR-PERSON-026). The messages actually observed come from the
consumer domains:

| Flow | Literal message | Source | Status |
|---|---|---|---|
| Registration, identification number already registered | `That ID number is already registered and associated to an account.` | `RegistrationServiceImpl:90` | 409 |
| Registration, username taken | `That username is taken.` | `RegistrationServiceImpl:82` | 409 |
| Profile update, person belongs to another account | `Person profile is associated to another account. Cannot use it.` | `ProfileServiceImpl:92` | 400 |
| Profile read, account has no profile | `The account does not have an associated profile` | `ProfileServiceImpl:74` | 404 |

These are listed here because they are the **observable error contract around person data**,
even though the code raising them belongs to the `user` domain. A service extraction has to
decide which of these become the person service's responsibility and which stay with the
caller — see `specs/05-api-contract.md` §5.

---

## 3. Conditions that are deliberately *not* errors

Equally part of the contract — a rewrite that turns any of these into an error changes
observable behaviour:

| Condition | Current outcome | Rule |
|---|---|---|
| A non-numeric `id` filter | Logged at info level, filter skipped, **HTTP 200** with an unfiltered page. | BR-PERSON-011 |
| An unrecognised query parameter | Silently ignored. | — |
| An unrecognised patch key | Silently ignored; by BR-PERSON-009 this also means no write. | BR-PERSON-014 |
| A blank patch value for `idNumber`, `firstName`, `lastName` or `email` | Silently ignored; stored value retained; **success reported**. | BR-PERSON-007 |
| A patch that changes nothing | Success, with no database write. | BR-PERSON-009 |
| A patch or lookup addressing a non-existent person via `partialUpdate(changes, id)` | Empty result, **no exception**. | — |
| A page index past the end of the registry | Empty `items` with a non-zero `totalCount`, HTTP 200. | — |
| An e-mail that is not a valid e-mail address | Accepted and stored. | BR-PERSON-005 |
| A phone number in any format | Accepted and stored. | BR-PERSON-006 |

---

## 4. Exception hierarchy

```
Serializable / RuntimeException (platform)
 └── framework EntityNotFoundException ............ → 404
 │    └── PersonNotFoundException .................. declared here, NEVER THROWN
 ├── framework EntityExistsException ............... → 409  (E2)
 ├── BadInputException (application base) .......... → 400  (E1)
 ├── UnsupportedOperationException ................. → 500  (E5, E6)
 ├── ClassCastException ............................ → 500  (E7)
 └── plain RuntimeException ........................ → 500  (E4, and the wrapper around E7)
```

`BadInputException` is the application's own base type for client-input faults and is the
only one of these with a clean 4xx mapping that this domain raises itself. All other 4xx
responses from this domain come from framework exception types raised by the shared-kernel
base.

---

## 5. No domain-specific error codes

This domain defines **no** numeric or symbolic error-code vocabulary. Errors are identified
solely by exception type and literal message. There is no error-code enum, no catalog
entry, and no machine-readable discriminator a client could branch on — a client must match
on message strings or status codes alone.

A rewrite introducing structured error codes would be an improvement, but note that it
changes the response body shape for every error above.
