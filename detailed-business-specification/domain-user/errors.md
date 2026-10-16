# Identity & Access (`user`) — Error Conditions

Every error this domain can produce, with its **verbatim** message literal. Messages are
client-observable contract and port unchanged (G2).

---

## 1. Domain-specific exception types

| Type | Extends | Mapped by `ExceptionsControllerAdvice`? |
|---|---|---|
| `jpa/exceptions/UserNotFoundException` | `jakarta.persistence.EntityNotFoundException` | **yes**, via its superclass → 404 |
| `jpa/exceptions/PersonNotFoundException` (person domain) | `jakarta.persistence.EntityNotFoundException` | **yes** → 404 |
| `security/exceptions/AccountProtectionViolationException` | `java.lang.RuntimeException` | **NO** → 500 (BR-USER-043) |

`UserNotFoundException` is `@NoArgsConstructor` plus `(String message) { super(message); }`.
The asymmetry in the third row is the whole of BR-USER-043 and is preserved (G1).

---

## 2. Account and role administration

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E1 | Blank account name on any path that resolves a user | `BadInputException` | `Invalid user name` | **400** | BR-USER-039 |
| E2 | Blank role name on any path that resolves a role | `BadInputException` | `Invalid user role name` | **400** | BR-USER-040 |
| E3 | `POST /data/users` with a name already taken | `EntityExistsException` | `The item already exists` | **409** | BR-USER-001 |
| E4 | `POST /data/user_roles` with a name already taken | `EntityExistsException` | `The item already exists` | **409** | BR-USER-005 |
| E5 | `UserPojo.role` blank on create | `BadInputException` | `The user was not given any role` | **400** | BR-USER-002 |
| E6 | `UserPojo.role` naming a role that does not exist | `BadInputException` | `The specified user role does not exist` | **400** | BR-USER-002 |
| E7 | `DELETE /data/users?name=<self>` | `BadInputException` | `A user should not be able to delete their own account` | **400** | — |
| E8 | `DELETE` matching the configured protected account | `AccountProtectionViolationException` | `Protected account cannot be deleted` | **500** | BR-USER-041, BR-USER-043 |
| E9 | `DELETE /data/user_roles` for a role still referenced by an account | `DataIntegrityViolationException` | Spring's, no domain literal | **500** | BR-USER-047 |

**E7 only inspects the `name` parameter** — deleting yourself by `id` or `nameLike`
bypasses it (`02` §6.1).

**E8 is a 500, not a 403.** And the guard inspects only a single matched account
(BR-USER-042), so a multi-match filter that includes the protected account can pass it and
delete everything it matched.

**E9 is a 500, not a 409**, because the FK rejection is never translated. Deleting an
account, by contrast, succeeds and leaves its person intact (BR-USER-048) — there is no
cascade on `User.person`.

---

## 3. Registration

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E10 | Account name already taken | `EntityExistsException` | `That username is taken.` | **409** | BR-USER-009 |
| E11 | ID number already registered | `EntityExistsException` | `That ID number is already registered and associated to an account.` | **409** | BR-USER-010 |
| E12 | The `Customer` role is missing from the database | `IllegalStateException` | `No user role matches 'Customer', database might be compromised` | **500** | BR-USER-011 |
| E13 | Body violating any `@NotBlank` on `RegistrationPojo` **or**, via its `@Valid`, on the nested `PersonPojo` | `MethodArgumentNotValidException` | Spring's field-error rendering | **400** | — |

**E10 and E11 both carry a trailing full stop**, unlike most literals in this codebase.
Both run **before any write**, so neither can leave partial state.

**Both checks are inside the Phase 2 transaction.** `register` is now
`@Transactional(rollbackFor = {BadInputException.class, EntityExistsException.class})`, so
a failure at step 5 or 7 — after the `people` row is written — now rolls back. Previously
it left an **orphaned `people` row with no delete path anywhere in the system**. Recorded
deviation from BR-USER-012, per ADR-001 §Decision.1, proven by
`RegistrationServiceImplTransactionalTest`, and the basis for exit criterion **X8**.

---

## 4. Profile (`/account/profile`)

| # | Condition | Exception | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E14 | No account with the principal's username | `UserNotFoundException` | `There is no account with the specified username` | **404** | BR-USER-052 |
| E15 | The account exists but has no profile | `PersonNotFoundException` | `The account does not have an associated profile` | **404** | BR-USER-003, BR-USER-052 |
| E16 | The submitted person is already claimed by another account | `BadInputException` | `Person profile is associated to another account. Cannot use it.` | **400** | **BR-USER-004** |

