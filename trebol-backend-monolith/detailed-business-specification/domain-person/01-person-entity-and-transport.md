# 01 — Person Entity, Transport Model and Exception Type

Source files covered:

| File | LOC | Role |
|---|---|---|
| `src/main/java/org/trebol/jpa/entities/Person.java` | 93 | persisted entity |
| `src/main/java/org/trebol/api/models/PersonPojo.java` | 51 | transport model |
| `src/main/java/org/trebol/jpa/exceptions/PersonNotFoundException.java` | 34 | exception type |

Field-level column definitions live in `specs/01-data-model.md`. This document specifies
**behaviour**: what each constructor, accessor and generated member does, and the
consequences of each.

---

## 1. `Person` — persisted entity

### 1.1 Class overview

`Person` is the single persisted type of this domain. It is a flat record of a natural
person — legal first and last name, national identification number, e-mail address, and
two phone numbers — with a database-generated surrogate key. It holds no associations: it
does not reference a user account, a customer record, or a salesperson record, even
though all three of those point *at* it.

The class is a plain mutable data holder. It contains no validation logic, no computed
properties, no lifecycle callbacks, and no business methods. All behaviour that acts on a
person lives in the service layer (document `02`). A reimplementation should treat this as
a value-carrying record with a generated identity, not as a rich domain object.

It is serializable (via the shared-kernel `DBEntity` contract) with an explicitly declared
serial version identifier of `9L`. The explicit value matters only if the rewrite must
remain binary-compatible with serialized instances from the existing system; otherwise it
can be discarded.

### 1.2 Constants

| Constant | Value | Visibility | Purpose |
|---|---|---|---|
| `serialVersionUID` | `9L` | private static | Serialization compatibility marker. Not business data. No other code reads it. |

No other constants are declared.

### 1.3 Generated members

The class delegates boilerplate to a code generator. The generated surface is part of the
contract and must be reproduced behaviourally:

| Generated member | Behaviour |
|---|---|
| No-argument constructor | Creates an instance with every field null — **including `phone1` and `phone2`**. The empty-string phone defaults do **not** apply here; they are attached to the builder only. See §1.4.2 for why this matters. |
| All-arguments constructor | Takes all seven fields positionally in declaration order (`id`, `firstName`, `lastName`, `idNumber`, `email`, `phone1`, `phone2`) and assigns each verbatim. No validation, no defaulting. |
| Builder | Fluent construction. Applies `""` as the default for `phone1` and `phone2` when those are not explicitly set. All other fields default to null. Terminal build performs no validation. |
| Getters | One per field, returning the stored reference with no copying or transformation. |
| Setters | One per field, assigning the given value with no validation, no normalization, no trimming, and no null rejection. A setter will happily store null into a field whose column is `NOT NULL`; the failure surfaces later, at flush time, from the database. |
| Equality / hash | Computed over **all seven fields, including `id`**. See §1.5 — this has a direct and non-obvious effect on whether a patch performs a database write. |
| String representation | Includes all seven field values. **Caution:** this emits personal data — name, national ID number, e-mail and both phone numbers — into any log statement that interpolates a `Person`. A rewrite handling personal data under a privacy regime should not reproduce this verbatim. |

### 1.4 Constructors

#### 1.4.1 Copy constructor `Person(Person source)`

**Purpose.** Produce a detached duplicate of an existing person. This is the mechanism
that makes patching non-destructive: the patch service clones the loaded entity, mutates
the clone, and leaves the original untouched so the "did anything change?" comparison in
§1.5 is meaningful.

**Parameters.** `source` — the instance to copy. Required.

**Behaviour.** Assigns all seven fields one by one from the source: `id`, `firstName`,
`lastName`, `idNumber`, `email`, `phone1`, `phone2`. The copy is field-for-field and
exhaustive — no field is skipped.

Critically, **`id` is copied too**. The clone therefore represents *the same* database row
as the source, not a new row. Saving the clone updates the existing row rather than
inserting a second one. A rewrite that omits the `id` copy will silently turn every patch
operation into an insert, duplicating people and violating the uniqueness constraint on
the identification number.

