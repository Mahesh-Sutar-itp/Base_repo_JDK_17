# Person Registry — API Contract

Domain slug: `person` · Controllers: 1 · HTTP endpoints: 1

This domain is a **hybrid**: it exposes one HTTP endpoint *and* a programmatic service API
that three other domains depend on. Both are specified here — §1–§3 cover the HTTP surface,
§4 covers the service API. §5 covers what a service extraction must add.

All endpoints inherit the application-wide authentication flow described in
`cross-cutting-documentation/authentication-authorization.md` and the error translation
described in `cross-cutting-documentation/error-handling-patterns.md`.

---

## 1. `GET /data/people` — list people

| Aspect | Value |
|---|---|
| Method | `GET` |
| Path | `/data/people` |
| Handler | `DataPeopleController.readMany` |
| Authentication | required (class-level rule) |
| Authority | **`people:read`** — literal string checked at runtime |
| Documentation tag | `People management` |
| Request body | none |
| Content-Type (response) | JSON |

### 1.1 Query parameters

All parameters are optional. Unrecognised parameters are silently ignored. Every parameter
is read from the raw query string — the controller forwards the whole map untouched.

**Filter parameters**

| Parameter | Type | Match semantics | Notes |
|---|---|---|---|
| `id` | integer | exact, on the surrogate key | **Short-circuits** — discards all other filters. Non-numeric values are ignored, not rejected (BR-PERSON-011). |
| `idNumber` | string | exact, **case-sensitive** | **Short-circuits** — discards all other filters. |
| `name` | string | exact, case-sensitive, on first name **OR** last name | |
| `firstName` | string | exact, case-sensitive | |
| `lastName` | string | exact, case-sensitive | |
| `email` | string | exact, case-sensitive | |
| `nameLike` | string | substring, **case-insensitive**, on first name **OR** last name | Metacharacters unescaped (BR-PERSON-017). |
| `firstNameLike` | string | substring, case-insensitive | Metacharacters unescaped. |
| `lastNameLike` | string | substring, case-insensitive | Metacharacters unescaped. |
| `idNumberLike` | string | substring, case-insensitive | Metacharacters unescaped. |
| `emailLike` | string | substring, case-insensitive | Metacharacters unescaped. |

Multiple filter parameters are combined with **AND**, except that `id` and `idNumber`
short-circuit and discard everything else (BR-PERSON-010). Supplying both `id` and
`idNumber` has non-deterministic precedence.

**Pagination parameters**

| Parameter | Type | Default | Behaviour |
|---|---|---|---|
| `pageIndex` | integer | `0` | Zero-based. A non-numeric value raises an error response (unlike `id`, it is **not** swallowed). An index past the end returns an empty list with a non-zero total. |
| `pageSize` | integer | application-configured items-per-page | Capped at the application-configured maximum. A requested size at or above the maximum yields the maximum. |

**Sort parameters**

| Parameter | Type | Allowed values | Behaviour |
|---|---|---|---|
| `sortBy` | string | `idNumber`, `firstName`, `email`, `phone1`, `phone2`, `name`, `lastName` | `name` and `lastName` are aliases — both sort by last name. There is no sort key for the surrogate identifier. **An unknown value causes HTTP 500** (BR-PERSON-018). |
| `order` | string | `asc`, `desc` | Any other value falls back to the ordering's declared ascending default. **Omitting it while supplying `sortBy` causes HTTP 500** (BR-PERSON-018). |

Absent `sortBy` → unsorted.

### 1.2 Response — 200 OK

A page container:

| Field | Type | Description |
|---|---|---|
| `items` | array of person objects | The page contents, in the requested order. |
| `pageIndex` | integer | The zero-based index echoed back. |
| `pageSize` | integer | The effective page size after capping. |
| `totalCount` | integer | Total rows **matching the filter**, not the number returned. Computed by a separate count query. |

Person object:

| Field | Type | Always present | Description |
|---|---|---|---|
| `firstName` | string | yes | |
| `lastName` | string | yes | |
| `idNumber` | string | yes | National identification number. |
| `email` | string | yes | Not format-validated (BR-PERSON-005). |
| `phone1` | string | **no** | Omitted when stored value is null; present as `""` when stored empty. |
| `phone2` | string | **no** | Same. |

**There is no identifier field.** The surrogate key is never emitted (BR-PERSON-020).
Null-valued fields are omitted from the response rather than serialized as null, so
consumers MUST treat an absent phone and an empty-string phone as equivalent.

