# Identity & Access (`user`) — Services, Controllers and Security

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`01-user-entity-and-transport.md`. Paths relative to `src/main/java/org/trebol/`.

---

## 1. Service contracts

| Contract | Extends | Declares |
|---|---|---|
| `jpa/services/crud/UsersCrudService` | `CrudService<UserPojo, User>` | nothing of its own |
| `jpa/services/crud/UserRolesCrudService` | `CrudService<UserRolePojo, UserRole>` | nothing of its own |
| `jpa/services/conversion/UsersConverterService` | `ConverterService<UserPojo, User>` | nothing of its own |
| `jpa/services/conversion/UserRolesConverterService` | `ConverterService<UserRolePojo, UserRole>` | nothing of its own |
| `jpa/services/patch/UsersPatchService` | `PatchService<UserPojo, User>` | nothing of its own |
| `jpa/services/patch/UserRolesPatchService` | `PatchService<UserRolePojo, UserRole>` | nothing of its own |
| `jpa/services/predicates/UsersPredicateService` | `PredicateService<User>` | `QUser basePath = QUser.user;` |
| `jpa/services/predicates/UserRolesPredicateService` | `PredicateService<UserRole>` | `QUserRole basePath = QUserRole.userRole;` |

Plus three domain-specific application contracts: `api/services/RegistrationService`
(§8.1), `api/services/ProfileService` (§8.2),
`security/services/AuthorizedApiService` (§7), `security/services/UserPermissionsService`
(§9.6) and `security/services/AuthorizationHeaderParserService<T>` (§9.7).

---

## 2. Repositories

Base: `jpa/Repository.java`

```java
public interface Repository<E extends DBEntity>
    extends JpaRepository<E, Long>, QuerydslPredicateExecutor<E> { }
```

**It is not annotated `@NoRepositoryBean`**, which is why no `@DataJpaTest` slice can
bootstrap this project — scanning fails with *"Not a managed type: interface
org.trebol.jpa.DBEntity"*. Identity inherits that limitation, so its integration-style
tests must be `@SpringBootTest`. Recorded on `UserRolePermissionUniquenessTest` and
`RegistrationServiceImplTransactionalTest`.

### 2.1 `UsersRepository`

```java
Optional<User> findByName(String name);

@Query("SELECT u FROM User u JOIN FETCH u.userRole WHERE u.name = :name")
Optional<User> findByNameWithRole(@Param("name") String name);

@Query("SELECT u FROM User u JOIN FETCH u.person WHERE u.name = :name")
Optional<User> findByNameWithProfile(@Param("name") String name);

@Query("SELECT u FROM User u JOIN FETCH u.person WHERE u.id = :id")
Optional<User> findByIdWithProfile(@Param("id") Long id);

@Query("SELECT u FROM User u JOIN FETCH u.person p WHERE p.idNumber = :idNumber")
Optional<User> findByPersonIdNumber(@Param("idNumber") String idNumber);
```

Five methods, and the four `JOIN FETCH` variants exist **because both relations are LAZY**
(`01` §1.3). Which one a caller picks is behaviour, not optimisation:

| Method | Used by | Why that one |
|---|---|---|
| `findByName` | `UsersCrudServiceImpl.getExisting` | existence check only; no relation needed |
| `findByNameWithRole` | `UserDetailsServiceImpl` | authority assembly needs the role |
| `findByNameWithProfile` / `findByIdWithProfile` | profile reads | needs the person |
| `findByPersonIdNumber` | `ProfileServiceImpl` | the **BR-USER-004 enforcement check** |

**Note the three `JOIN FETCH`es on `u.person` are inner joins.** Since `person` is
optional (BR-USER-003), an account with **no** profile is **silently absent** from those
three result sets — it does not return a `User` with a null person, it returns
`Optional.empty()`. `ProfileServiceImpl` does not rely on them for its null-profile path;
it uses `findByName` and then checks `getPerson() == null`, which is what makes
BR-USER-052's two distinct exceptions possible.

### 2.2 `UserRolesRepository`

```java
@Query
Optional<UserRole> findByName(String name);
```

**A bare `@Query` with no value** (`00` §2 W16). It works as a Spring Data derived query
from the method name; the annotation is misleading but harmless. Port as-is — "fixing" it
by deleting the annotation is a diff with no behavioural content, and adding a JPQL value
risks changing the generated SQL.

### 2.3 `PermissionsRepository`

```java
public interface PermissionsRepository extends Repository<Permission> { }
```

**Empty.** No declared method, and no code path writes `app_permissions` at runtime —
BR-USER-038, grants and permissions are seed data only. The bean exists because the
generic kernel expects one per entity.

### 2.4 `UserRolePermissionsRepository`

```java
@Query("SELECT urp FROM UserRolePermission urp JOIN FETCH urp.permission WHERE urp.userRole.id = :userRoleId")
List<UserRolePermission> deepFindPermissionsByUserRoleId(@Param("userRoleId") Long userRoleId);
```

**This single query is the whole authorization model's data access.** It is called on
every login (§9.6) and on every guest session (§9.5), and the `JOIN FETCH` is required —
`permission` is LAZY, and the caller dereferences `getPermission().getCode()` after the
session may have closed.

It returns a `List`, which is why de-duplication happens in memory (§9.6).

---

## 3. CRUD services

### 3.1 `UsersCrudServiceImpl`

```java
@Transactional
@Service
public class UsersCrudServiceImpl
    extends CrudGenericService<UserPojo, User>
    implements UsersCrudService {
```

Class-level `@Transactional`, **no attributes**. Constructor-injects `UsersRepository`,
`UsersConverterService`, `UsersPatchService` **and `SecurityProperties`** — the last one
only for the delete guard.

**Override 1 — `getExisting`:**

```java
@Override
public Optional<User> getExisting(UserPojo input) throws BadInputException {
    String name = input.getName();
    if (StringUtils.isBlank(name)) {
        throw new BadInputException("Invalid user name");
    } else {
        return usersRepository.findByName(name);
    }
}
```

Literal `"Invalid user name"` (BR-USER-039). Keyed by **account name**, not by person
idNumber — the one place this domain's business key differs from `customer`/`salesperson`.

**Override 2 — `delete`, the account-protection guard (BR-USER-041):**

