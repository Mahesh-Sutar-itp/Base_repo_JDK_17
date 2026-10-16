# Identity & Access (`user`) — Domain Index

Domain slug: `user` · Complexity: **high** · 55 source files · 4 owned tables ·
4 entities · **13 HTTP endpoints + 2 filter-processed URLs** · 55 business rules

Manifest entry: `business-rules-extraction/domain-decomposition-overview/domain-manifest.json`
→ `domains[]` where `slug == "user"`.

Produced for **Phase 2 (Identity extraction)**. Only `domain-person` and `domain-image`
existed at porting depth; the definition requires each phase to produce the specs for the
domains it touches. This is the largest of the three Phase 2 domains and the one that owns
the whole authentication and authorization machinery.

---

## 1. Domain summary

**Purpose.** Accounts, roles, authority grants, authentication, token issuance, token
verification, authority discovery, self-registration and self-service profile management.
Everything that decides *who you are* and *what you may do*, for the entire system.

**Core invariants, as implemented.** Account names are unique (BR-USER-001); every account
has exactly one role (BR-USER-002); authority codes are unique (BR-USER-006); grants are
immutable once written (BR-USER-008); and the authority code format is `resource:action`
(BR-USER-031). An account may exist **without** a person profile (BR-USER-003), and a
person may be claimed by at most one account (BR-USER-004) — but see §2 W1: that last one
has **no database expression at all**.

**Boundaries.** This is the only Phase 2 domain with genuine outbound coupling, and it is
all into `customer` (§3.2) — which the approved grouping places in the same service, so it
becomes intra-service coupling the moment Identity exists.

**Key entities.** `User` → `app_users`; `UserRole` → `app_user_roles`;
`Permission` → `app_permissions`; `UserRolePermission` → `app_user_role_permissions`.

**Key services.** Four repositories, two CRUD services (one with an **account-protection
override on delete**), two converters, two patch services, two predicate services, two
sort specs, five controllers, three application services (`RegistrationService`,
`ProfileService`, `AuthorizedApiService`), the Spring `UserDetailsService` implementation,
and a **thirteen-class `security/` package** containing three filters plus an abstract
base.

**The authorization model in one sentence.** Authorities are **not** roles: a user has one
role, the role is granted a set of permissions, the permission **codes** become granted
authorities, and `@PreAuthorize("hasAuthority('...')")` matches those codes — nothing ever
checks a role name.

---

## 2. Boundary warnings — read before extracting

