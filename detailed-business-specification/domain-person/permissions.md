# Person Registry — Permissions

The authentication mechanism, how authorities are granted to roles, and the system-wide
authority catalog are specified in
`cross-cutting-documentation/authentication-authorization.md` and
`cross-cutting-documentation/common-permissions.md`. This document covers only the
authorities governing this domain's resources.

---

## 1. Domain-owned authorities

| Authority (literal string) | Resource | Operation | Enforcement point |
|---|---|---|---|
| `people:read` | `people` | list / search | Declarative method-level rule on `DataPeopleController.readMany` |

**One authority, one operation.** The literal value is `people:read` — this is the exact
string checked at runtime, taken from the authority declaration on the handler, not from a
description or documentation annotation.

### 1.1 Class-level requirement

`DataPeopleController` additionally carries a class-level rule requiring the caller to be
authenticated. It applies to every handler in the controller. Since the only handler also
requires `people:read`, the method-level authority is the operative constraint — but a
rewrite that adds handlers inherits the authentication requirement by default and must
add its own authority rule explicitly.

---

## 2. Authorities that do not exist

There is **no** `people:create`, `people:update`, `people:delete` or equivalent write
authority anywhere in the permission catalog.

This is a direct consequence of BR-PERSON-013: the domain exposes no write route, so there
is no write operation to protect. Person records are created and modified exclusively
through in-process calls from the `user`, `customer` and `salesperson` domains, and those
calls are governed by **the consumer's** authorities, not by any person authority.

| Write path | Governed by |
|---|---|
| Self-registration creating a person | the `user` domain's registration route — a **public** route requiring no authority |
| Profile editing patching a person | the `user` domain's profile route — requires the caller to be the authenticated account holder |
| Customer creation cascading a person | the `customer` domain's own authorities |
| Salesperson creation cascading a person | the `salesperson` domain's own authorities |

**Security implication.** Person records can be created by an **unauthenticated** caller
through the self-registration flow, while *reading* the registry requires an explicit
authority. Write is less protected than read. That is a defensible design for a
registration flow, but it must be a conscious choice in any extraction — see §4.

---

## 3. What `people:read` actually grants

A single authority grants the ability to page through the **entire** person registry with
no scoping and no field-level redaction: every person's legal name, national identification
number, e-mail address and both phone numbers.

| Property | Value |
|---|---|
| Row scoping | none — all rows are visible |
| Field redaction | none — all fields except the surrogate identifier are returned |
| Ownership filtering | none — a caller cannot be restricted to "their own" person record |
| Search breadth | full — including case-insensitive substring search across names, identification numbers and e-mails |

Note also BR-PERSON-017: an unescaped metacharacter in a `*Like` filter (for example
`?idNumberLike=%`) returns every person, so even a caller attempting a narrow search can
trivially retrieve the whole registry.

This is the domain's principal data-exposure surface. A rewrite handling personal data
under a privacy regime should review whether one coarse authority is the right granularity,
and whether the identification number and phone numbers should be returned to every holder
of it.

---

## 4. Domain-scoped roles

**None.** This domain defines no role of its own. `people:read` is granted to roles defined
in the system-wide catalog; see `cross-cutting-documentation/common-permissions.md` for
which roles hold it.

---

## 5. Extraction considerations

| # | Consideration |
|---|---|
| 1 | **Write authorities must be invented.** Extracting the domain requires write endpoints (see `specs/05-api-contract.md` §5), and therefore authorities that do not exist today — for example `people:create` and `people:update`. There is no precedent in the catalog to copy. |
| 2 | **The unauthenticated-write path must be preserved deliberately.** Self-registration creates person records with no authority at all. If the extracted service requires an authority for all writes, the registration flow breaks unless the `user` service calls it with a service-level credential. |
| 3 | **Consumer authorities no longer transfer.** Today a consumer domain's authority check implicitly governs the person write that follows it in-process. Across a service boundary the person service sees only the caller's token — the authorization decision has to be re-established, either by propagating the end-user identity or by trusting the calling service. |
| 4 | **Read scoping is the natural place to tighten.** Since `people:read` currently grants full enumeration, an extraction is a good opportunity to introduce row scoping or field redaction — but note this changes behaviour for existing holders of the authority. |