**Return / side effects.** A new in-memory instance. No persistence interaction, no
mutation of `source`.

**Error paths.** A null `source` causes a null-dereference failure on the first field
read. The parameter is not checked. No domain-specific exception is raised — this is an
unguarded programming error, not a handled condition.

#### 1.4.2 The null-phone trap

Three construction routes produce different phone states, and the difference is
behaviourally significant:

| Route | Resulting `phone1` / `phone2` |
|---|---|
| Builder, phones not set | `""` (empty string) |
| No-argument constructor | `null` |
| All-arguments constructor | whatever was passed, including `null` |
| Copy constructor | whatever the source held, including `null` |

Both phone columns are `NOT NULL` with no database-level default. An instance that reaches
the database with a null phone fails at flush time with a constraint violation rather than
a readable validation error. The domain's own creation path always uses the builder and so
is safe (document `02` §3.2); the risk exists for any new write path a rewrite introduces.

### 1.5 Equality semantics and their effect on writes

Equality covers all seven fields including the identifier. Two consequences:

1. **Two instances are equal only if every field matches.** A loaded entity and a patched
   clone are equal exactly when the patch changed nothing.
2. **The generic CRUD service exploits this.** Its partial-change flush compares the
   original entity to the patched clone and, if they are equal, returns the original
   **without issuing a database write** (document `02` §3.4). This is the mechanism behind
   BR-PERSON-009 — a no-op patch produces no write and no modification.

A rewrite that defines equality by identifier alone (a common and otherwise reasonable
choice for persisted entities) **breaks this**: every patched clone would compare equal to
its source because they share an `id`, and therefore *no* patch would ever be written.
Conversely, defining equality by business key (`idNumber`) alone would suppress any patch
that does not change the identification number. The all-fields comparison is load-bearing
and must be preserved, or the flush logic must be rewritten to compare explicitly.

### 1.6 Lifecycle callbacks

**None.** The entity declares no pre-persist, post-load, pre-update, post-update,
pre-remove or post-remove hook. There is no auditing, no timestamp maintenance, no
normalization-on-save, and no AOP-intercepted handler chain. What the service layer
assembles is exactly what is persisted.

### 1.7 Save / delete overrides

**None.** The entity contributes no persistence override. All persistence behaviour comes
from the shared-kernel repository contract.

### 1.8 Static finder methods

**None** on the entity. Query entry points live on the repository interface (document `02`
§2).

---

## 2. `PersonPojo` — transport model

### 2.1 Class overview

`PersonPojo` is the external representation of a person: the request body shape for
inbound writes and the response element shape for the list endpoint. It is a flat mutable
data holder with six fields and no behaviour.

Its most consequential property is a deliberate omission: **it has no identifier field**.
External callers can neither read nor supply a person's surrogate key. Every externally
initiated person lookup is therefore keyed on the national identification number, which is
why that field — and not the primary key — is the domain's operative business key.

### 2.2 Constants

None.

### 2.3 Generated members

| Generated member | Behaviour |
|---|---|
| No-argument constructor | All six fields null. Required for deserialization of inbound request bodies. |
| All-arguments constructor | Six fields positionally in declaration order (`firstName`, `lastName`, `idNumber`, `email`, `phone1`, `phone2`), assigned verbatim. |
| Builder | Fluent construction. **No defaults for any field** — unlike the entity builder, the transport builder does not substitute empty strings for absent phones. Unset fields remain null. |
| Getters / setters | One pair per field. Direct access, no validation or transformation. |
| Equality / hash / string representation | Computed over all six fields. The string form includes personal data; the same logging caution as §1.3 applies. |

### 2.4 Validation rules

| Field | Rule | Enforced where |
|---|---|---|
| `firstName` | must be present and not blank | transport layer, on validated inbound request bodies only |
| `lastName` | must be present and not blank | as above |
| `idNumber` | must be present and not blank | as above |
| `email` | must be present and not blank | as above |
| `phone1` | none active | — |
| `phone2` | none active | — |