| # | Finding | Severity | Where |
|---|---|---|---|
| W1 | **`app_users.person_id` has no unique constraint, and `User.person` is `@ManyToOne`.** So BR-USER-004 — one person, at most one account — is enforced **only** by an application-level check in `ProfileServiceImpl`, with the literal `"Person profile is associated to another account. Cannot use it."` Nothing stops `POST /data/users` or `POST /public/register` from attaching a second account to the same person. The three identity tables express **three different** person-cardinality rules (`customers` unique + `@OneToOne`, `salespeople` non-unique + `@ManyToOne`, `app_users` non-unique + optional `@ManyToOne`). Harmonising them is a behavioural change in every direction. | **High** | BR-USER-003, BR-USER-004, `01` §1.3 |
| W2 | **`SecurityConfig` declares no `authorizeHttpRequests` rules whatsoever** (BR-USER-025). Enforcement is entirely method-level, so **any endpoint without an authority annotation is publicly reachable**. This is the Gate D finding: a gateway cannot derive authorization from paths. Recorded in `ADR-002`; the decision is shared signing key, local verification per service, method-level enforcement retained. | **High** | BR-USER-025, `02` §9 |
| W3 | **Authorities travel inside the token and are never re-checked against the database** (BR-USER-022). `JwtTokenVerifierFilter` reads the `authorities` claim and trusts it. A revoked grant stays effective until the token expires, and token lifetime is the **sum** of three configured components (BR-USER-017), which can be made arbitrarily long. | **High** | BR-USER-017, BR-USER-022 |
| W4 | **An expired token produces no authentication failure** — the filter simply does not populate the security context and **continues the chain** (BR-USER-019), so the request proceeds anonymously and fails later, if at all. Combined with W2, an expired token against an un-annotated endpoint succeeds. | **High** | BR-USER-019 |
| W5 | **`PUT /data/users` overwrites the password with whatever the body carries** (BR-USER-046), because `CrudGenericService.update(M, Long)` converts the input to a brand-new entity and sets the id. A read-modify-write that omits `password` writes a **null** password. The patch service that would avoid this is unreachable (W7). | **High** | BR-USER-046, `02` §3.3 |
| W6 | **The account-protection guard inspects only a single matched account** (BR-USER-042): it calls `findOne(filters)`, so a filter matching several users — including the protected one — passes the guard and deletes them all. And the violation is an **unmapped runtime exception** → HTTP 500, not 403 (BR-USER-043). | **High** | BR-USER-041, BR-USER-042, BR-USER-043 |
| W7 | **The account patch capability is dead code, and it deliberately excludes the password** (BR-USER-044, BR-USER-045). `UsersPatchServiceImpl` is a live bean, fully tested, with no route that can reach it. Port it regardless. | Medium | BR-USER-045, `02` §5 |
| W8 | **Guest credentials are `name == password`** (BR-USER-026) and the value is seeded configuration. Ruled **PRESERVE** in Phase 2 — it is a credential/product decision, not conversion work. | Medium | BR-USER-026, `02` §9.3 |
| W9 | **Authority discovery matches by substring, not equality** (BR-USER-033), so asking about `product` also returns `product_lists:*` and `product_categories:*` actions. Revisited in Phase 2 as promised and **still ruled PRESERVE** — `AuthorizedApiServiceImplTest` pins the behaviour, so clients may depend on it. | Medium | BR-USER-032, BR-USER-033, `02` §7 |
| W10 | **All four `UserDetails` account-status flags are hardcoded `true`** (BR-USER-023), and there is no password change or reset path anywhere (register item **D15**). An account cannot be locked, expired or disabled. Ruled PRESERVE, out of scope, first post-conversion Identity backlog item. | Medium | BR-USER-023 |
| W11 | **The bearer prefix is stripped by substring `replace`, not by `startsWith` + offset** (BR-USER-021), so the literal `"Bearer "` is removed from **anywhere** in the header value. | Low | BR-USER-021 |
| W12 | **`AccountProfileController.updateProfile` takes `@RequestBody` without `@Valid`** (BR-USER-051), so profile replacement skips bean validation entirely — unlike every `/data/*` write. | Medium | BR-USER-051, `02` §6.5 |
| W13 | **`JwtLoginAuthenticationFilter` constructs a new `ObjectMapper()` per request**, while `JwtGuestAuthenticationFilter` was changed in Phase 0 to inject the Spring bean. The asymmetry is real; the login filter was out of D14's scope. | Low | `02` §9.2 |
| W14 | **Four seeded authority codes are dead and several seeded read authorities are never enforced** (BR-USER-036, BR-USER-037). Grants are **seed data only** — there is no runtime API to grant or revoke (BR-USER-038). The catalogue still carries all 54, and must (G2, X6). | Low | BR-USER-036, BR-USER-037, BR-USER-038 |
| W15 | **`UserRolesSortSpec` names its map `orderSpecMap`**, while every other sort spec uses `ORDER_SPEC_MAP` (BR-USER-054). A public static field name is API; port the inconsistency verbatim. | Low | BR-USER-054 |
| W16 | **`UserRolesRepository.findByName` is annotated with a bare `@Query` carrying no value.** It works as a derived query, but the annotation is misleading. Port as-is. | Low | `02` §2.2 |
| W17 | **Bootstrap credentials are committed to source control** (BR-USER-055) — four seeded accounts whose password equals the username. This is precisely what invariant **G7** forbids replicating. **Phase 2 does not carry them into identity-service's committed `data.sql`**; see §7. | **High** | BR-USER-055, §7 |
| W18 | **A `User`'s `userRole` is dereferenced without a null check** in `UsersConverterServiceImpl.convertToPojo` (`source.getUserRole().getName()`), and the relation is LAZY. Safe only because every read path that reaches the converter uses `findByNameWithRole` or a live session. | Low | `02` §4.1 |

**Phase 0 fixes already landed and ported with the code:** D1 (delete-all guard), **D2**
(the token value is no longer logged), D6 (profile update no longer always fails), D7,
D9, **D14** (guest login authenticates before provisioning), **D17**
(`UQ_UROLEPERMS_UROLE_PERM`). Phase 2 adds **transactional registration**. No other
warning above is fixed during the port (G1).

---

## 3. File coverage

### 3.1 Entities, repositories, services, sort specs