```java
@Override
public void delete(Predicate filters) throws EntityNotFoundException {
    if (securityProperties.isAccountProtectionEnabled()) {
        Optional<User> optionalUser = usersRepository.findOne(filters);
        if (optionalUser.isPresent() &&
            optionalUser.get().getId() == securityProperties.getProtectedAccountId()) {
            throw new AccountProtectionViolationException("Protected account cannot be deleted");
        }
    }
    super.delete(filters);
}
```

Three defects in nine lines, **all preserved** (G1):

1. **The guard inspects only one matched account** (BR-USER-042). `findOne(filters)` on a
   multi-match predicate returns empty or throws, so a filter matching the protected
   account **plus others** can slip past and `super.delete` then removes them all.
2. **`==` between a `Long` and a `long`.** `getId()` is `Long`, `getProtectedAccountId()`
   is primitive `long`, so the `Long` is **unboxed** and the comparison is numeric — which
   happens to be correct. It is a latent NPE if `getId()` were ever null (it cannot be for
   a persisted entity) and it reads like reference comparison. Do not "fix" it to
   `equals`: on a `Long`/`long` pair that would box the primitive and still compare by
   value, so the behaviour is identical and the diff is noise.
3. **The violation is an unmapped runtime exception** (BR-USER-043).
   `AccountProtectionViolationException extends RuntimeException` and
   `ExceptionsControllerAdvice` has no handler for it, so the client gets **HTTP 500**, not
   403 or 409.

Also note the guard runs **before** `super.delete`, so a protected-account attempt that
matches nothing still reaches the `EntityNotFoundException(ITEM_NOT_FOUND)` path.

### 3.2 `UserRolesCrudServiceImpl`

```java
@Transactional
@Service
```

Single override:

```java
@Override
public Optional<UserRole> getExisting(UserRolePojo input) throws BadInputException {
    String name = input.getName();
    if (StringUtils.isBlank(name)) {
        throw new BadInputException("Invalid user role name");
    } else {
        return rolesRepository.findByName(name);
    }
}
```

Literal `"Invalid user role name"` (BR-USER-040) — **a different string** from the user
one. **No delete override**: there is no protection for roles, and deleting a role still
referenced by an account is blocked by the **database** FK, not by application logic
(BR-USER-047), surfacing as a `DataIntegrityViolationException` → 500.

### 3.3 Inherited behaviour from `CrudGenericService`

Class-level `@Transactional`, no attributes. Constants
`ITEM_NOT_FOUND = "Requested item(s) not found"` and
`ITEM_ALREADY_EXISTS = "The item already exists"`.

| Method | Behaviour | Rule |
|---|---|---|
| `create(M)` | `validateInputPojoBeforeCreation` → `convertToNewEntity` → `saveAndFlush` → `convertToPojo` | — |
| `validateInputPojoBeforeCreation` | `getExisting(input).isPresent()` → `EntityExistsException(ITEM_ALREADY_EXISTS)` → **409** | BR-USER-001, BR-USER-005 |
| `update(M)` | `UnsupportedOperationException("This method signature has been deprecated")` | — |
| `update(M, Long id)` | `convertToNewEntity(input)` → `setId(id)` → `saveAndFlush`. **Full overwrite — the password is rewritten from the body** | **BR-USER-046** |
| `update(M, Predicate)` | `count > 1` → `RuntimeException("Cannot update more than one item at a time")`; no match → `Optional.empty()` | — |
| `partialUpdate(Map, …)` | `findById`/`findOne` → `flushPartialChanges`; a `BadInputException` is **wrapped** as `RuntimeException(e)` → 500 | BR-USER-045 |
| `flushPartialChanges` | patch, then `if (existing.equals(updated)) return existing;` else `saveAndFlush` | — |
| `delete(Predicate)` | `count == 0` → `EntityNotFoundException(ITEM_NOT_FOUND)`; else `deleteAll(findAll(filters))` | BR-USER-047, BR-USER-048 |
| `readOne(Predicate)` | empty → `EntityNotFoundException(ITEM_NOT_FOUND)` | — |

**BR-USER-046 is the highest-impact inherited behaviour in this domain.** Because
`UserPojo.password` is `@NotBlank`, a client *must* send a password on `PUT`; because
`update(M, Long)` builds a new entity, whatever is sent **replaces** the stored hash.
There is no "leave the password alone" path, and the patch service that would provide one
is unreachable (§5).

From `DataCrudGenericController` (shared kernel):

```java
public static final String NO_RECOGNIZED_FILTERING_PARAMS = "No recognized filtering parameters were provided";
```

- `update(M, Map)` / `partialUpdate(Map, Map)`: empty params →
  `BadInputException("Missing request params")`; then
  `.orElseThrow(() -> new EntityNotFoundException("No element was found to update"))`.
- `delete(Map)`: the **Phase 0 D1 guard** —
  `if (predicate instanceof BooleanBuilder && !((BooleanBuilder) predicate).hasValue())`
  → `BadInputException(NO_RECOGNIZED_FILTERING_PARAMS)`. **Empty params are a silent
  no-op**, which is a separate shape from "params present but all unrecognised".

---

## 4. Converters

### 4.1 `UsersConverterServiceImpl`

`@Transactional`, `@Service`. Injects `UserRolesRepository`, `PeopleConverterService`,
`PeopleRepository`, `PasswordEncoder`, plus a class `Logger`.

**`convertToPojo(User source)`:**
1. `UserPojo.builder().name(source.getName()).role(source.getUserRole().getName()).build()`
   — **`getUserRole()` is dereferenced with no null check** on a LAZY relation
   (`00` §2 W18). Safe only because callers fetched the role.
2. if `source.getPerson() != null` → `target.setPerson(peopleConverterService.convertToPojo(sourcePerson))`
   — the null check **is** present here, because `person` is genuinely optional
   (BR-USER-003).
3. **The password is never copied out.** Not nulled, not masked — simply never read. This
   is the only reason `GET /data/users` does not leak password hashes, and it is a
   one-line property worth protecting in review.

**`convertToNewEntity(UserPojo source) throws BadInputException`:**
1. `User.builder().name(source.getName()).build()`
2. role: blank → `BadInputException("The user was not given any role")`; else
   `rolesRepository.findByName(sourceRole)` — present → `logger.trace("User role found")`
   + `setUserRole`; absent → `BadInputException("The specified user role does not exist")`
