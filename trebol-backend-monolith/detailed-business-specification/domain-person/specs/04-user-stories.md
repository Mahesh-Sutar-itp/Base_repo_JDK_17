# Person Registry — User Stories

Domain code: `PERSON`

Each story is a coherent user action or system behaviour, with acceptance criteria for the
happy path, alternate paths and error paths, cross-referenced to the business rules in
`specs/03-business-rules.md`.

Roles used:

| Role | Meaning |
|---|---|
| Staff user | An authenticated principal holding the `people:read` authority. |
| Consumer domain | Another bounded context (`user`, `customer`, `salesperson`) calling the service API in-process. |
| Visitor | An unauthenticated caller. |

---

## US-PERSON-001: List the person registry

**As a** staff user, **I want to** retrieve people one page at a time, **so that** I can
review the registry without loading every record.

**Acceptance criteria**

- GIVEN 25 people exist AND I hold `people:read`
  WHEN I request `GET /data/people?pageSize=10`
  THEN I receive 10 person objects, `pageIndex` 0, `pageSize` 10, and `totalCount` 25.
- GIVEN 25 people exist
  WHEN I request `?pageIndex=2&pageSize=10`
  THEN I receive the remaining 5 objects and `totalCount` is still 25.
- GIVEN 25 people exist
  WHEN I request `?pageIndex=99`
  THEN I receive an empty `items` array with HTTP 200 and `totalCount` 25 — **not** a 404.
- GIVEN no `pageSize` is supplied
  THEN the application-configured default page size applies.
- GIVEN I request a `pageSize` above the configured maximum
  THEN the maximum is used instead and the response `pageSize` reflects the cap.

**Business rules:** BR-PERSON-012, BR-PERSON-020
**Data changes:** none
**Events published:** none
**Error cases:** 401 unauthenticated; 403 without `people:read`; 500 if `pageIndex` or
`pageSize` is non-numeric
**API:** `GET /data/people`

---

## US-PERSON-002: Find a person by national identification number

**As a** staff user, **I want to** look a person up by their identification number,
**so that** I can confirm whether they are already registered.

**Acceptance criteria**

- GIVEN a person with identification number `11222333-4` exists
  WHEN I request `?idNumber=11222333-4`
  THEN exactly that person is returned and `totalCount` is 1.
- GIVEN no person carries that number
  THEN `items` is empty, `totalCount` is 0, and the status is 200.
- GIVEN the stored number is `11222333-A`
  WHEN I request `?idNumber=11222333-a`
  THEN nothing is returned — the match is case-sensitive.
- GIVEN I request `?idNumber=11222333-4&email=wrong@example.com`
  THEN the person is **still returned** — the e-mail filter is silently discarded.

**Business rules:** BR-PERSON-010, BR-PERSON-016, BR-PERSON-001
**Data changes:** none
**Error cases:** as US-PERSON-001
**API:** `GET /data/people`

---

## US-PERSON-003: Search people by name or e-mail

**As a** staff user, **I want to** search by full or partial name and e-mail, **so that** I
can find a person when I do not know their identification number.

**Acceptance criteria**

- GIVEN a person named Ana Soto
  WHEN I request `?name=Ana` THEN she is returned (first name matches).
  WHEN I request `?name=Soto` THEN she is returned (last name matches).
- WHEN I request `?firstName=ana` THEN nothing is returned — exact filters are
  case-sensitive.
- WHEN I request `?firstNameLike=ana` THEN she is returned — partial filters are
  case-insensitive.
- GIVEN I request `?firstNameLike=an&lastNameLike=so`
  THEN only people matching **both** substrings are returned — non-identity filters
  combine with AND.
- WHEN I request `?nameLike=o` THEN every person whose first or last name contains `o` is
  returned.
- WHEN I request `?emailLike=%` THEN **every** person is returned, because the
  metacharacter is not escaped.

**Business rules:** BR-PERSON-016, BR-PERSON-017
**Data changes:** none
**Error cases:** as US-PERSON-001
**API:** `GET /data/people`

---

## US-PERSON-004: Sort the registry

**As a** staff user, **I want to** order results by a chosen field, **so that** I can scan
the registry predictably.

**Acceptance criteria**

- GIVEN I request `?sortBy=lastName&order=asc` THEN results are ordered by last name
  ascending.
- GIVEN I request `?sortBy=email&order=desc` THEN results are ordered by e-mail descending.
- GIVEN I request `?sortBy=name` AND `?sortBy=lastName` separately
  THEN both produce the **same** ordering — they are aliases for last name.
- GIVEN I supply no `sortBy` THEN results are unsorted and order is not guaranteed.
- GIVEN I request `?sortBy=idNumber&order=sideways`
  THEN the ascending default applies — an unrecognised direction is not an error.
- GIVEN I request `?sortBy=id`
  THEN the request **fails with HTTP 500**, because there is no sort key for the surrogate
  identifier and an unknown key is not handled.
