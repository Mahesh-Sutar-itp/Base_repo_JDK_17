# Person Registry — Integration Points

## 1. External systems

**None.** This domain integrates with no external system. Specifically, there is:

- no outbound HTTP, gRPC or SOAP client;
- no message broker producer or consumer, no topic, no queue;
- no e-mail, SMS or push-notification sender — despite the domain storing an e-mail address
  and two phone numbers, nothing in this domain ever contacts them;
- no payment, shipping, tax or identity-verification provider;
- no file, object-store or report export;
- no cache, search index or secondary datastore;
- no scheduled or asynchronous job;
- no no-op or mock client standing in for an absent external service.

The domain's only I/O is the relational database holding the `people` table.

**Notably absent: identification-number validation.** The national identification number is
the domain's business key and its only uniqueness invariant, yet it is never validated for
format or checksum, and never verified against any registry or external authority. It is
accepted as an opaque string of up to 20 characters (BR-PERSON-005 covers the analogous
e-mail case). A rewrite may want such a check; there is no existing integration to model it
on.

---

## 2. Cross-domain integration points

These are **in-process** today, not network calls. They are documented here because they
are the boundaries a service extraction must convert into real integrations, and because
the coupling runs deeper than the service interface suggests.

### 2.1 Direction of dependency

This domain `depends_on_domains` **nothing**. It is a pure leaf: it calls no other domain,
holds no foreign key, and declares no association. All coupling is **inbound**.

| Consumer domain | Depends on this domain for |
|---|---|
| `user` | identity records for accounts; profile read, create, link and update |
| `customer` | the person behind a customer record |
| `salesperson` | the person behind a salesperson record |

### 2.2 Inbound call boundaries — the complete census

Each row is a place where code outside this domain reaches into it. "Level" records how
deep the reach goes, which determines how hard the boundary is to cut.

| # | Caller (file:line) | Target | Level | What is passed | What is returned | Side effects |
|---|---|---|---|---|---|---|
| I1 | `ProfileServiceImpl:76` | `PeopleConverterService.convertToPojo` | service | a loaded `Person` entity | a `PersonPojo` | none |
| I2 | `ProfileServiceImpl:87` | `PeopleCrudService.getExisting` | service | a `PersonPojo` (only its `idNumber` is read) | optional `Person` | none; throws bad-input if blank |
| I3 | `ProfileServiceImpl:98` | `PeopleConverterService.convertToNewEntity` | service | a `PersonPojo` | an unsaved `Person` with null key, phones defaulted to `""` | none |
| I4 | `ProfileServiceImpl:99` | `PeopleRepository.saveAndFlush` | **persistence** | an unsaved `Person` | the saved `Person` with generated key | **row inserted into `people`** |
| I5 | `ProfileServiceImpl:104` | `PeoplePatchService.patchExistingEntity(model, entity)` | service | a `PersonPojo` and the loaded `Person` | — | **always throws** (BR-PERSON-027) |
| I6 | `ProfileServiceImpl:105` | `PeopleRepository.saveAndFlush` | **persistence** | a `Person` | the saved `Person` | **row updated in `people`** — unreachable, because I5 throws first |
| I7 | `RegistrationServiceImpl:86` | `PeopleConverterService.convertToNewEntity` | service | a `PersonPojo` from the registration body | an unsaved `Person` | none |
| I8 | `RegistrationServiceImpl:89` | `PeopleRepository.exists` | **persistence** | a predicate on `idNumber` | boolean | none |
| I9 | `RegistrationServiceImpl:92` | `PeopleRepository.saveAndFlush` | **persistence** | an unsaved `Person` | the saved `Person` | **row inserted into `people`** |
| I10 | `UsersConverterServiceImpl:74` | `PeopleConverterService.convertToPojo` | service | a `Person` | a `PersonPojo` nested in a user model | none |
| I11 | `UsersConverterServiceImpl:101` | `PeopleRepository.findByIdNumber` | **persistence** | an identification number string | optional `Person` | none |
| I12 | `CustomersConverterServiceImpl:46` | `PeopleConverterService.convertToPojo` | service | `customer.getPerson()` | a `PersonPojo` — **the customer's entire external representation is the person** | none |
| I13 | `CustomersConverterServiceImpl:51` | `PeopleConverterService.convertToNewEntity` | service | a `PersonPojo` | an unsaved `Person`, wrapped in a new `Customer` | none directly; **the person row is inserted by the customer's association cascade** |
| I14 | `CustomersPatchServiceImpl:56` | `PeoplePatchService.patchExistingEntity(changes, entity)` | service | the unprefixed person change map and the existing `Person` | a patched `Person` clone | none directly; persisted by the consumer |
| I15 | `SalespeopleConverterServiceImpl:46` | `PeopleConverterService.convertToPojo` | service | `salesperson.getPerson()` | a `PersonPojo` | none |
| I16 | `SalespeopleConverterServiceImpl:51` | `PeopleConverterService.convertToNewEntity` | service | a `PersonPojo` | an unsaved `Person`, wrapped in a new `Salesperson` | none directly; cascade-persisted |
| I17 | `SalespeoplePatchServiceImpl:54` | `PeoplePatchService.patchExistingEntity(changes, entity)` | service | the unprefixed person change map and the existing `Person` | a patched `Person` clone | none directly |