3. person: if `sourcePerson != null && StringUtils.isNotBlank(sourcePerson.getIdNumber())`
   → `logger.trace("Finding person profile...")`, `peopleRepository.findByIdNumber(...)`,
   if present `logger.trace("Person profile found")` + `setPerson`.
   **If absent, the person is silently left null** — the account is created with no
   profile and no error. Fail-silent, preserved.
   **No check that the person is already claimed by another account** — this is where
   BR-USER-004 is *not* enforced (`00` §2 W1).
4. password: if `StringUtils.isNotBlank(source.getPassword())` →
   `target.setPassword(passwordEncoder.encode(rawPassword))` (BR-USER-013).
   **If blank, no password is set at all** — and since `user_password` is
   `nullable = false`, that surfaces as a constraint violation → 500, not a 400. Blank is
   already rejected by `@NotBlank` on the controller path, so this is reachable only
   through direct service use.

**`applyChangesToExistingEntity`** → `UnsupportedOperationException("This method is deprecated")`.

Three `logger.trace` literals port verbatim; they are the only trace-level logging in the
domain.

### 4.2 `UserRolesConverterServiceImpl`

`@Service`, `@NoArgsConstructor`. **No `@Transactional`** — nothing to transact.

```java
convertToPojo        → UserRolePojo.builder().name(source.getName()).build()
convertToNewEntity   → UserRole.builder().name(source.getName()).build()   // no `throws`
applyChangesToExistingEntity → throw new UnsupportedOperationException("This method is deprecated")
```

`convertToNewEntity` declares **no `throws BadInputException`**, unlike every other
converter in the codebase — the narrower signature is part of the compiled contract.

---

## 5. Patch services — both unreachable (BR-USER-044)

### 5.1 `UsersPatchServiceImpl`

`@Transactional`, `@Service`. Injects `UserRolesRepository` through a **plain constructor
with no `@Autowired`** (valid for a single constructor; port as-is).

```java
@Override
public User patchExistingEntity(Map<String, Object> changes, User existing) throws BadInputException {
    User target = new User(existing);
    // "name": cast to String; if not blank → target.setName(name)
    // "role": cast to String; if not blank → rolesRepository.findByName(roleName).ifPresent(target::setUserRole)
    return target;
}

@Override
public User patchExistingEntity(UserPojo changes, User existing) throws BadInputException {
    throw new UnsupportedOperationException("This method signature has been deprecated");
}
```

| Fact | Detail |
|---|---|
| **The password cannot be patched** | **BR-USER-045.** The copy constructor already nulled it (`01` §1.4), and no `"password"` key is handled. There is no password-change path anywhere in the system — register item **D15** |
| **An unknown role name is silently ignored** | `.ifPresent(...)` with no `orElseThrow`, so the role is simply left unchanged. Contrast the converter, which raises `BadInputException` for the same input |
| Casts are **unguarded** | `(String) changes.get("name")` — a non-string value is a `ClassCastException` → 500 |
| **Unreachable over HTTP** | no `@PatchMapping` on `DataUsersController` (BR-USER-044). Port regardless; the bean is live and tested |
| Literal | `"This method signature has been deprecated"` — **with** the word "signature" |

### 5.2 `UserRolesPatchServiceImpl`

`@Service`, `@NoArgsConstructor`, **no `@Transactional`**.

```java
patchExistingEntity(Map<String,Object>, UserRole) → new UserRole(existing); "name" non-blank → setName; return target
patchExistingEntity(UserRolePojo, UserRole)       → throw new UnsupportedOperationException("This method has been deprecated")
```

**The literal differs from §5.1 — no word "signature".** Both strings exist in this
domain; do not normalise them (G2).

---

## 6. Controllers

### 6.1 `DataUsersController`

```java
@RestController
@RequestMapping("/data/users")
@Tag(name = "Users management")
@PreAuthorize("isAuthenticated()")
public class DataUsersController extends DataCrudGenericController<UserPojo, User> {
```

| Verb | `@PreAuthorize` | Signature | Status | Throws |
|---|---|---|---|---|
| GET | `hasAuthority('" + USERS_READ + "')` | `DataPagePojo<UserPojo> readMany(@RequestParam Map<String,String> allRequestParams)` | 200 | — |
| POST | `hasAuthority('" + USERS_CREATE + "')` | `void create(@Valid @RequestBody UserPojo input)` → `crudService.create(input)` | **201** | `BadInputException, EntityExistsException` |
| PUT | `hasAuthority('" + USERS_UPDATE + "')` | `void update(@Valid @RequestBody UserPojo input, @RequestParam Map<String,String> requestParams)` | **204** | `BadInputException, EntityNotFoundException` |
| DELETE | `hasAuthority('" + USERS_DELETE + "')` | `void delete(Principal principal, @RequestParam Map<String,String> requestParams)` | **204** | `EntityNotFoundException, BadInputException` |

`@Operation(summary = ...)`: `"List users."`, `"Register new users."`,
`"Replace users data."`, `"Remove users."`.

**The `delete` method is NOT `@Override`** — it takes an extra `Principal` and adds the
self-delete guard:

```java
if (requestParams.containsKey("name") && requestParams.get("name").equals(principal.getName())) {
    throw new BadInputException("A user should not be able to delete their own account");
}
super.delete(requestParams);
```

**The guard only inspects the `name` parameter.** Deleting yourself by `id`, or by
`nameLike`, bypasses it entirely and falls through to the account-protection guard (§3.1)
— which has its own single-match weakness. Two independent guards, both narrow, neither
fixed (G1).

Sort hook → `UsersSortSpec.ORDER_SPEC_MAP`. **No PATCH mapping** (BR-USER-044).

### 6.2 `DataUserRolesController`

```java
@RestController
@RequestMapping("/data/user_roles")
@Tag(name = "Params management")
@PreAuthorize("isAuthenticated()")
```

**`@Tag` is `"Params management"`, not "User roles management"** — a mis-tagging that
groups this controller with the parameters API in the OpenAPI document. Client-visible in
generated docs and SDKs; port verbatim.

Four methods, all `@Override`, guarded by `USER_ROLES_READ` / `_CREATE` / `_UPDATE` /
`_DELETE`, with `@Operation` summaries `"List user roles."`,
`"Define new user roles."`, `"Replace user roles data."`, `"Remove user roles."`. Statuses
201 / 204 / 204 as usual. **No self-delete guard and no protection guard.**