**E14 and E15 are two distinct exceptions for two distinct conditions** — that is
BR-USER-052, and it exists only because an account may legitimately have no profile.
`getProfileFromUserName` uses `findByName` and then null-checks `getPerson()`, rather than
one of the `JOIN FETCH` queries, precisely so it can tell the two apart.

**E16 is the only enforcement of BR-USER-004 in the entire system** (`00` §2 W1). It runs
on the profile-update path only; `POST /data/users` and `POST /public/register` do not
perform it.

**There is no validation error on this route.** `updateProfile` takes `@RequestBody`
**without** `@Valid` (BR-USER-051), so blank names and malformed e-mail reach the service.
Adding `@Valid` would return 400 where the baseline returns 200 — a behavioural change
(G1).

---

## 5. Authentication, tokens and access discovery

| # | Condition | Exception / outcome | Verbatim message | HTTP | Rule |
|---|---|---|---|---|---|
| E17 | `POST /public/login` body unparseable as a login request | `BadCredentialsException` | `Request body is not a login request` | **401** | BR-USER-014 |
| E18 | `POST /public/guest` body unparseable | `BadCredentialsException` | `Invalid request body for guest session` | **401** | — |
| E19 | Guest profile data insufficient or invalid | `BadCredentialsException` | `Insufficient or invalid profile data for guest` | **401** | BR-USER-028 |
| E20 | Wrong credentials at either filter | `BadCredentialsException` | Spring Security's | **401** | — |
| E21 | Token present but malformed, or signature invalid | **status only**, `IllegalStateException` caught | `Token %s can't be trusted` — **constructed but never emitted** | **401, no body** | BR-USER-020 |
| E22 | No account matches the token's subject | `UsernameNotFoundException` | the **raw username** | **401** | BR-USER-034 |
| E23 | Authenticated principal lacking the required authority | `AccessDeniedException` | Spring Security's | **403** | BR-USER-049 |
| E24 | Anonymous request to a `@PreAuthorize("isAuthenticated()")` class | `AuthenticationException` | Spring Security's | **401** | BR-USER-050 |

**E17–E20 all surface from filters**, so they bypass `ExceptionsControllerAdvice` entirely
and are rendered by Spring Security's entry point. That is exactly why the D14 remainder
(b) ruling is PRESERVE: adding `@Validated` to the guest profile would raise
`ConstraintViolationException`, which the filter does **not** catch, turning E19's 401 into
a 500.

**E21 writes no response body at all** (BR-USER-020) — the 401 status is the whole
response. The log line is the Phase 0 **D2** fix:
`"Access denied: '{}' '{}' used an invalid token"` with the method and URI, and
**deliberately not the token**.

> **E21's message literal deserves attention during the port.**
> `ClaimsAuthorizationHeaderParserServiceImpl` builds
> `String.format("Token %s can't be trusted", token)` — **interpolating the whole live
> credential**. Today nothing emits it: `JwtTokenVerifierFilter` catches the exception and
> logs only method and URI, and `AccessController`'s handler discards it. So **no token
> leaks**. But the message exists on every failure and would leak the moment any handler
> logged `exc.getMessage()`. Same defect class as D2, one layer deeper. Recorded, not
> changed — altering it is out of Phase 2 scope (G1), and the fix belongs with whatever
> change first wants to log it.

**E22's message is the raw username.** It reaches the log but never a response body,
because of E21's and `AccessController`'s silent handlers.

---

## 6. Errors inherited from the shared kernel

| # | Condition | Exception | Verbatim message | HTTP |
|---|---|---|---|---|
| E25 | `PUT`/`PATCH` with **no** request parameters | `BadInputException` | `Missing request params` | **400** |
| E26 | `PUT`/`PATCH` whose filters match nothing | `EntityNotFoundException` | `No element was found to update` | **404** |
| E27 | `PUT`/`PATCH` whose filters match more than one row | `RuntimeException` | `Cannot update more than one item at a time` | 500 |
| E28 | `DELETE` whose parameters are **all** unrecognised | `BadInputException` | `No recognized filtering parameters were provided` | **400** |
| E29 | `GET`/`DELETE` finding nothing | `EntityNotFoundException` | `Requested item(s) not found` | **404** |
| E30 | `update(M)` — the single-argument overload | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 |
| E31 | A `BadInputException` raised inside `partialUpdate` | wrapped: `RuntimeException(e)` | the wrapped cause's message | **500** |
| E32 | `applyChangesToExistingEntity` on either converter | `UnsupportedOperationException` | `This method is deprecated` | 500 |
| E33 | `UsersPatchServiceImpl.patchExistingEntity(UserPojo, User)` | `UnsupportedOperationException` | `This method signature has been deprecated` | 500 |
| E34 | `UserRolesPatchServiceImpl.patchExistingEntity(UserRolePojo, UserRole)` | `UnsupportedOperationException` | `This method has been deprecated` | 500 |
| E35 | A non-string value under a patch key | `ClassCastException` | none | **500** |