- GIVEN I request `?sortBy=email` with no `order`
  THEN the request **fails with HTTP 500**.

**Business rules:** BR-PERSON-018, BR-PERSON-020
**Data changes:** none
**Error cases:** 500 on unknown sort key or missing direction — a known defect, documented
rather than designed
**API:** `GET /data/people`

---

## US-PERSON-005: Register a new person

**As a** consumer domain, **I want to** create a person record, **so that** a user account,
customer or salesperson can be associated with a real identity.

**⚠ Scope note.** In the current system this story is **not** served by this domain's
`create` operation, which has no caller at all. Consumers compose the converter with the
repository directly, each applying its own duplicate guard (BR-PERSON-026). The criteria
below are split accordingly.

**Acceptance criteria — the live path (self-registration)**

- GIVEN no person carries identification number `X`
  WHEN an unauthenticated caller posts a registration containing `X`
  THEN a person row is inserted, then a user row, then a customer row — all in one
  transaction.
- GIVEN a person already carries `X`
  THEN registration fails with `"That ID number is already registered and associated to an
  account."` (409) and no row is written.
- GIVEN the submitted username is taken
  THEN registration fails with `"That username is taken."` (409) **before** any person
  lookup occurs — the username check runs first.
- GIVEN the registration body omits a mandatory person field
  THEN the route-layer validation rejects it with 400, because the registration body is
  validated.
- GIVEN the registration omitted both phone numbers
  THEN both stored phone values are `""` — the converter's builder defaults apply.

**Acceptance criteria — the dead path (`create`)**

- GIVEN `create` were invoked with a blank identification number
  THEN it would fail with `"Customer does not have ID card"` (400).
- GIVEN `create` were invoked for an existing identification number
  THEN it would fail with `"The item already exists"` (409).
- GIVEN `create` succeeded
  THEN the returned model would **not** contain the new identifier.
- **These criteria are specified for a rewrite designing a write API. Nothing exercises them
  today.**

**Business rules:** BR-PERSON-001, BR-PERSON-002, BR-PERSON-003, BR-PERSON-004,
BR-PERSON-006, BR-PERSON-019, BR-PERSON-020, BR-PERSON-026
**Data changes:** one row inserted into `people` (plus a user and a customer row, owned by
other domains)
**Events published:** none
**Error cases:** as above; plus a constraint violation (500) on a null phone or an
over-length field
**API:** `POST /public/register` — **an unauthenticated route in the `user` domain**. This
domain exposes no creation route (BR-PERSON-013).

---

## US-PERSON-006: Correct a person's details

**As a** consumer domain, **I want to** change selected fields on an existing person,
**so that** corrections do not require resubmitting the whole record.

**⚠ Two callers, two outcomes.** The map-based patch service works and is used correctly by
the `customer` and `salesperson` domains. The `user` domain's profile path calls the
*deprecated* overload and therefore always fails (BR-PERSON-027).

**Acceptance criteria — the working path (nested customer/salesperson patch)**

- GIVEN a person exists
  WHEN a consumer patches `{"person.firstName": "Ana María"}`
  THEN only the first name changes and all other fields are preserved.
- GIVEN a consumer patches `{"person.email": ""}`
  THEN the stored e-mail is **unchanged**, no error is raised, and the call reports
  success.
- GIVEN a consumer patches `{"person.phone1": ""}`
  THEN the stored phone **is** cleared to the empty string.
- GIVEN the patch results in no actual change
  THEN **no database write occurs** and the unchanged record is returned.
- GIVEN a consumer patches `{"person.nickname": "bob"}`
  THEN the unknown key is silently ignored, nothing changes, and no write occurs.
- GIVEN a consumer patches with **unprefixed** keys such as `{"firstName": "Ana"}`
  THEN the extracted person change map is empty and **the person is not touched at all**.
- GIVEN a consumer patches `{"person.firstName": 42}`
  THEN the call **fails with HTTP 500**, not a 400 — a non-string value is an unhandled
  cast failure.

**Acceptance criteria — the broken path (user profile update)**

- GIVEN an authenticated account that **already has** an associated person
  WHEN the account holder submits a profile update
  THEN the call **always fails with HTTP 500** and the message `"This method signature has
  been deprecated"`. The profile cannot be edited at all.
- GIVEN an authenticated account with **no** associated person
  WHEN the account holder submits a profile update whose identification number matches an
  existing person already linked to another account
  THEN it fails with `"Person profile is associated to another account. Cannot use it."`
  (400).
- GIVEN the same, but the matched person is linked to no account
  THEN the account is linked to that existing person and no person row is created.
- GIVEN the same, but no person matches the identification number
  THEN a new person row is created and linked to the account.
- GIVEN the submitted identification number is blank
  THEN it fails with `"Customer does not have ID card"` (400) — this is the one live use of
  `getExisting`.