Sort hook → **`UserRolesSortSpec.orderSpecMap`** — note the non-conforming field name
(BR-USER-054, `00` §2 W15).

### 6.3 `AccessController`

```java
@RestController
@RequestMapping("/access")
@Tag(name = "User Accounts")
@PreAuthorize("isAuthenticated()")
```

Injects `AuthorizationHeaderParserService<Claims>`, `UserDetailsService`,
`AuthorizedApiService`, `RegexMatcherAdapterService`.

| Verb | Path | Signature | Returns |
|---|---|---|---|
| GET | `/access` | `AuthorizedAccessPojo getApiRoutesAccess(@RequestHeader HttpHeaders requestHeaders) throws UsernameNotFoundException, IllegalStateException` | `.routes(...)` only |
| GET | `/access/{apiRoute}` | `AuthorizedAccessPojo getApiResourceAccess(@RequestHeader HttpHeaders requestHeaders, @PathVariable String apiRoute) throws IllegalStateException` | `.permissions(...)` only |

`@Operation` summaries: `"List authorized API routes"` and
`"List authorized access to API route"` — **no trailing full stop**, unlike the `/data/*`
controllers.

**No method-level `@PreAuthorize`** beyond the class-level `isAuthenticated()` — these
endpoints report what the caller may do, so any authenticated principal may ask about
itself.

**`private UserDetails getUserDetails(HttpHeaders)`:**
1. `extractAuthorizationHeader`; if null **or** `!regexMatcherService.isAValidAuthorizationHeader(header)` → `return null`
2. `String jwt = authorizationHeader.replace(JWT_PREFIX, "")` (BR-USER-021)
3. `Claims body = jwtClaimsParserService.parseToken(jwt)`; `username = body.getSubject()`
4. `return userDetailsService.loadUserByUsername(username)`

**A null `userDetails` makes the handler `return null`**, which Spring renders as an empty
200 body — **not** an error (BR-USER-035). So a missing or malformed header yields
`200` with no body, while an *untrusted* token yields 401 via the handler below.

```java
@ResponseStatus(UNAUTHORIZED)
@ExceptionHandler({UsernameNotFoundException.class, IllegalStateException.class})
public void handleException(Exception ex) { }
```

**An empty method body, deliberately** (BR-USER-034). The original comment is preserved
verbatim: *"bad credentials method. whatever provided data is in token, didn't match with
existing records of users. the consumer sent an invalid token. don't return an explanation
of this. the status code should suffice."* This is a 5+ line comment block and it is a
security decision — port it with the code.

**This controller re-parses the token itself** rather than reading the already-populated
`SecurityContext`. That is redundant work, but it is also why these endpoints behave
differently from everything else when the header is odd. Preserved.

### 6.4 `PublicRegisterController`

```java
@RestController
@RequestMapping("/public/register")
@Tag(name = "User Accounts")
public class PublicRegisterController { ... }
```

**No `@PreAuthorize` anywhere on this class** — fully anonymous, by design.

```java
@PostMapping
@Operation(summary = "Request creation of new user account.")
public void register(@Valid @RequestBody RegistrationPojo userProfile)
    throws BadInputException, EntityExistsException {
    this.registrationService.register(userProfile);
}
```

Default status **200**, not 201 — unlike every `/data/*` create. `@Valid` cascades into
`PersonPojo` via `RegistrationPojo.profile`'s `@Valid` (`01` §5.4).

`IdentityRouteParityTest` must assert that **this route is unsecured** — if a future change
accidentally added `isAuthenticated()`, self-registration would break silently for every
anonymous client.

### 6.5 `AccountProfileController`

```java
@RestController
@RequestMapping("/account/profile")
@Tag(name = "User Accounts")
@PreAuthorize("isAuthenticated()")
```

| Verb | Signature | Status |
|---|---|---|
| GET | `PersonPojo getProfile(Principal principal) throws EntityNotFoundException` | 200 |
| PUT | `void updateProfile(Principal principal, @RequestBody PersonPojo newProfile) throws EntityNotFoundException, BadInputException` | 200 |

`@Operation` summaries: `"View stored profile information"`,
`"Replace stored profile information"`.

Two load-bearing properties:

1. **`isAuthenticated()` only — no authority check at all** (BR-USER-050). Any
   authenticated principal may read and replace **their own** profile, and the identity is
   taken from `principal.getName()`, never from the request, so there is no horizontal
   privilege path. This is correct and must not be "tightened" by adding a `users:update`
   requirement, which would break self-service for ordinary customers.
2. **`@RequestBody` without `@Valid`** (BR-USER-051, `00` §2 W12). Profile replacement
   **skips bean validation entirely**, so blank names and malformed e-mail reach the
   service layer. The service's own guards catch some of it; the rest is written. Adding
   `@Valid` would start returning 400 where the baseline returns 200 — a behavioural
   change (G1).

**There is no `/account/password` route.** `IdentityRouteParityTest` must assert that none
is invented during the port — adding one would be new product behaviour (register item
**D15**).

---

## 7. Authority discovery — `AuthorizedApiServiceImpl`

`@Service`, no `@Transactional`, **throws nothing**.

```java
// getAuthorizedApiRoutes(UserDetails)
resourceName = resourceAuthority.replaceAll(":.+$", "");   // → HashSet<String>

// getAuthorizedApiRouteAccess(UserDetails, String apiRoute)
if (resourceAuthority.contains(apiRoute)) {
    actionName = resourceAuthority.replaceAll("^.+:", "");  // → HashSet<String>
}
```

**BR-USER-032** — route discovery strips everything from the colon onward.
**BR-USER-033** — action discovery matches by **`contains`**, not equality. So
`GET /access/product` returns the actions of `products:*` **and** `product_lists:*`
**and** `product_categories:*`.

**Revisited in Phase 2 as the register required, and the ruling is still PRESERVE.**
`AuthorizedApiServiceImplTest` asserts the current substring behaviour, so clients may
depend on it; the authority catalogue now makes an exact-match implementation cheap, but
making the change is a **client-visible contract change** (G1, G2) needing its own ruling
and release note. Recorded as a post-conversion backlog item with the one-line
implementation sketched.

Neither method reads the database — both work purely off the authorities already on the
`UserDetails`, which came from the token (BR-USER-022).