### 2.3 What the census shows

**Six of seventeen call sites reach the repository directly** (I4, I6, I8, I9, I11, and
`getExisting`'s own internal use). Those are the hard boundaries: a service extraction
cannot intercept them at a service interface because they bypass it. See BR-PERSON-026.

**Every write is a direct repository call.** No consumer writes through
`PeopleCrudService.create` — it has no caller (BR-PERSON-026). Person rows are inserted at
I4, I9, and by cascade from I13 and I16.

**Two consumers write by cascade.** `customer` (I13) and `salesperson` (I16) only *build* a
`Person` via the converter; persistence happens when the consumer saves its own aggregate
and the association cascade propagates. Across a service boundary there is no cascade —
these flows must become explicit remote calls, and they lose transactional atomicity with
the consumer's own write.

**`customer` has no representation of its own.** At I12 the customer's transport model *is*
the person model. A customer is, externally, a person. This makes the `customer`/`person`
boundary especially artificial and is a strong argument for merging them in any service
grouping.

**Entity instances cross the boundary.** I1, I5, I6, I10, I12, I14, I15 and I17 pass or
return `Person` *entities*, not transport models. Entities carry persistence identity and
lifecycle state. Across a process boundary only serialized data can cross, so each of these
needs a transport-model equivalent that does not exist yet.

### 2.4 Outbound call boundaries

**None.** No code in this domain calls into any other domain. The domain reads nothing it
does not own.

---

## 3. Payload shapes at the boundary

Since there are no external integrations, the only boundary payload is the transport model,
specified in `specs/01-data-model.md` §2:

| Field | Type | Required | Note |
|---|---|---|---|
| `firstName` | string | yes at the route layer | |
| `lastName` | string | yes at the route layer | |
| `idNumber` | string | yes at the route layer | the business key |
| `email` | string | yes at the route layer | never format-validated |
| `phone1` | string | no | omitted from output when null |
| `phone2` | string | no | omitted from output when null |

No identifier field. No serialization format other than JSON over HTTP on the one read
route. No versioning, no content negotiation, no schema registry.

---

## 4. Retry, ordering and delivery semantics

**Not applicable.** With no message broker, no external client and no asynchronous
processing, there is no retry policy, no backoff, no circuit breaker, no timeout
configuration, no idempotency key, no ordering guarantee and no delivery guarantee to
document.

The only reliability property is the database transaction: every CRUD-service method runs
in one, and the `user` domain's registration and profile flows are transactional at the
consumer's service method.

**Extraction consequence.** That single shared transaction is what currently makes
"create a person, then a user, then a customer" atomic (`RegistrationServiceImpl`). Split
`person` into a service and that atomicity is gone — registration becomes a distributed
operation needing a saga or compensating delete, and there is currently **no delete path**
to compensate with (BR-PERSON-013, US-PERSON-009). This is the single most consequential
integration problem in extracting this domain.
