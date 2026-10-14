# Salesperson — Error Conditions

Every error this domain can produce, with its **verbatim** message literal. Messages are
client-observable contract and port unchanged (G2).

---

## 1. Domain-specific errors

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E1 | `idNumber` is null, empty or whitespace-only on any path that resolves a salesperson | `BadInputException` | `Salesperson does not have an ID card` | **400** | BR-SALESPERSON-006 |
| E2 | `applyChangesToExistingEntity` is called on the converter | `UnsupportedOperationException` | `This method is deprecated` | 500 | — |
| E3 | `patchExistingEntity(PersonPojo, Salesperson)` is called on the patch service | `UnsupportedOperationException` | `This method has been deprecated` | 500 | — |

**E1's literal is `Salesperson does not have an ID card`** — a *different string* from the
customer domain's `Customer does not have an ID card`. The two must not be collapsed into
a shared constant during the port; a client distinguishing them today would stop being
able to.

E2 and E3 are unreachable through any route, and their two literals differ from each
other. All three strings port verbatim.

---

## 2. Errors inherited from `CrudGenericService`

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E4 | `POST` with an `idNumber` that already has a salesperson | `EntityExistsException` | `The item already exists` | **409** | BR-SALESPERSON-007 |
| E5 | `PUT` or `DELETE` whose filters match nothing | `EntityNotFoundException` | `Requested item(s) not found` | **404** | — |
| E6 | `PUT` whose filters match more than one row | `RuntimeException` | `Cannot update more than one item at a time` | 500 | **BR-SALESPERSON-022** |
| E7 | `update(M)` — the single-argument overload — is called | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 | — |
| E8 | A `BadInputException` raised inside `partialUpdate` | wrapped: `RuntimeException(e)` | the wrapped cause's message | **500** | BR-SALESPERSON-011 |

**E6 is a 500, not a 400** — `CrudGenericService` throws a bare `RuntimeException`, so a
client cannot distinguish an ambiguous filter from an internal fault. Preserved (G1).

**E8 turns a 400 into a 500** by wrapping. Unreachable today — there is no PATCH route
(BR-SALESPERSON-010) — but it ports with the kernel.

---

## 3. Errors from the shared kernel and the framework

| # | Condition | Exception | Message source | HTTP |
|---|---|---|---|---|
| E9 | `POST`/`PUT` body violating any `@NotBlank` on `PersonPojo` | `MethodArgumentNotValidException` | Spring's field-error rendering, **not** a domain literal | **400** |
| E10 | `DELETE` whose request parameters are **all** unrecognised | `BadInputException` | the Phase 0 **D1** guard in `DataCrudGenericController` | **400** |
| E11 | Authenticated principal lacking the required authority | `AccessDeniedException` | Spring Security | **403** |
| E12 | No authentication at all against a `@PreAuthorize("isAuthenticated()")` class | `AuthenticationException` | Spring Security | **401** |
| E13 | `getExisting` called with a **null** `PersonPojo` | `NullPointerException` | none | **500** |

**E10 is an intentional Phase 0 behaviour change.** Before the D1 fix the same request
matched every row and deleted the entire table with HTTP 204.

**E13 is less exposed here than in the customer domain**, because no contract method feeds
external data into `getExisting` — `SalespersonLookupContract` is read-only and takes an
entity (`02` §8). It remains reachable from the controller only through a body that
`@Valid` would already have rejected.

---

## 4. The error this domain *should* be able to raise, and cannot

| # | Condition | What happens | Why it matters |
|---|---|---|---|
| E14 | Two salesperson rows share one `person` | `findByPersonIdNumber` raises a **non-unique-result** exception from Spring Data | **500**, with a framework message and no domain literal |

`salespeople.person_id` has no unique key and the entity is `@ManyToOne`, so this state is
**legal** by BR-SALESPERSON-002 — but the repository method that looks a salesperson up by
business key returns `Optional` and therefore cannot represent it (`01` §1.5).

Unreachable in practice only because `people.person_id_number` carries
`UNIQUE KEY UK_PERSON_ID_NO`, so two salespeople can be attached to the same person row
only by direct database manipulation or through the order-side cascade
(BR-SALESPERSON-016, `00` §2 W2).

**Recorded, not fixed.** Adding a unique key would reject data the model explicitly
permits; changing the return type to a collection would be an API change. Either is a
behavioural change needing its own ruling (G1, G2). It is the clearest example of why
this domain's schema asymmetry must be ported rather than tidied.

---

## 5. Deliberate non-errors — silence that is contract

### 5.1 An unrecognised filter parameter
`parseMap`'s `default: break` ignores it silently. On `GET` this widens the result set; on
`PUT`/`PATCH` it is the D19 match-all shape, ruled PRESERVE in scope.

### 5.2 A `PUT` that orphans a `people` row
The replacement succeeds with HTTP 204 and **no error of any kind**, while leaving an
unreferenced `people` row that nothing deletes (BR-SALESPERSON-013, `00` §2 W3). Silent
data growth, ruled PRESERVE.

### 5.3 A no-op `PATCH`
`flushPartialChanges` finds the patched copy `equals` to the original and **skips the
write** entirely, returning success. No error, and no `updated_at` to betray it — there is
no audit column and no `@Version`.

### 5.4 Deleting a salesperson — the recorded contradiction
`Salesperson.person` declares `cascade = CascadeType.ALL`, so a successful delete would
cascade to `people`. **BR-SALESPERSON-018 asserts the opposite** — that the person
survives.

In practice the delete is usually blocked first by `FK_ORDER_SPERSON` from `orders`
(BR-SALESPERSON-019), which surfaces as a `DataIntegrityViolationException` → **HTTP 500**,
not a 409. So the rule appears to hold for the wrong reason.

**The code is authoritative for the port** (G1, G2): the cascade carries over verbatim.
**Action required of the owner:** correct BR-SALESPERSON-018, or rule that the cascade
should be narrowed — a behavioural change needing its own ruling. This is the same
contradiction as BR-CUSTOMER-014, in the sibling domain, and both were found while writing
these porting-depth specs.

### 5.5 A non-numeric `id` filter
Caught, logged at INFO, and the parameter is dropped. The request proceeds with whatever
other filters parsed.

### 5.6 An order with no salesperson
Entirely normal. The FK is nullable (BR-SALESPERSON-014) and **no route ever sets it**
(BR-SALESPERSON-015), so in practice every order has `salesperson_id IS NULL`. Nothing
validates, warns or reports on it. This is what makes the Phase 3 FK cut cheap.

---

## 6. What this domain never raises

- **No 403 from domain code.** Every authorisation decision is Spring Security's, driven
  by `@PreAuthorize`; there is no programmatic permission check in the domain.
- **No authentication error of any kind.** A salesperson is not a login account
  (BR-SALESPERSON-020); there is no credential, no password and no relationship to
  `app_users`, so no authentication path touches this table.
- **No optimistic-locking failure.** No `@Version` field; concurrent writers last-write-win.
- **No validation error on phone numbers.** Both `@Pattern` annotations on `PersonPojo`
  are commented out (`01` §2).
- **No 409 on delete.** An FK conflict is a 500, per §5.4.
- **No error when a person is shared.** Per §4 — the state is legal and silent until a
  lookup trips over it.