### 7.1 Predicate services

**`UsersPredicateServiceImpl`** (`@Service` only) — five recognised parameters:

| Parameter | Predicate |
|---|---|
| `id` | `basePath.id.eq(Long.valueOf(value))` — **early `return`** |
| `name` | `basePath.name.eq(value)` — **early `return`** |
| `email` | `basePath.person.email.eq(value)` — **traverses into the person** (BR-USER-053) |
| `nameLike` | `basePath.name.likeIgnoreCase("%"+v+"%")` |
| `emailLike` | `basePath.person.email.likeIgnoreCase("%"+v+"%")` |
| *anything else* | `default: break` — silently ignored |

**BR-USER-053 is a real information-flow note:** account filtering reaches the linked
person's e-mail, so `users:read` transitively exposes a person attribute. And because
`person` is an **inner** join in QueryDSL path navigation, filtering by `email` or
`emailLike` **silently excludes accounts with no profile**.

**`UserRolesPredicateServiceImpl`** — three parameters: `id` (early return), `name`
(early return), `nameLike`, plus `default: break`.

Both share the catch:

```java
catch (NumberFormatException exc) {
    logger.info("Param '{}' couldn't be parsed as number (value: '{}')", paramName, stringValue);
}
```

### 7.2 Sort specs

```java
// UsersSortSpec
public static final Map<String, OrderSpecifier<?>> ORDER_SPEC_MAP = Map.of(
    "name", BASE_PATH.name.asc(),
    "role", BASE_PATH.userRole.name.asc()
);

// UserRolesSortSpec  — note the field name
public static final Map<String, OrderSpecifier<?>> orderSpecMap = Map.of(
    "name", BASE_PATH.name.asc()
);
```

Both `@NoArgsConstructor(access = PRIVATE)`. The **field-name asymmetry is real**
(BR-USER-054) and is a public static field, i.e. API. These closed maps are the verified
mitigation for register item **D21** (QueryDSL CVE-2024-49203): `SortSpecParserService`
rejects any `sortBy` outside them, so `orderBy` never receives a raw client string.

---

## 8. Application services

### 8.1 `RegistrationServiceImpl` — and the Phase 2 deviation

Class: `@Service` only. Injects `PeopleRepository`, `UsersRepository`,
`UserRolesRepository`, **`CustomersRepository`**, `PasswordEncoder`,
`PeopleConverterService`.

```java
@Override
@Transactional(rollbackFor = {BadInputException.class, EntityExistsException.class})
public void register(RegistrationPojo registration) throws BadInputException, EntityExistsException {
```

**The `@Transactional` is the Phase 2 change** — a deliberate, recorded deviation from
**BR-USER-012** ("registration is not transactional"), per ADR-001 §Decision.1 and exit
criterion **X8**. Before it, a failure after the first `saveAndFlush` left an orphaned
`people` row that **no code path in this system can delete** (BR-PERSON-013,
BR-USER-012).

Step sequence:
1. `username = registration.getName()`; `Predicate userWithSameName = QUser.user.name.eq(username)`
2. `if (usersRepository.exists(userWithSameName)) throw new EntityExistsException("That username is taken.");` — **BR-USER-009**, note the trailing full stop
3. `Person newPerson = peopleConverterService.convertToNewEntity(registration.getProfile());`
4. `Predicate sameProfileData = QPerson.person.idNumber.eq(sourcePerson.getIdNumber());`
   `if (peopleRepository.exists(sameProfileData)) throw new EntityExistsException("That ID number is already registered and associated to an account.");`
   **else** `newPerson = peopleRepository.saveAndFlush(newPerson);` — **BR-USER-010**
5. `User newUser = this.convertToUser(registration); newUser.setPerson(newPerson); usersRepository.saveAndFlush(newUser);`
6. `logger.info("New user created with name '{}' and idNumber '{}'", newUser.getName(), newPerson.getIdNumber());`
7. `Customer newCustomer = Customer.builder().person(newPerson).build(); customersRepository.saveAndFlush(newCustomer);`

```java
protected User convertToUser(RegistrationPojo registration) {
    String password = passwordEncoder.encode(registration.getPassword());
    User target = User.builder().name(...).password(password).build();
    Optional<UserRole> customerRole = rolesRepository.findByName("Customer");
    if (customerRole.isEmpty()) {
        throw new IllegalStateException("No user role matches 'Customer', database might be compromised");
    }
    target.setUserRole(customerRole.get());
    return target;
}
```

| Fact | Detail |
|---|---|
| **Three writes, now one transaction** | `people`, `app_users`, `customers`. Co-location in Identity is what makes this possible |
| **`"Customer"` is hardcoded** | **BR-USER-011**; a missing role is `IllegalStateException("No user role matches 'Customer', database might be compromised")` → 500 |
| Duplicate checks run **before** any write | both throw `EntityExistsException` → 409, and both literals port verbatim |
| **BR-USER-004 is not enforced here** | the idNumber check (step 4) rejects a duplicate *person*, which happens to cover the common case — but nothing checks whether an **existing** person is already claimed |
| `convertToUser` is `protected`, not private | it is part of the class's extensible surface; keep the modifier |
| The method is the **only** writer of `customers` outside the customer domain | the `CustomersRepository` injection is intra-service after Phase 2 and is deliberate |

### 8.2 `ProfileServiceImpl`

`@Service`; **no class-level `@Transactional`**. Injects `UsersRepository`,
`PeopleCrudService`, `PeopleConverterService`, `PeoplePatchService`, `PeopleRepository`.

**`getProfileFromUserName(String)` — no `@Transactional`:**
1. `usersRepository.findByName(userName)`; empty → `UserNotFoundException("There is no account with the specified username")`
2. `person = byName.get().getPerson()`; null → `PersonNotFoundException("The account does not have an associated profile")`
3. else `peopleConverter.convertToPojo(person)`

**Two distinct exceptions for two distinct conditions is BR-USER-052**, and it only exists
because an account may have no profile (BR-USER-003).