Example:

```json
{
  "items": [
    {
      "firstName": "Ana",
      "lastName": "Soto",
      "idNumber": "11222333-4",
      "email": "ana.soto@example.com",
      "phone1": "+56 9 1234 5678",
      "phone2": ""
    }
  ],
  "pageIndex": 0,
  "pageSize": 10,
  "totalCount": 1
}
```

### 1.3 Error responses

| Status | Condition | Body |
|---|---|---|
| 401 | No authentication presented. | application-wide authentication error shape |
| 403 | Authenticated but lacking authority `people:read`. | application-wide authorization error shape |
| 500 | `sortBy` names a key outside the allow-list, or `sortBy` supplied without `order` (BR-PERSON-018). | generic error shape |
| 500 | `pageIndex` or `pageSize` is non-numeric. | generic error shape |

**No 404.** An empty result is a 200 with an empty `items` array.

### 1.4 Behavioural notes

- **Two queries per request.** A count query and a fetch query, executed separately and
  not atomically with respect to concurrent writes.
- **No caching.** No cache is read or written on this path; see
  `cross-cutting-documentation/caching-patterns.md`.
- **No rate limiting** specific to this endpoint.
- **Full-registry exposure.** With no filter, the endpoint pages through every person in
  the system. Any holder of `people:read` can enumerate the entire registry — names,
  national identification numbers, e-mail addresses and phone numbers. This is the
  domain's most significant data-exposure surface and a rewrite handling personal data
  under a privacy regime should review it deliberately.

---

## 2. Endpoints that do not exist

There is **no** create, update, patch, delete, or single-person fetch endpoint. The
controller extends the read-only generic data controller, which contributes only the list
operation (BR-PERSON-013).

Requests to `POST /data/people`, `PUT /data/people`, `PATCH /data/people` or
`DELETE /data/people` are unmatched routes and produce the application's standard
method-not-allowed or not-found response — not a domain error.

---

## 3. Authentication and authorization summary

| Aspect | Value |
|---|---|
| Authentication | Required for the whole controller. |
| Authority enforced | `people:read` on the list operation. |
| Enforcement point | Declarative method-level rule on `DataPeopleController.readMany`. |
| Authorities for write | **None exist** — there is no write route to protect. |
| Cross-site request forgery | Not applicable; the only route is a read. |

See `cross-cutting-documentation/common-permissions.md` for how `people:read` is granted.

---

## 4. Service API — what consumer domains actually call

Because the HTTP surface is read-only but three domains write person records, the
**programmatic** surface is the domain's real write contract. But it is **not** the CRUD
service: a full call-site census (BR-PERSON-026) shows consumers reach past it.

### 4.1 The live surface

| Member | Live callers | Specified in |
|---|---|---|
| `PeopleCrudService.getExisting(model) → optional entity` | `ProfileServiceImpl:87` (`user`) | `02` §3.1 |
| `PeopleCrudService.readMany(…) → page` | this domain's controller | `02` §3.3 |
| `PeopleConverterService.convertToPojo(entity) → model` | `user`, `customer`, `salesperson` converters and this domain's read path | `02` §4.3 |
| `PeopleConverterService.convertToNewEntity(model) → entity` | `user` registration and profile, `customer`, `salesperson` converters | `02` §4.2 |
| `PeoplePatchService.patchExistingEntity(changes, entity) → entity` | `customer`, `salesperson` patch services, via the `person.` prefix | `02` §5.1 |
| `PeopleRepository.saveAndFlush(entity)` | `user` registration and profile | `02` §2.3 |
| `PeopleRepository.exists(predicate)` | `user` registration | `02` §2.3 |
| `PeopleRepository.findByIdNumber(string)` | `user` converter, and `getExisting` | `02` §2.2 |

### 4.2 The dead surface

These exist, are fully specified in document `02`, and have **no caller anywhere**:
`create`, `update(model)`, `update(model, id)`, `update(model, filters)`,
`partialUpdate(changes, id)`, `partialUpdate(changes, filters)`, `readOne(filters)`,
`delete(filters)`.

A rewrite should treat them as **design input for a new write API**, not as behaviour to
preserve bit-for-bit — nothing currently depends on them.

### 4.3 The one always-failing call

`PeoplePatchService.patchExistingEntity(model, entity)` — the transport-model overload — is
called by `ProfileServiceImpl:104` and **throws unconditionally**. Profile updates for an
account that already has a profile therefore always fail with HTTP 500 (BR-PERSON-027).

