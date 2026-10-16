# Customer — Error Conditions

Every error this domain can produce, with its **verbatim** message literal. Messages are
client-observable contract and port unchanged (G2).

---

## 1. Domain-specific errors

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E1 | `idNumber` is null, empty or whitespace-only on any path that resolves a customer | `BadInputException` | `Customer does not have an ID card` | **400** | BR-CUSTOMER-006 |
| E2 | `applyChangesToExistingEntity` is called on the converter | `UnsupportedOperationException` | `This method is deprecated` | 500 | — |
| E3 | `patchExistingEntity(PersonPojo, Customer)` is called on the patch service | `UnsupportedOperationException` | `This method has been deprecated` | 500 | — |

E2 and E3 are **unreachable through any route**, and their two literals are **different
strings**. Both are preserved exactly; normalising them would be a silent contract change.

---

## 2. Errors inherited from `CrudGenericService`

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E4 | `POST` with an `idNumber` that already has a customer | `EntityExistsException` | `The item already exists` | **409** | BR-CUSTOMER-007 |
| E5 | `PUT` or `DELETE` whose filters match nothing | `EntityNotFoundException` | `Requested item(s) not found` | **404** | — |
| E6 | `PUT` whose filters match more than one row | `RuntimeException` | `Cannot update more than one item at a time` | 500 | **BR-CUSTOMER-021** |
| E7 | `update(M)` — the single-argument overload — is called | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 | — |
| E8 | A `BadInputException` raised inside `partialUpdate` | wrapped: `RuntimeException(e)` | the wrapped cause's message | **500** | BR-CUSTOMER-011 |

**E6 is a 500, not a 400**, because `CrudGenericService` throws a bare `RuntimeException`
rather than `BadInputException`. A client cannot distinguish "your filter was ambiguous"
from an internal fault. Preserved (G1).

**E8 turns a 400 into a 500** by wrapping. Unreachable for customers today — there is no
PATCH route (BR-CUSTOMER-010) — but it ports with the kernel and becomes reachable the
moment one is added.

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
matched every row and deleted the entire table with HTTP 204. Recorded in the register's
"Intentional observable-behaviour changes" table.

**E13 is a genuine latent fault, not a hypothetical.** `getExisting` reads
`input.getIdNumber()` with no null guard (`02` §3.2). It is reachable through
`CustomerProvisioningContract.resolveOrCreateCustomer`, which the checkout path calls with
whatever the order carried. `OrdersConverterServiceImplTest.no_customer_information`
pins the current behaviour. **Not fixed in Phase 2** — a null-guard here would change an
observable 500 into a 400 (G1), and it belongs with the Phase 3 checkout work.

---

## 4. Errors on the guest path

| # | Condition | Exception | Verbatim message | HTTP |
|---|---|---|---|---|
| E14 | Guest profile data insufficient or invalid | `BadCredentialsException` | `Insufficient or invalid profile data for guest` | **401** |
| E15 | Guest request body unparseable | `BadCredentialsException` | `Invalid request body for guest session` | **401** |

Both are raised by `JwtGuestAuthenticationFilter`, which converts
`GuestCustomerProvisioningService`'s `BadInputException` and an `IOException`
respectively. Because they surface **from a filter**, they bypass
`ExceptionsControllerAdvice` entirely and are rendered by Spring Security's entry point.

This is precisely why the D14 remainder (b) ruling is PRESERVE: adding `@Validated` to the
guest profile would raise `ConstraintViolationException`, which this filter does **not**
catch, turning E14's 401 into a 500.

---

## 5. Deliberate non-errors — silence that is contract

### 5.1 An already-registered guest
`provisionCustomer` swallows `EntityExistsException` and logs
`"Guest with idNumber={} is already registered in the database"` at INFO. The guest
session succeeds (BR-CUSTOMER-018). **Not an error.**

### 5.2 An unrecognised filter parameter
`parseMap`'s `default: break` ignores it silently. On `GET` this widens the result set; on
`PUT`/`PATCH` it is the D19 match-all shape, ruled PRESERVE in scope.

### 5.3 A `PUT` that orphans a `people` row
The replacement succeeds with HTTP 204 and **no error of any kind**, while leaving an
unreferenced `people` row that no code path deletes (`00` §2 W2). Silent data growth,
ruled PRESERVE.

### 5.4 Deleting a customer — the recorded contradiction
`Customer.person` declares `cascade = CascadeType.ALL`, so a successful delete would
cascade to `people`. **BR-CUSTOMER-014 asserts the opposite** — that the person survives.

In practice the delete is usually blocked first by `FK_ORDER_CUSTOM_ID` from `orders`
(BR-CUSTOMER-015), which surfaces as a `DataIntegrityViolationException` → **HTTP 500**,
not a 409. So the rule appears to hold for the wrong reason.

**The code is authoritative for the port** (G1, G2): the cascade is carried over verbatim.
**Action required of the owner:** correct BR-CUSTOMER-014, or rule that the cascade should
be narrowed — which is a behavioural change needing its own ruling. Recorded alongside D22
as a specification-versus-code discrepancy found while writing a porting-depth spec, which
is exactly what the exercise is for.

### 5.5 A non-numeric `id` filter
Caught, logged at INFO, and the parameter is dropped. The request proceeds with whatever
other filters parsed.

---

## 6. What this domain never raises

- **No 403 from domain code.** Every authorisation decision is Spring Security's, driven
  by `@PreAuthorize`; there is no programmatic permission check anywhere in the domain.
- **No optimistic-locking failure.** No `@Version` field exists, so concurrent writers
  silently last-write-win.
- **No validation error on phone numbers.** Both `@Pattern` annotations on `PersonPojo`
  are commented out (`01` §2).
- **No 409 on delete.** An FK conflict is a 500, per §5.4.