**`updateProfileForUserWithName(String, PersonPojo)` — `@Transactional` (method level, no
attributes):**
1. `targetUser = getUserFromName(userName)` (private; empty → `UserNotFoundException("There is no account with the specified username")`)
2. `target = targetUser.getPerson()`
3. **if `target == null`** (the account has no profile yet):
   - `peopleService.getExisting(profile)` — if **present**:
     `usersRepository.findByPersonIdNumber(person.getIdNumber())`; if that user exists →
     `BadInputException("Person profile is associated to another account. Cannot use it.")`
     — **this is the only enforcement of BR-USER-004 anywhere in the system**;
     else `targetUser.setPerson(person); usersRepository.saveAndFlush(targetUser);`
   - if **absent**: `newProfile = peopleConverter.convertToNewEntity(profile);`
     `newProfile = peopleRepository.saveAndFlush(newProfile);`
     `targetUser.setPerson(newProfile); usersRepository.saveAndFlush(targetUser);`
4. **else** (a profile exists): `target = peoplePatchService.patchExistingEntity(asChangesMap(profile), target); peopleRepository.saveAndFlush(target);`

```java
private static Map<String, Object> asChangesMap(PersonPojo profile)
// puts only non-null idNumber, firstName, lastName, email, phone1, phone2
```

**Step 4 and `asChangesMap` are the Phase 0 D6 fix.** Before it, this branch called the
`PersonPojo` overload of the patch service, which **always threw**
`UnsupportedOperationException`, so profile update **always failed with HTTP 500 for any
account that already had a profile**. The fix routes through the `Map` overload instead.
D6 is one of the four register items that intentionally changed observable behaviour — the
javadoc on this method records it, and it ports with the code.

Because only non-null entries are put, **a null field leaves the stored value unchanged**;
it does not clear it. Combined with the missing `@Valid` (§6.5), a profile `PUT` is
effectively a partial update despite being a `PUT`.

---

## 9. The security package

### 9.1 `SecurityConfig` — the filter chain

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
```

Injects `UserDetailsService`, `SecretKey`, `SecurityProperties`,
`AuthorizationHeaderParserService<Claims>`, **`GuestCustomerProvisioningService`**,
`ObjectMapper`; holds a mutable `private AuthenticationManager authenticationManager;`

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
    return httpSecurity
        .authenticationManager(this.authenticationManager())
        .headers(configure -> configure.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(configure -> configure.sessionCreationPolicy(STATELESS))
        .addFilter(this.loginFilterForUrl("/public/login"))
        .addFilterAfter(this.guestFilterForUrl("/public/guest"),
                        JwtLoginAuthenticationFilter.class)
        .addFilterAfter(new JwtTokenVerifierFilter(jwtClaimsParserService),
                        JwtGuestAuthenticationFilter.class)
        .build();
}
```

**Wiring order, which is behaviour and must be reproduced exactly:**
login filter (at the `UsernamePasswordAuthenticationFilter` position, URL
`/public/login`) → guest filter (`/public/guest`) **after** it →
`JwtTokenVerifierFilter` **after** the guest filter.
`JwtGuestAuthenticationFilterOrderingTest` (4 tests) pins this.

**There are no `authorizeHttpRequests` / `antMatchers` rules at all** — **BR-USER-025**,
the Gate D finding (`00` §2 W2). Every authorization decision is a method-level
`@PreAuthorize`, which means **an endpoint without an annotation is public**. `ADR-002`
records the resulting decision: shared signing key, local verification per service,
method-level enforcement retained, gateway for routing and TLS only.

Other members:

| Member | Definition |
|---|---|
| `passwordEncoder()` | `new BCryptPasswordEncoder(securityProperties.getBcryptEncoderStrength())` (BR-USER-013) |
| `authenticationManager()` | lazily `new ProviderManager(this.daoAuthenticationProvider())` |
| `daoAuthenticationProvider()` | `new DaoAuthenticationProvider(this.passwordEncoder())` + `setUserDetailsService(userDetailsService)` |
| `loginFilterForUrl(String)` | `new JwtLoginAuthenticationFilter(securityProperties, secretKey, authenticationManager)` + `setFilterProcessesUrl(url)` |
| `guestFilterForUrl(String)` | `new JwtGuestAuthenticationFilter(securityProperties, secretKey, authenticationManager, guestCustomerProvisioningService, objectMapper)` + `setFilterProcessesUrl(url)` |

**`STATELESS` + CSRF disabled is BR-USER-024.** Both are correct for a token API and both
must port — re-enabling CSRF would break every client.

**Identity's `SecurityConfig` is the mirror image of Catalogue's.** Catalogue's is
verify-only and its javadoc explicitly enumerates what belongs here: the login filter and
`/public/login`, the guest filter and `/public/guest`, `UserDetailsService`,
`DaoAuthenticationProvider`, `AuthenticationManager`, and the bcrypt `PasswordEncoder`.
Identity is the **only** service that issues tokens.

### 9.1.1 `GenericJwtAuthenticationFilter` — token issuance

Abstract, `extends UsernamePasswordAuthenticationFilter`. Javadoc: *"Abstract filter that
writes a Bearer token to the response upon a succesful authentication call"* — **note the
spelling of "succesful"; preserve it.** Injects `SecurityProperties`, `SecretKey`.

```java
Instant expiration = now.plus(Period.ofDays(daysToExpire))
    .plus(Duration.ofHours(hoursToExpire))
    .plus(Duration.ofMinutes(minutesToExpire));
String token = Jwts.builder()
    .setSubject(authResult.getName())
    .claim(JWT_CLAIM_AUTHORITIES, authResult.getAuthorities())
    .setIssuedAt(Date.from(now))
    .setExpiration(Date.from(expiration))
    .signWith(secretKey)
    .compact();
String headerValue = JWT_PREFIX + token;
response.addHeader(HttpHeaders.AUTHORIZATION, headerValue);
response.getWriter().write(headerValue);
```

| Fact | Rule |
|---|---|
| Claims are exactly `sub`, `authorities`, `iat`, `exp` | BR-USER-022 |
| **Lifetime is the sum of all three components**, not a precedence | BR-USER-017 |
| **The token is written to both the `Authorization` header and the response body** | **BR-USER-016** |
| `chain` is **not** continued | the response ends here |

BR-USER-016 means the token appears in the body of a 200 response, where it is more likely
to be logged by proxies or cached by clients than a header. It is **contract** — clients
read it from the body — so it ports verbatim.

### 9.2 `JwtLoginAuthenticationFilter` — `/public/login`