**E28 is an intentional Phase 0 behaviour change (D1).** Before the fix, the same request
matched every row and **deleted the whole table** with HTTP 204.

**E33 and E34 differ by the single word "signature"** (`02` §5). Both strings exist in
this domain; normalising them would be a silent contract change. E30, E32, E33 and E34 are
all unreachable over HTTP, and all four literals port verbatim.

**E31 and E35 are both unreachable today** because neither controller maps PATCH
(BR-USER-044), but both port with the kernel and the patch services (BR-USER-045).

---

## 7. Deliberate non-errors — silence that is contract

### 7.1 An expired token
`JwtTokenVerifierFilter` simply does not populate the security context and **continues the
chain** (BR-USER-019). The request proceeds **anonymously**. Combined with BR-USER-025 —
no route-level rules, so an un-annotated endpoint is public — an expired token against an
un-annotated endpoint **succeeds**.

### 7.2 A missing or malformed `Authorization` header on `/access`
`getUserDetails` returns null, the handler returns null, and Spring renders an **empty 200
body** (BR-USER-035). Not an error, and distinguishable from E21's 401 only by status.

### 7.3 A revoked authority
Authorities travel inside the token and are **never re-checked** against the database
(BR-USER-022). A grant removed from `app_user_role_permissions` stays effective until the
token expires — and lifetime is the **sum** of three configured components
(BR-USER-017), so it can be arbitrarily long.

### 7.4 An unknown role name on the patch path
`UsersPatchServiceImpl` uses `.ifPresent(target::setUserRole)` with no `orElseThrow`, so
the role is silently left unchanged — where the converter raises E6 for the same input
(`02` §5.1).

### 7.5 A person that cannot be found on account creation
`UsersConverterServiceImpl.convertToNewEntity` looks the person up by `idNumber` and, if
absent, **leaves `person` null**. The account is created with no profile, HTTP 201, no
warning (BR-USER-003).

### 7.6 A duplicate authority grant
Before **D17** the database accepted it and login de-duplicated in memory
(BR-USER-007). D17 added `UQ_UROLEPERMS_UROLE_PERM`; the in-memory de-duplication was
**deliberately left in place**. No runtime path inserts grants anyway (BR-USER-038).

### 7.7 Dead and unenforced authorities
Four seeded authority codes are never referenced by any annotation (BR-USER-036), and
several seeded read authorities are never enforced on the routes they appear to describe
(BR-USER-037). Nothing warns. All 54 remain in the catalogue, and must (G2, X6).

### 7.8 A `PUT /data/users` that blanks a password
It cannot: `UserPojo.password` is `@NotBlank`, so a body without one is E13. But a body
*with* a different password **silently replaces the stored hash** (BR-USER-046) and
returns 204. There is no confirmation step, no old-password check, and no audit record —
`app_users` has no timestamp column.

### 7.9 A no-op patch
`flushPartialChanges` finds the patched copy `equals` to the original and **skips the
write**, returning success.

### 7.10 An empty-parameter `DELETE`
`DataCrudGenericController.delete` only parses a predicate when parameters are present, so
**no parameters at all is a silent no-op** — a different shape from E28 and also preserved.

---

## 8. What this domain never raises

- **No 403 from domain code.** Every authorisation decision is Spring Security's, driven
  by `@PreAuthorize`. There is no programmatic permission check anywhere.
- **No account-status error.** All four `UserDetails` flags are hardcoded `true`
  (BR-USER-023); an account cannot be locked, expired or disabled. Register item **D15**.
- **No password-policy error, and no password-change path at all.** D15 again — ruled
  PRESERVE, out of scope, first post-conversion Identity backlog item.
- **No error when a second account claims a person** on the create paths — only on profile
  update (E16, BR-USER-004).
- **No optimistic-locking failure.** No `@Version` on any of the four entities; concurrent
  writers last-write-win.
- **No error for a weak or short secret at request time** — but the application **fails
  fast at startup** instead, because `SecurityProperties` is `@Validated` with
  `@NotBlank jwtSecretKey`, and `Keys.hmacShaKeyFor` rejects a key under 256 bits
  (BR-USER-018). That is the property that makes supplying the secret as
  `${TREBOL_JWT_SECRET_KEY}` with **no default** safe under G7.