| # | Source file | Role | Covered in |
|---|---|---|---|
| 1–4 | `jpa/entities/{User,UserRole,Permission,UserRolePermission}.java` | entities | `01` §1–4 — ✅ Full |
| 5–8 | `jpa/repositories/{Users,UserRoles,Permissions,UserRolePermissions}Repository.java` | repositories | `02` §2 — ✅ Full |
| 9–12 | `jpa/services/crud/{Users,UserRoles}CrudService.java` + `impl/` | CRUD | `02` §3 — ✅ Full |
| 13–16 | `jpa/services/conversion/{Users,UserRoles}ConverterService.java` + `impl/` | conversion | `02` §4 — ✅ Full |
| 17–20 | `jpa/services/patch/{Users,UserRoles}PatchService.java` + `impl/` | patch | `02` §5 — ✅ Full |
| 21–24 | `jpa/services/predicates/{Users,UserRoles}PredicateService.java` + `impl/` | predicates | `02` §7.1 — ✅ Full |
| 25–26 | `jpa/sortspecs/{Users,UserRoles}SortSpec.java` | helpers | `02` §7.2 — ✅ Full |
| 27 | `jpa/exceptions/UserNotFoundException.java` | exception | `errors.md` §1 — ✅ Full |

### 3.2 Transport models

| # | Source file | Covered in |
|---|---|---|
| 28 | `api/models/UserPojo.java` | `01` §5.1 |
| 29 | `api/models/UserRolePojo.java` | `01` §5.2 |
| 30 | `api/models/LoginPojo.java` | `01` §5.3 |
| 31 | `api/models/RegistrationPojo.java` | `01` §5.4 |
| 32 | `api/models/AuthorizedAccessPojo.java` | `01` §5.5 |
| 33 | `security/UserDetailsPojo.java` | `01` §5.6 |

**`AuthorizedApiPojo` and `SessionPojo` do not exist.** The real class is
`AuthorizedAccessPojo`. Any plan or inventory naming the other two is wrong.

### 3.3 Controllers and application services

| # | Source file | Role | Covered in |
|---|---|---|---|
| 34 | `api/controllers/DataUsersController.java` | controller | `02` §6.1 |
| 35 | `api/controllers/DataUserRolesController.java` | controller | `02` §6.2 |
| 36 | `api/controllers/AccessController.java` | controller | `02` §6.3 |
| 37 | `api/controllers/PublicRegisterController.java` | controller | `02` §6.4 |
| 38 | `api/controllers/AccountProfileController.java` | controller | `02` §6.5 |
| 39–40 | `api/services/RegistrationService.java` + `impl/` | service | `02` §8.1 |
| 41–42 | `api/services/ProfileService.java` + `impl/` | service | `02` §8.2 |

### 3.4 The `security/` package — all 13 classes

| # | Source file | Role | Covered in |
|---|---|---|---|
| 43 | `security/GenericJwtAuthenticationFilter.java` | abstract filter, **issues** tokens | `02` §9.1 |
| 44 | `security/JwtLoginAuthenticationFilter.java` | filter, `/public/login` | `02` §9.2 |
| 45 | `security/JwtGuestAuthenticationFilter.java` | filter, `/public/guest` | `02` §9.3 |
| 46 | `security/JwtTokenVerifierFilter.java` | filter, **verifies** tokens | `02` §9.4 |
| 47 | `security/UserDetailsServiceImpl.java` | Spring `UserDetailsService` | `02` §9.5 |
| 48 | `security/services/UserPermissionsService.java` + `impl/` | authority assembly | `02` §9.6 |
| 49 | `security/services/AuthorizedApiService.java` + `impl/` | authority discovery | `02` §7 |
| 50 | `security/services/AuthorizationHeaderParserService.java` + `impl/ClaimsAuthorizationHeaderParserServiceImpl.java` | token parsing | `02` §9.7 |
| 51 | `security/exceptions/AccountProtectionViolationException.java` | exception | `errors.md` §1 |

### 3.5 Configuration

| # | Source file | Role | Covered in |
|---|---|---|---|
| 52 | `config/SecurityConfig.java` | the filter chain, `AuthenticationManager`, `PasswordEncoder` | `02` §9 |
| 53 | `config/SecurityProperties.java` | `trebol.security.*`, `@Validated` | `01` §6 |
| 54 | `config/JwtKeyConfig.java` | the `SecretKey` bean | `01` §6.2 |
| 55 | `config/Constants.java` | `JWT_CLAIM_AUTHORITIES`, `JWT_PREFIX`, `PERSON_DATA_MAP_KEYS_PREFIX`, `AUTHORITY_CHECKOUT` | `01` §6.3 |