`extends GenericJwtAuthenticationFilter`. `attemptAuthentication`:
1. `if (!HttpMethod.POST.matches(request.getMethod())) return null;` — **BR-USER-015**
2. read `LoginPojo` with **a new `ObjectMapper()`** from `request.getInputStream()`
3. `new UsernamePasswordAuthenticationToken(userData.getName(), userData.getPassword())`
4. `authenticationManager.authenticate(...)`
5. `catch (IOException e)` → `throw new BadCredentialsException("Request body is not a login request")`

**Login is a filter, not a controller route** (BR-USER-014). Consequences: no bean
validation of `LoginPojo` (`01` §5.3), no `@Operation` in the OpenAPI document, and errors
bypass `ExceptionsControllerAdvice`.

**`new ObjectMapper()` per request** (`00` §2 W13) — the guest filter was changed to inject
the Spring bean in Phase 0, this one was not, because it was outside D14's scope.
Asymmetry preserved.

### 9.3 `JwtGuestAuthenticationFilter` — `/public/guest`

`extends GenericJwtAuthenticationFilter`; injects `AuthenticationManager`,
`GuestCustomerProvisioningService`, `SecurityProperties`, and the **Spring**
`ObjectMapper`.

```java
// non-POST → return null
PersonPojo guestCustomerData = objectMapper.readValue(request.getInputStream(), PersonPojo.class);
final String credential = securityProperties.getGuestUserName();
Authentication authentication = new UsernamePasswordAuthenticationToken(credential, credential);

// Register item D14: authenticate BEFORE provisioning. This filter used to persist a
// real person and customer row first, so a failed guest login still wrote business
// data. The write itself now belongs to a transactional application service.
Authentication result = authenticationManager.authenticate(authentication);
guestCustomerProvisioningService.provisionCustomer(guestCustomerData);
return result;
```

Catches: `IOException` → `BadCredentialsException("Invalid request body for guest session")`;
`BadInputException` → `BadCredentialsException("Insufficient or invalid profile data for guest")`.

| Fact | Rule / ruling |
|---|---|
| **`credential` is used as both username and password** | **BR-USER-026**. D14 remainder (a), ruled **PRESERVE** — the guest name *is* the stored credential, and it is seeded. A credential/product decision, not conversion work |
| Guest access is **disabled by default** | BR-USER-027 |
| A guest session creates a real `person` + `customer` | BR-USER-028 |
| A repeat guest is tolerated, not rejected | BR-USER-029 — `EntityExistsException` is swallowed by the provisioning service |
| **Authenticate first, provision second** | the **D14 Phase 0 fix**. The inline comment is part of the record; port it |
| **No `@Validated` on the guest profile** | D14 remainder (b), ruled **PRESERVE**: `@Validated` would raise `ConstraintViolationException`, which this filter does **not** catch, turning a rejected guest profile from **401 into 500** (G1) |

`/public/guest` must be ported **as a filter**, as the definition requires — not converted
into a controller route.

### 9.4 `JwtTokenVerifierFilter` — verification

`extends OncePerRequestFilter`; injects `AuthorizationHeaderParserService<Claims>`.

```java
private Set<SimpleGrantedAuthority> extractAuthorities(Claims tokenBody) {
    // casts tokenBody.get(JWT_CLAIM_AUTHORITIES) to List<Map<String,String>>
    // → new SimpleGrantedAuthority(authorityKeyValuePair.get("authority")) into a HashSet<>
}
```

**The claim is a list of objects each carrying an `"authority"` key**, because that is how
Jackson serialised `authResult.getAuthorities()` at issuance. The `"authority"` string is
**wire contract** between issuer and verifier and must be identical in every service.

`doFilterInternal`:
1. blank `Authorization` header → **pass through the chain** (unauthenticated, not
   rejected) — this is why an un-annotated endpoint is public (BR-USER-025)
2. `jwt = authorizationHeader.replace(JWT_PREFIX, "")` (BR-USER-021)
3. `parseToken`; `if (expiration.isAfter(now))` →
   `SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null, authorities))`
4. `filterChain.doFilter(...)` **regardless** — an expired token means anonymous, **not a
   failure** (BR-USER-019, `00` §2 W4)

```java
catch (NullPointerException | IllegalStateException exc) {
    // Register item D2: the token value itself used to be logged here, leaking a live
    // credential into the application log. The request is still logged, the token is not.
    myLogger.info("Access denied: '{}' '{}' used an invalid token", request.getMethod(), request.getRequestURI());
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
}
```

**This is the Phase 0 D2 fix** — the full token value used to be written to the log. The
comment and the log literal both port. **No body is written** (BR-USER-020): the 401
status is the whole response.

Catching `NullPointerException` as a control-flow mechanism is deliberate in the original
and preserved.

### 9.5 `UserDetailsServiceImpl`

`@Service`; javadoc *"Service required by the DaoAuthenticationProvider bean."* **No
`@Transactional`.** Injects `UsersRepository`, `UserRolePermissionsRepository`,
`UserPermissionsService`, `SecurityProperties`.

```java
@Override
public UserDetailsPojo loadUserByUsername(String username) throws UsernameNotFoundException {
    if (securityProperties.isGuestUserEnabled() && username.equals(securityProperties.getGuestUserName())) {
        return loadGuestUserDetails();
    }
    return usersRepository.findByNameWithRole(username)
        .map(this::loadRegularUserDetails)
        .orElseThrow(() -> new UsernameNotFoundException(username));
}
```

**The guest check comes first**, so a real account whose name equals `guestUserName` is
shadowed by the guest path. **`UsernameNotFoundException`'s message is the raw username** —
it is echoed into no response body (§9.4, §6.3), so it does not leak, but it does reach
the log.

| Path | Authorities from | De-duplicated? |
|---|---|---|
| `loadRegularUserDetails(User)` | `userPermissionsService.loadPermissionsForUser(user)` → `new SimpleGrantedAuthority(code)` → `Collectors.toList()` | **yes**, upstream in a `HashSet<Permission>` (§9.6) |
| `loadGuestUserDetails()` | `rolePermissionsRepository.deepFindPermissionsByUserRoleId(guestUserRoleId)` → `map(rp -> new SimpleGrantedAuthority(rp.getPermission().getCode()))` → `toList()` | **no** — a plain list, no `HashSet` |