### 4.4 Error contract of the service API

| Operation | Error | Condition | Mapped status |
|---|---|---|---|
| `getExisting` (live) | bad-input, `"Customer does not have ID card"` | identification number blank | 400 |
| `create` (dead) | entity-already-exists, `"The item already exists"` | identification number already present | 409 |
| `readOne`, `delete` (dead) | entity-not-found, `"Requested item(s) not found"` | no match | 404 |
| `partialUpdate(…, filters)`, `update(…, filters)` (dead) | runtime, `"Cannot update more than one item at a time"` | more than one match | 500 |
| `patchExistingEntity(model, entity)` (live) | unsupported-operation, `"This method signature has been deprecated"` | always | 500 |
| `update(model)` (dead) | unsupported-operation, same literal | always | 500 |
| `convertToNewEntity`, `saveAndFlush` (live) | constraint violation | null phone, over-length field, or duplicate identification number | 500 |

**Duplicate-guard messages a client actually sees** come from the *consumers*, not from this
domain:

| Flow | Literal message | Source |
|---|---|---|
| Registration, identification number taken | `"That ID number is already registered and associated to an account."` | `RegistrationServiceImpl:90` |
| Profile update, person belongs to another account | `"Person profile is associated to another account. Cannot use it."` | `ProfileServiceImpl:92` |
| Registration, username taken | `"That username is taken."` | `RegistrationServiceImpl:82` |

`PersonNotFoundException` is declared in this domain and thrown at exactly one site —
`ProfileServiceImpl:74` in the `user` domain, message `"The account does not have an
associated profile"` (BR-PERSON-023).

### 4.5 Patch key vocabulary

The patch change map is keyed by **transport-model field names**, not column names:
`idNumber`, `firstName`, `lastName`, `email`, `phone1`, `phone2`. Unknown keys are silently
ignored (BR-PERSON-014). The four name/identity/e-mail keys reject blank values silently;
the two phone keys accept them and clear the stored value (BR-PERSON-007, BR-PERSON-008).

When a consumer patches a person nested in its own aggregate, keys arrive prefixed with the
literal `"person."` and the consumer strips the prefix before delegating (BR-PERSON-028).

---

## 5. What a service extraction must add

The current contract is not sufficient to run this domain as an independent service. The
gap is structural, not cosmetic:

1. **Consumers reach past the service layer.** Three domains call this domain's
   **repository** directly — `saveAndFlush`, `exists`, `findByIdNumber` — so a service
   boundary breaks them at the persistence level, not at a clean service interface
   (BR-PERSON-026). Each such call site must be rewritten against a new API.
2. **Write endpoints do not exist.** There is no create or patch route, and the CRUD
   service's `create` has no caller to model one on. An extraction must design
   `POST /data/people` and a patch route from scratch, with authorities that do not exist
   today (for example `people:create`, `people:update`).
3. **Three separate duplicate guards must be reconciled.** Registration checks
   `peopleRepository.exists`; profile update checks `getExisting` and then
   `usersRepository.findByPersonIdNumber`; the CRUD service's own check is unused. Each
   emits a different message. A single service API must pick one behaviour and one message,
   which changes what at least two consumers' clients observe.
4. **The transport model cannot address a record.** It has no identifier field, so a patch
   route must take the key from the path or accept the national identification number as
   the addressing key. The latter is more faithful to the domain's identity model.
5. **Cascade-based writes must become explicit calls.** `customer` and `salesperson` create
   person rows through association cascades after calling only the converter. Across a
   service boundary there is no cascade — those flows need explicit calls, which also makes
   them non-transactional with respect to the consumer's own write.
6. **The `person.` key prefix is a cross-domain coupling.** Nested patches encode person
   fields as prefixed flat keys (BR-PERSON-028). A service contract should use a nested
   object instead.
7. **Fix the broken profile path first.** Profile updates for accounts that already have a
   profile currently always fail (BR-PERSON-027). Extracting the domain without fixing this
   carries the defect across the boundary, where it will be harder to diagnose.
8. **Patch semantics must be preserved deliberately.** The blank-guard asymmetry
   (BR-PERSON-007/008) and the no-write-when-unchanged behaviour (BR-PERSON-009) are
   observable. A naive REST patch implementation will not reproduce them.

See `domain-decomposition-overview/anti-corruption-layers.md` for the boundary options.