**Coverage: 55/55 (100%).** Paths relative to `src/main/java/org/trebol/`.

**`AuthorityCodes` is not in this list** because it is not in monolith source: it lives in
`trebol-services/shared-contracts/src/main/java/org/trebol/security/authorities/AuthorityCodes.java`
(`CATALOGUE_VERSION 1.0.0`, `CATALOGUE_SIZE 54`, checksum `c77f0fb…947c`) and is generated
by `src/test/java/org/trebol/security/authorities/AuthorityCatalogueGenerator.java` from
`business-rules-extraction/database-documentation/00-seed-data.json`. The generator and
`AuthorityCatalogueIntegrityTest` **stay in the monolith**, with the seed JSON that is
their source of truth.

### 3.6 Outbound coupling census

Every import block of all 55 files was examined for
`order|billing|shipper|product|image|customer|salesperson`. **Eight hits: four false
positives, four real.**

False positives — `com.querydsl.core.types.OrderSpecifier` in `UsersSortSpec`,
`UserRolesSortSpec`, `DataUsersController`, `DataUserRolesController`.

**Real cross-domain coupling — all four into `customer`:**

| File | Import | Shape |
|---|---|---|
| `api/services/impl/RegistrationServiceImpl.java:34` | `org.trebol.jpa.entities.Customer` | entity |
| `api/services/impl/RegistrationServiceImpl.java:40` | `org.trebol.jpa.repositories.CustomersRepository` | **direct repository injection** |
| `security/JwtGuestAuthenticationFilter.java:31` | `org.trebol.api.services.GuestCustomerProvisioningService` | service |
| `config/SecurityConfig.java:42` | `org.trebol.api.services.GuestCustomerProvisioningService` | service, for filter construction |

**All four are intra-service after Phase 2**, because `customer` lands in Identity with
`user`. The `CustomersRepository` injection in `RegistrationServiceImpl` is not a
violation to be cut — co-locating those writes is exactly what makes registration a single
transaction (ADR-001 §Decision.1, exit criterion X8).

Zero imports of `order`, `billing`, `shipper`, `product`, `image` or `salesperson`.

Additionally — and not matched by that pattern — the domain reaches the **person** domain
widely: `Person`, `PeopleRepository`, `PeopleConverterService`, `PeopleCrudService`,
`PeoplePatchService`, and `basePath.person.email` in `UsersPredicateServiceImpl`
(BR-USER-053). `person` is in Identity too.

### 3.7 Inbound coupling

**Every authenticated request in the system depends on this domain**, through
`JwtTokenVerifierFilter` and `@PreAuthorize`. That dependency is satisfied **without a
network call**: per `ADR-002`, each service verifies tokens locally with a shared signing
key. Catalogue already does exactly this — its `SecurityConfig` is verify-only and its
javadoc enumerates what belongs here (login filter, guest filter, `UserDetailsService`,
`DaoAuthenticationProvider`, `AuthenticationManager`, bcrypt `PasswordEncoder`).

Identity is the **only** service that issues tokens. No other service reads `app_users`.

### 3.8 Shared-kernel files read but not owned

`CrudGenericService`, `DataCrudGenericController` (and the Phase-0 D1 guard),
`DataGenericController`, `DataCrudController`, `DataController`, the five generic service
contracts, `jpa/Repository.java`, `jpa/DBEntity.java`, `PaginationServiceImpl`,
`SortSpecParserServiceImpl`, `BadInputException`, `common/Utils.java`,
`config/ApiProperties.java`, `ExceptionsControllerAdvice`, and `AuthorityCodes` from
shared-contracts.

---

## 4. Entities

| Entity | Table | Ownership | Columns | Relationships |
|---|---|---|---|---|
| `User` | `app_users` (index on `user_name`; `user_name` unique) | owned | 3 + 2 FKs | 2 — **LAZY** `@ManyToOne` to `Person` (**optional**), **LAZY** `@ManyToOne` to `UserRole` (`optional = false`); **no cascade on either** |
| `UserRole` | `app_user_roles` (index on `user_role_name`; unique) | owned | 2 | none |
| `Permission` | `app_permissions` (`permission_code` unique) | owned | 3 | none |
| `UserRolePermission` | `app_user_role_permissions` (**`UQ_UROLEPERMS_UROLE_PERM`**) | owned | 1 + 2 FKs | 2 — **LAZY** `@ManyToOne` to `Permission` and to `UserRole`, both `optional = false`, **`updatable = false`**, no cascade |