The guest path reads `securityProperties.getGuestUserRoleId()` (BR-USER-030) and sets
**username and password both** to `getGuestUserName()` (BR-USER-026).

**Both paths set all four status flags to literal `true`** — `accountNonExpired(true)`,
`accountNonLocked(true)`, `credentialsNonExpired(true)`, `enabled(true)`. That is
**BR-USER-023** and register item **D15**: there is no column to record account status and
nothing would read it. Ruled PRESERVE, out of scope, first post-conversion backlog item.

### 9.6 `UserPermissionsServiceImpl`

`@Service`, javadoc *"Service required by the DaoAuthenticationProvider bean."* No
`@Transactional`.

```java
@Override
public Set<Permission> loadPermissionsForUser(User source) {
    // source.getUserRole() → userRoleId
    // → userRolePermissionsRepository.deepFindPermissionsByUserRoleId(userRoleId)
    // → loop adding rolePermission.getPermission() into Set<Permission> targetList = new HashSet<>()
}
```

**The `HashSet<Permission>` is the in-memory de-duplication** that BR-USER-007 describes.
Before the Phase 0 **D17** fix, `app_user_role_permissions` had no unique constraint, so
the same authority could be granted twice and the duplicate was removed here. **D17 added
`UQ_UROLEPERMS_UROLE_PERM` and this de-duplication was deliberately left in place** —
removing it is unnecessary behavioural risk. Both port.

It works because `Permission`'s Lombok `@EqualsAndHashCode` covers all three fields
including `id` (`01` §3), so two rows of the same permission are the same object by value.

**No `@Transactional` on this service or on `UserDetailsServiceImpl`**, yet both
dereference LAZY relations. Safe only because `deepFindPermissionsByUserRoleId`
`JOIN FETCH`es the permission and `findByNameWithRole` `JOIN FETCH`es the role. Remove
either fetch and login breaks with a `LazyInitializationException`.

### 9.7 `ClaimsAuthorizationHeaderParserServiceImpl`

`@Service`; injects `SecretKey` via `@Autowired`.

```java
// parseToken(String token) throws IllegalStateException
Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);  // → claimsJws.getPayload()
catch (JwtException e) → throw new IllegalStateException(String.format("Token %s can't be trusted", token));

// extractAuthorizationHeader(HttpHeaders) — @Nullable
return httpHeaders.containsKey(AUTHORIZATION) ? httpHeaders.getFirst(AUTHORIZATION) : null;
```

> **Security note worth carrying forward.** The `IllegalStateException` message
> **interpolates the whole token** via `String.format("Token %s can't be trusted", token)`.
> It is not currently written to a log or a response body — `JwtTokenVerifierFilter`
> catches it and logs only the method and URI (the D2 fix, §9.4), and `AccessController`'s
> handler discards it (§6.3) — so **no live credential leaks today**. But the message is
> constructed on every failure and would leak immediately if any future handler logged
> `exc.getMessage()`. It is the same defect class as D2, one layer deeper, and is recorded
> here rather than changed: altering the message is out of Phase 2 scope (G1), and the
> safe fix belongs with whatever change first wants to log it.

---

## 10. Test inventory (G8 — these port with the code)

25 test classes, **73 tests** at the Phase 0 baseline.

| Test class | Path under `src/test/java/org/trebol/` | Baseline |
|---|---|---|
| `AccessControllerTest` | `api/controllers/` | 3 |
| `AccountProfileControllerTest` | `api/controllers/` | 2 |
| `DataUsersControllerTest` | `api/controllers/` | 6 |
| `DataUserRolesControllerTest` | `api/controllers/` | 6 |
| `PublicRegisterControllerTest` | `api/controllers/` | 1 |
| `ProfileServiceImplTest` | `api/services/impl/` | 9 |
| `RegistrationServiceImplTest` | `api/services/impl/` | 5 |
| `RegistrationServiceImplTransactionalTest` | `api/services/impl/` | **1 (new in Phase 2)** |
| `NoAuthorityLiteralsArchitectureTest` | `architecture/` | 3 |
| `UserRolePermissionUniquenessTest` | `jpa/entities/` | 2 |
| `UsersConverterServiceImplTest` | `jpa/services/conversion/impl/` | 5 |
| `UserRolesConverterServiceImplTest` | `jpa/services/conversion/impl/` | 2 |
| `UsersCrudServiceImplTest` | `jpa/services/crud/impl/` | 2 |
| `UserRolesCrudServiceImplTest` | `jpa/services/crud/impl/` | 1 |
| `UsersPatchServiceImplTest` | `jpa/services/patch/impl/` | 5 |
| `UserRolesPatchServiceImplTest` | `jpa/services/patch/impl/` | 3 |
| `UsersPredicateServiceTest` | `jpa/services/predicates/impl/` | 1 |
| `UserRolesPredicateServiceTest` | `jpa/services/predicates/impl/` | 1 |
| `UserDetailsServiceImplTest` | `security/` | 2 |
| `JwtLoginAuthenticationFilterTest` | `security/` | 1 |
| `JwtGuestAuthenticationFilterTest` | `security/` | 1 |
| `JwtGuestAuthenticationFilterOrderingTest` | `security/` | **4** (pins the D14 ordering) |
| `JwtVerifiterFilterTest` | `security/` | 2 — **note the typo in the class name; preserve it** |
| `AuthorizedApiServiceImplTest` | `security/services/impl/` | 2 (pins the BR-USER-033 substring behaviour) |
| `ClaimsAuthorizationHeaderParserServiceImplTest` | `security/services/impl/` | 2 |
| `UserPermissionsServiceImplTest` | `security/services/impl/` | 1 |
| `AccountProtectionViolationExceptionTest` | `security/exceptions/` | present as a file, absent from the baseline listing |

Support classes that port with them: `security/SecurityTestingConfig.java`,
`testing/*TestHelper` fixtures and `TestConstants`.

**Staying in the monolith:** `security/authorities/AuthorityCatalogueGenerator.java` and
`security/authorities/AuthorityCatalogueIntegrityTest.java` (7 tests) — they live with
`00-seed-data.json`, which is their source of truth. Identity instead carries
`NoAuthorityLiteralsArchitectureTest` with its own count floor, plus the new
`IdentityRouteParityTest` and `IdentityApplicationTest`.

No test may be deleted or disabled (G3); a module that compiles with zero tests fails the
G8 check.