**Business rules:** BR-PERSON-007, BR-PERSON-008, BR-PERSON-009, BR-PERSON-014,
BR-PERSON-015, BR-PERSON-027, BR-PERSON-028
**Data changes:** at most one row updated or inserted in `people`; **zero** writes when
nothing changed
**Events published:** none
**Error cases:** 500 on the profile path for existing profiles; 500 on a non-string value;
400 on a blank identification number or a person owned by another account
**API:** no route in this domain; reached through the `user` and `customer` domains' routes

---

## US-PERSON-007: Patch a person nested inside a consumer payload

**As a** consumer domain, **I want to** patch a person's fields as part of patching my own
aggregate, **so that** editing a customer or salesperson record updates the underlying
identity in the same operation.

**Acceptance criteria**

- GIVEN a consumer domain owns a record associated with a person
  WHEN it applies a change map whose person keys carry the literal prefix `"person."`
  THEN the consumer strips the prefix, delegates the remainder to this domain's map-based
  patch service, and the semantics of US-PERSON-006 apply unchanged.
- GIVEN the change map contains no `person.`-prefixed key
  THEN the extracted person change map is empty AND the consumer **skips the person patch
  entirely** — no person write occurs.
- GIVEN the nested patch changes nothing
  THEN no person write occurs (BR-PERSON-009 applies transitively).
- GIVEN the consumer is `customer` or `salesperson`
  THEN this path works correctly.
- GIVEN the consumer is `user` (profile update)
  THEN this path is **not** used — that caller invokes the deprecated overload instead and
  always fails (BR-PERSON-027).

**Business rules:** BR-PERSON-007, BR-PERSON-008, BR-PERSON-009, BR-PERSON-028
**Data changes:** at most one row updated in `people`
**API:** service API only, invoked from a consumer domain's own route
**Source:** the prefix constant `PERSON_DATA_MAP_KEYS_PREFIX = "person."` in
`src/main/java/org/trebol/config/Constants.java:47`, consumed by
`CustomersPatchServiceImpl:53` and `SalespeoplePatchServiceImpl`
**Note:** the routes that use this convention belong to the consumer domains; see the
`customer` and `salesperson` domain specifications. This story records the obligation this
domain satisfies and the cross-domain feature it participates in
(`CDF-04 Account profile management`).

---

## US-PERSON-008: Be denied access without the read authority

**As a** visitor or under-privileged user, **I want to** be refused access to the registry,
**so that** personal data is not exposed.

**Acceptance criteria**

- GIVEN I present no credentials
  WHEN I request `GET /data/people`
  THEN I receive 401 and no person data.
- GIVEN I am authenticated but do not hold `people:read`
  THEN I receive 403 and no person data.
- GIVEN I hold `people:read`
  THEN I receive 200 — and I can page through **every** person in the system, including
  national identification numbers, e-mail addresses and phone numbers.

**Business rules:** BR-PERSON-012
**Data changes:** none
**Error cases:** 401, 403
**API:** `GET /data/people`
**Note:** the third criterion is the current behaviour, not an endorsement. A single
authority grants full registry enumeration with no field-level redaction; see
`specs/05-api-contract.md` §1.4.

---

## US-PERSON-009: Attempt to delete a person

**As a** staff user, **I want to** remove a person record, **so that** obsolete identities
do not accumulate — **this story is currently unsatisfiable and is recorded as a gap.**

**Acceptance criteria (current behaviour)**

- GIVEN a person exists AND is referenced by no user, customer or salesperson
  WHEN I attempt to delete them
  THEN **there is no route and no caller** — deletion is unreachable in the running system.
- GIVEN a consumer record referencing a person is deleted
  THEN the person row **survives** — no consumer cascades removal.
- THEREFORE orphaned person rows accumulate permanently and no mechanism exists to
  reclaim them.

**Business rules:** BR-PERSON-013, BR-PERSON-022
**Data changes:** none — deletion never occurs
**API:** none
**Rewrite note:** the underlying delete-by-filter capability exists in the service layer but
is bounded only by "at least one match", so exposing it as-is would permit mass deletion.
Any extraction that adds a delete path must bound it and decide what happens to inbound
references first.

---

## Coverage summary

| Category | Stories |
|---|---|
| Search and read | US-PERSON-001, 002, 003, 004 |
| Creation | US-PERSON-005 |
| Modification | US-PERSON-006, 007 |
| Access control | US-PERSON-008 |
| Lifecycle gap | US-PERSON-009 |
| **Total** | **9** |

**Categories with no stories, and why:** no state-transition stories (the domain has no
state field — see `specs/00-plan.md`); no batch, scheduled, or integration-triggered
stories (the domain has no background job, no scheduled task and no external integration —
see `reference/background-jobs.md` and `reference/integrations.md`); no error-recovery
stories (no retry, compensation or reconciliation logic exists).

Every feature in the Mode A catalog `detailed-domain-bre/domain-person/features.md`
(`F-001`…`F-007`) maps to at least one story above: F-001→US-001, F-002→US-002/003,
F-003→US-004, F-004→US-002/005, F-005→US-005, F-006→US-006, F-007→US-007.