`serialVersionUID`: `User` **19L**, `UserRole` **20L**, `Permission` **8L**,
`UserRolePermission` **21L**. No inheritance, no enumeration, no state field, and **no
lifecycle callbacks on any of the four**.

`UQ_UROLEPERMS_UROLE_PERM` is the **Phase 0 D17 fix** and is the only unique constraint
declared via `@Table(uniqueConstraints = ...)` in the whole domain.

---

## 5. Spec inventory

| File | Provides |
|---|---|
| `00-domain-index.md` | this document — coverage of all 55 files, 18 boundary warnings, the coupling census, the Phase 2 checklist |
| `01-user-entity-and-transport.md` | behavioural spec: the four entities, the six transport models, and the three configuration classes |
| `02-user-services.md` | behavioural spec: repositories, both CRUD services, converters, patch, predicates, five controllers, registration, profile, authority discovery, and the entire security package — plus the G8 test inventory |
| `errors.md` | every error condition with its literal message; the deliberate non-errors |
| `permissions.md` | the eight user authorities, the grant matrix, the enforcement model and the Gate D consequence |

The authoritative rule set already exists at
`business-rules-extraction/detailed-domain-bre/domain-user/business-rules.md`
(title: *Business Rules — Identity & Access (`user`)*).

---

## 6. Rule traceability

All 55 `BR-USER-*` rules (001–055, contiguous) are referenced by id across this document,
`01`, `02`, `errors.md` and `permissions.md`. No rule is unreferenced, which is what keeps
exit criterion X4 verifiable after the port.

**Two rules are deliberately deviated from, both recorded:**

| Rule | Baseline | After Phase 2 | Authority |
|---|---|---|---|
| **BR-USER-012** "Registration is not transactional" | three writes, no transaction, orphan `people` rows possible | `@Transactional(rollbackFor = {BadInputException.class, EntityExistsException.class})`, proven by `RegistrationServiceImplTransactionalTest` | ADR-001 §Decision.1, exit criterion **X8** |
| **BR-USER-055** "Bootstrap credentials are committed to source control" | four seeded accounts, password == username | **not carried into identity-service's committed configuration** | invariant **G7**, exit criterion **X10** |

Both are improvements over the baseline, both are explicit, and neither is silent (G1).
The rule texts should be annotated by the owner to record the deviation.

---

## 7. Phase 2 porting checklist

| Item | Requirement |
|---|---|
| Classes | All 55 files, **identical fully-qualified names**, including the whole `security/` package and the three config classes. |
| Literals | All 20 message literals in `errors.md`, the two JWT constants (`"authorities"`, `"Bearer "`), `"person."`, the eight authority constants, the hardcoded role name `"Customer"`, the 5 + 3 filter parameter names, and the 2 + 1 sort keys — verbatim (G2). |
| Tables | `app_users`, `app_user_roles`, `app_permissions`, `app_user_role_permissions` move with DDL, column types and constraint names unchanged, **including `UQ_UROLEPERMS_UROLE_PERM`** (D17) and **excluding** any unique constraint on `app_users.person_id` (W1). All FKs point inside Identity. |
| Load order | `app_user_roles` and `app_permissions` **before** `app_users` and `app_user_role_permissions`; `people` before `app_users`. |
| Seed data | The 4 roles, 54 permissions and 100 grants, **with their identity-generator restart statements**. **No bootstrap account row is committed** (W17, G7): the four demonstration accounts are recreated in `src/test/resources` as a test fixture so every ported test keeps working, and the operator step for creating the first administrator from the environment is recorded in `PHASE-2-EXIT.md`. No new runtime code. |
| Configuration | Every sensitive value is `${ENV_VAR}` with **no default** — `TREBOL_JWT_SECRET_KEY`, the datasource triple, CORS origins. Identity actually **uses** `jwt-expiration*` and `bcrypt-encoder-strength`, so those carry their real values (G7, X10). |
| Tests | All 25 user-domain test classes (**73 tests**), the three `testing/*TestHelper` fixtures, `TestConstants` and `SecurityTestingConfig` (G8). `AuthorityCatalogueGenerator` and `AuthorityCatalogueIntegrityTest` **stay in the monolith**; Identity gets `NoAuthorityLiteralsArchitectureTest` with its own count floor, plus `IdentityRouteParityTest` and `IdentityApplicationTest`. |
| Behaviour | No warning in §2 is fixed during the port, except the two recorded deviations in §6 (G1). |