**Scope limit.** These rules fire only when the model arrives as a request body on a route
that requests validation. They do **not** fire on the internal creation path, where
consumer domains hand a model instance directly to the CRUD service. On that path the only
surviving guard is the identification-number blank check inside the CRUD service
(document `02` §3.1). This is the substance of BR-PERSON-004: the four mandatory-field
rules are route-layer rules, not domain invariants.

**Note on `email`:** the rule requires non-blankness only. Nothing in the domain — not the
transport model, not the entity, not any service — checks that the value resembles an
e-mail address. `"x"` is an acceptable e-mail as far as this domain is concerned, subject
only to the application-level 5-character minimum on the entity field. BR-PERSON-005.

### 2.5 Known quirks

**Disabled phone format validation.** Both phone fields carry a commented-out format rule
in the source:

```
^(((\(\+?[0-9]{3}\))|(\+?[0-9]{3})) ?)?[0-9]{3,4}[ -]?[0-9]{4}$
```

It is inert — phone values of any shape are accepted and stored. The commented pattern
records the intended format (an optional 3-digit country code, optionally parenthesised
and optionally `+`-prefixed, then 3–4 digits, an optional space or hyphen, then 4 digits).

A rewrite has a genuine choice here, and it should be made consciously: enabling the
pattern is arguably the intent, but it will reject phone values that the current system
accepts and may already have stored. Document the decision; do not enable it silently.
BR-PERSON-006.

### 2.6 Serialization behaviour

Null fields are **omitted** from serialized output rather than rendered as explicit nulls.
Combined with the converter's null-guard behaviour (document `02` §3.2), the practical
effect on the list endpoint is: a person whose stored phone is the empty string serializes
with `phone1: ""`, while a person whose stored phone is null — only reachable for rows
written outside the builder path — omits the field entirely. Response consumers must treat
absent and empty as equivalent.

---

## 3. `PersonNotFoundException`

### 3.1 Class overview

A domain-specific exception type signifying that a requested person does not exist. It
extends the persistence framework's standard entity-not-found exception, and therefore
inherits its handling: the application's error translation layer maps it to **HTTP 404
Not Found** (see `cross-cutting-documentation/error-handling-patterns.md`).

### 3.2 Constructors

| Constructor | Behaviour |
|---|---|
| No-argument | Generated. Produces an instance with no message. |
| `PersonNotFoundException(String message)` | Passes the message straight to the parent. No transformation, no default message, no message constant. |

### 3.3 Behaviour

The type carries no state of its own, no error code field, and no logic. Its entire
contribution is its identity as a distinct type.

### 3.4 Where the type is actually thrown — exactly one site

`PersonNotFoundException` has **exactly one throw site in the entire codebase**, and it is
**not in this domain**:

| Throw site | Message (literal) | Condition |
|---|---|---|
| `src/main/java/org/trebol/api/services/impl/ProfileServiceImpl.java:74` (the `user` domain) | `"The account does not have an associated profile"` | An authenticated account is found, but its person association is null — the account exists without a profile. |

So the type is a **cross-domain contract**: this domain declares it, and the `user` domain
raises it. A rewrite that extracts `person` as a service must decide where the type lives,
because the only code that throws it belongs to a different bounded context. The condition
it reports — "an account with no linked person" — is a `user` domain invariant violation,
not a `person` domain one.

### 3.5 Known quirk — this domain never throws its own exception

Although the type exists, **no code path in the `person` domain itself throws it.** This
domain's own not-found conditions arise inside the shared-kernel CRUD base, which throws
the *framework's* generic entity-not-found exception with the shared literal message
`"Requested item(s) not found"` (document `02` §3.7) — not this type.

Consequences for a rewrite:

1. A 404 originating from `GET /data/people` or from this domain's service API carries the
   generic shared message, not a person-specific one.
2. A 404 carrying `"The account does not have an associated profile"` originates from the
   `user` domain, even though the exception type is declared here.

Recorded as BR-PERSON-023 in `specs/03-business-rules.md`.
