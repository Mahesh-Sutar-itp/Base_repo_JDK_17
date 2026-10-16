# Identity & Access (`user`) — Entities, Transport Models and Configuration

Porting-depth behavioural specification. Companion to `00-domain-index.md` and
`02-user-services.md`. Paths relative to `src/main/java/org/trebol/`.

All four entities share the Lombok set
`@Builder @NoArgsConstructor @AllArgsConstructor @Getter @Setter @EqualsAndHashCode @ToString`,
implement `org.trebol.jpa.DBEntity` (`extends Serializable`, `Long getId()` /
`void setId(Long)`), use `jakarta.*` imports, and have **no lifecycle callbacks** —
no `@PrePersist`, no `@PreUpdate`, no `@Version`.

---

## 1. `jpa/entities/User.java` — `serialVersionUID = 19L`

### 1.1 Declaration

```java
@Entity
@Table(
    name = "app_users",
    indexes = {
        @Index(columnList = "user_name")
    })
```

**No `@Table(uniqueConstraints = ...)`.** Name uniqueness is declared on the column
instead (§1.2), and the person link has **no uniqueness declaration anywhere** (§1.3).

### 1.2 Columns

| Field | Annotations | Rule |
|---|---|---|
| `Long id` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "user_id", nullable = false)` | — |
| `String name` | `@Size(min = 1, max = 50) @Column(name = "user_name", nullable = false, unique = true)` | **BR-USER-001** |
| `String password` | `@Size(min = 1, max = 100) @Column(name = "user_password", nullable = false)` | BR-USER-013 |

`@Size(max = 100)` on `password` is sized for a **BCrypt hash**, not a plaintext password.
`BCryptPasswordEncoder` output is 60 characters, so the column has headroom; raising the
cost factor does not change the hash length.

### 1.3 Relationships — the W1 asymmetry in full

```java
@JoinColumn(name = "person_id", referencedColumnName = "person_id")
@ManyToOne(fetch = FetchType.LAZY)
private Person person;

@JoinColumn(name = "user_role_id", referencedColumnName = "user_role_id")
@ManyToOne(optional = false, fetch = FetchType.LAZY)
private UserRole userRole;
```

| Property | `person` | `userRole` |
|---|---|---|
| Cardinality | **`@ManyToOne`** | `@ManyToOne` |
| `optional` | **not declared → defaults to `true`** (BR-USER-003) | **`false`** (BR-USER-002) |
| `fetch` | **explicit LAZY** | **explicit LAZY** |
| `cascade` | **none** | **none** |
| Unique on the FK column | **NO** | n/a |

Four consequences that must survive the port:

1. **An account may exist with no person profile** (BR-USER-003). This is why
   `ProfileServiceImpl` has a distinct `PersonNotFoundException` path (BR-USER-052) and why
   register item D6 existed at all.
2. **BR-USER-004 — one person, at most one account — has no database expression.** Neither
   the entity nor the DDL constrains `person_id`. The only enforcement is an application
   check in `ProfileServiceImpl.updateProfileForUserWithName`, raising
   `BadInputException("Person profile is associated to another account. Cannot use it.")`.
   **That check runs only on the profile-update path** — `POST /data/users` and
   `POST /public/register` do not perform it. The rule is therefore partially enforced, and
   that is the current observable behaviour.
3. **No cascade on `person`** means deleting an account does **not** delete its person
   (BR-USER-048) — genuinely true here, unlike the `customer` and `salesperson` domains,
   where `cascade = ALL` contradicts the equivalent rule.
4. **Both relations are LAZY**, which is why `UsersRepository` carries four distinct
   `JOIN FETCH` queries (`02` §2.1) and why `UsersConverterServiceImpl.convertToPojo`'s
   unguarded `source.getUserRole().getName()` is safe only on paths that fetched the role
   (`00` §2 W18).

The three identity tables' person-cardinality rules, side by side — **all three
intentional, all three different**:

| Table | Association | `person_id` unique? | Rule |
|---|---|---|---|
| `customers` | `@OneToOne(optional = false)`, EAGER | **YES** — `UK_CUSTOM_PERSON_ID` | BR-CUSTOMER-001 |
| `salespeople` | `@ManyToOne(optional = false)`, EAGER | **NO** | BR-SALESPERSON-002 |
| `app_users` | `@ManyToOne`, **optional**, LAZY | **NO** | BR-USER-003, BR-USER-004 |

### 1.4 Copy constructor

```java
/**
 * Please note that this copy-constructor only preserves a User's relationship to a role
 *
 * @param source The original User
 */
public User(User source) {
    this.id = source.id;
    this.name = source.name;
    this.userRole = source.userRole;
    this.password = null;
    this.person = null;
}
```

- **`id` IS copied.**
- **`password` and `person` are deliberately nulled**, and the javadoc says so.

This is load-bearing, and it is the reason `UsersPatchServiceImpl` cannot touch the
password (BR-USER-045): the copy it patches has already lost it. It also means the patch
path **cannot** be naively redirected through `update(M, Long)` — that would write the
nulls. Lombok `@EqualsAndHashCode` covers all five fields, so
`CrudGenericService.flushPartialChanges` compares id, name, password, person and role.

---

## 2. `jpa/entities/UserRole.java` — `serialVersionUID = 20L`

```java
@Entity
@Table(
    name = "app_user_roles",
    indexes = {
        @Index(columnList = "user_role_name")
    })
```

| Field | Annotations | Rule |
|---|---|---|
| `Long id` | `@Id @GeneratedValue(IDENTITY) @Column(name = "user_role_id", nullable = false)` | — |
| `String name` | `@Size(min = 1, max = 50) @Column(name = "user_role_name", nullable = false, unique = true)` | **BR-USER-005** |

**No relationships at all** — the role does not hold a collection of permissions. The
grant is a separate entity (§4), which is why authority assembly needs an explicit query
(`02` §9.6). The copy constructor copies **both** fields.

`"Customer"` is a **hardcoded role name** in `RegistrationServiceImpl` (BR-USER-011); its
absence is an `IllegalStateException`. The four seeded roles are listed in
`permissions.md`.

---

## 3. `jpa/entities/Permission.java` — `serialVersionUID = 8L`

```java
@Entity
@Table(name = "app_permissions")
```

| Field | Annotations | Rule |
|---|---|---|
| `Long id` | `@Id @GeneratedValue(IDENTITY) @Column(name = "permission_id", nullable = false)` | — |
| `String code` | `@Size(min = 1, max = 25) @Column(name = "permission_code", nullable = false, unique = true)` | **BR-USER-006**, BR-USER-031 |
| `String description` | `@Size(max = 100) @Column(name = "permission_description")` | — |

Two things matter:

1. **`code` is the granted authority string.** It is what becomes a
   `SimpleGrantedAuthority` and what `hasAuthority('...')` matches. It follows the
   `resource:action` convention (BR-USER-031) and is **client-observable contract** (G2) —
   renaming one silently disables an access check, which is the entire reason Gate B
   exists.
2. **`@Size(max = 25)`** is a real ceiling on the catalogue. The longest seeded code,
   `product_categories:update` at 25 characters, is **exactly at the limit**. Any new
   authority code longer than 25 characters would be rejected. Worth knowing before anyone
   invents `product_categories:partial_update`.

`description` is nullable, unvalidated, and never read by any code path.

The copy constructor copies all three fields, and Lombok equality covers all three — which
is what makes `UserPermissionsServiceImpl`'s `HashSet<Permission>` de-duplicate correctly
(`02` §9.6).

---

## 4. `jpa/entities/UserRolePermission.java` — `serialVersionUID = 21L`

```java
@Entity
@Table(name = "app_user_role_permissions",
    uniqueConstraints = @UniqueConstraint(name = "UQ_UROLEPERMS_UROLE_PERM",
        columnNames = {"user_role_id", "permission_id"}))
```

**`UQ_UROLEPERMS_UROLE_PERM` is the Phase 0 D17 fix** and the only unique constraint
declared via `@Table(uniqueConstraints = ...)` in this domain. It must port with its
**name** unchanged. It is declared on the entity because the runtime schema is generated
from the annotations — `spring.jpa.hibernate.ddl-auto` is commented out and the embedded
datasource default applies — and mirrored into `schema-mariadb.sql` and the extracted
global schema for parity.

| Field | Annotations |
|---|---|
| `Long id` | `@Id @GeneratedValue(IDENTITY) @Column(name = "user_role_permission_id", nullable = false)` |
| `Permission permission` | `@JoinColumn(name = "permission_id", referencedColumnName = "permission_id", updatable = false) @ManyToOne(optional = false, fetch = FetchType.LAZY)` |
| `UserRole userRole` | `@JoinColumn(name = "user_role_id", referencedColumnName = "user_role_id", updatable = false) @ManyToOne(optional = false, fetch = FetchType.LAZY)` |

**`updatable = false` on both join columns is BR-USER-008** — a grant is immutable once
written. There is no API to change one; the only operations are insert and delete, and
**neither is exposed at runtime** (BR-USER-038 — grants are seed data only).

Copy constructor:

```java
/**
 * Please note: this copy-constructor does not include a UserRolePermission's relationships
 */
public UserRolePermission(UserRolePermission source) {
    this.id = source.id;
    this.permission = null;
    this.userRole = null;
}
```

`id` is copied, both relations are nulled, and the javadoc says so. There is no patch
service for this entity, so nothing calls it.

**BR-USER-007** — "the same authority can be granted to a role twice" — describes the
**pre-D17** state. After the fix the database rejects it. The in-memory de-duplication at
login was **deliberately left in place** (`02` §9.6): removing it is unnecessary risk.
Both the constraint and the de-duplication port.

---

## 5. Transport models

### 5.1 `api/models/UserPojo.java`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)
public class UserPojo {
    @NotBlank
    @JsonInclude
    private String name;
    @NotBlank
    private String password;
    private PersonPojo person;
    private String role;
}
```

| Fact | Consequence |
|---|---|
| **`@NotBlank` on `password`** | a `POST`/`PUT` body **must** carry a password. Combined with `update(M, Long)` being a full overwrite, this is BR-USER-046: `PUT` always rewrites the password |
| `@JsonInclude(NON_NULL)` at class level, plus a **redundant field-level `@JsonInclude`** on `name` | the field-level annotation re-states the default and changes nothing. Port it; it is harmless and removing it is a diff with no purpose |
| `role` is a **plain `String`**, not a `UserRolePojo` | the role is addressed by **name**, resolved by `UsersConverterServiceImpl` against `UserRolesRepository`; an unknown name is `BadInputException("The specified user role does not exist")` |
| `role` and `person` carry **no validation** | both optional at the bean-validation layer; `role` is then rejected by the converter if blank (`"The user was not given any role"`) |
| **No id field** | accounts are addressed by `name` or by filter map, never by `user_id` |
| The password is **never serialised out** | `convertToPojo` simply does not copy it (`02` §4.1) |

### 5.2 `api/models/UserRolePojo.java`

```java
@Data @Builder @JsonInclude
public class UserRolePojo {
    @NotNull
    private String name;
}
```

Three deviations from every other pojo in the codebase, all to be ported verbatim:

1. **No `@NoArgsConstructor` / `@AllArgsConstructor`.** Jackson deserialises it through the
   Lombok builder path; adding the constructors would be a behavioural change in edge
   cases.
2. **`@JsonInclude` with no argument** — the annotation default is `ALWAYS`, so this is the
   opposite policy from `UserPojo`'s `NON_NULL`. The two coexist deliberately.
3. **`@NotNull`, not `@NotBlank`.** An **empty string** passes bean validation here and is
   then rejected deeper, by `UserRolesCrudServiceImpl.getExisting` with
   `BadInputException("Invalid user role name")` (BR-USER-040). A different status path
   from the equivalent user case.

### 5.3 `api/models/LoginPojo.java`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginPojo {
    @NotBlank
    private String name;
    @NotBlank
    private String password;
}
```

**No `@JsonInclude`** — it is input-only and never serialised out.

**The `@NotBlank` annotations are never enforced.** This pojo is deserialised by
`JwtLoginAuthenticationFilter` with a hand-rolled `ObjectMapper`, **not** by a Spring MVC
`@Valid @RequestBody`, because login is a **filter**, not a controller route
(BR-USER-014). Blank credentials therefore reach `authenticationManager.authenticate(...)`
and fail as bad credentials — a 401, not a 400. Port the annotations anyway: they are
documentation of intent, and removing them would be a gratuitous diff.

### 5.4 `api/models/RegistrationPojo.java`

```java
@Data @Builder @JsonInclude
public class RegistrationPojo {
    @NotBlank
    private String name;
    @NotBlank
    private String password;
    @Valid
    private PersonPojo profile;
}
```

`@Valid` on `profile` **cascades** bean validation into `PersonPojo`, so all four of its
`@NotBlank` fields are enforced on registration. This one **is** enforced, because
`PublicRegisterController.register` takes `@Valid @RequestBody` — unlike the login filter
(§5.3) and unlike profile update (§5.5 note). Again `@JsonInclude` with no argument, i.e.
`ALWAYS`.

### 5.5 `api/models/AuthorizedAccessPojo.java`

```java
@Data @Builder @JsonInclude(NON_NULL)
public class AuthorizedAccessPojo {
    private Collection<String> routes;
    private Collection<String> permissions;
}
```

`NON_NULL` is **functional here, not cosmetic**: `AccessController` populates exactly one
of the two fields per endpoint — `routes` for `GET /access`, `permissions` for
`GET /access/{apiRoute}` — so the other is omitted from the response entirely. Switching
to `ALWAYS` would start emitting `"permissions": null` and change the response shape for
every client (G2).

The name is **`AuthorizedAccessPojo`**. There is no `AuthorizedApiPojo` and no
`SessionPojo`.

> **Note on BR-USER-051.** `AccountProfileController.updateProfile` declares
> `@RequestBody PersonPojo` **without** `@Valid`, so profile replacement skips bean
> validation even though `PersonPojo` carries four `@NotBlank` fields. That is the third
> place in this domain where declared validation does not run.

### 5.6 `security/UserDetailsPojo.java`

```java
@Data @Builder
public class UserDetailsPojo implements UserDetails {
    private static final long serialVersionUID = 1L;
    private final List<? extends GrantedAuthority> authorities;
    private final String username;
    private final String password;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final boolean enabled;
}
```

All fields `final`; built only through the Lombok builder. **`serialVersionUID = 1L`.**

**All four status flags are set to `true` at both construction sites** — regular and guest
(`02` §9.5). That is **BR-USER-023**: an account cannot be locked, expired or disabled,
because no column exists to record it and nothing would read it. Register item **D15**
rules this PRESERVE and out of scope, flagged as the first post-conversion Identity
backlog item. Note the flags are **not** hardcoded in this class — it faithfully reports
what it is given; the four literal `true`s live in `UserDetailsServiceImpl`.

---

## 6. Configuration

### 6.1 `config/SecurityProperties.java`

```java
@Data @Component @ConfigurationProperties(prefix = "trebol.security") @Validated
```

| Property | Constraint | Used for |
|---|---|---|
| `String jwtSecretKey` | **`@NotBlank`** | the signing key (§6.2) |
| `int jwtExpirationAfterMinutes` | `@PositiveOrZero` | token lifetime component |
| `int jwtExpirationAfterHours` | `@PositiveOrZero` | token lifetime component |
| `int jwtExpirationAfterDays` | `@PositiveOrZero` | token lifetime component |
| `int bcryptEncoderStrength` | **`@Min(6)`** | BCrypt cost (BR-USER-013) |
| `boolean guestUserEnabled` | — | BR-USER-027 |
| `String guestUserName` | — | guest username **and password** (BR-USER-026) |
| `boolean accountProtectionEnabled` | — | BR-USER-041 |
| `long protectedAccountId` | — | BR-USER-041 |
| `long guestUserRoleId` | — | BR-USER-030 |

**`@Validated` + `@NotBlank jwtSecretKey` is the mechanism that makes G7 safe.** With the
secret supplied as `${TREBOL_JWT_SECRET_KEY}` and **no default**, a missing environment
variable fails **fast at startup** with a binding error, rather than silently booting with
a placeholder key. That is why identity-service's `application.properties` must not carry
a fallback value, and why its test resources carry a throwaway key under a `TEST FIXTURE`
banner.

**Token lifetime is the sum of all three components** (BR-USER-017), not a precedence
choice — see `02` §9.1. All three being zero yields an instantly-expired token, which
`@PositiveOrZero` permits.

### 6.2 `config/JwtKeyConfig.java`

```java
@Bean
public SecretKey secretKey(SecurityProperties jwtProperties) {
    return Keys.hmacShaKeyFor(jwtProperties.getJwtSecretKey().getBytes());
}
```

**BR-USER-018** — the key is derived **directly** from the configured string, with no
KDF, no salt and `getBytes()` using the **platform default charset**. Two consequences:

1. `hmacShaKeyFor` requires at least 256 bits, so a secret shorter than 32 bytes throws at
   startup — another fail-fast that makes a missing env var loud.
2. The platform default charset means a non-ASCII secret could derive a different key on a
   differently-configured host. Port verbatim; pinning the charset would change the derived
   key and invalidate every issued token.

**This bean is the shared-signing-key decision from `ADR-002` made concrete**: every
service constructs the same `SecretKey` from the same configured secret and verifies
locally. Catalogue already does; Identity additionally **signs** with it.

### 6.3 `config/Constants.java`

```java
public static final String JWT_CLAIM_AUTHORITIES = "authorities";
public static final String JWT_PREFIX = "Bearer ";
public static final String PERSON_DATA_MAP_KEYS_PREFIX = "person.";
public static final String AUTHORITY_CHECKOUT = org.trebol.security.authorities.AuthorityCodes.CHECKOUT;
```

- **`"authorities"`** is the custom claim name and is **wire contract** between issuer and
  verifier. Changing it in one service and not another breaks every token.
- **`"Bearer "`** — note the **trailing space**. It is stripped with `replace`, not
  `startsWith` + offset (BR-USER-021, `00` §2 W11), so it is removed from anywhere in the
  header value.
- **`AUTHORITY_CHECKOUT` is a delegating alias** to the generated catalogue, kept so the
  single pre-Gate-B constant's name still resolves. It is the one authority that was
  already a constant before Gate B; the other 53 were duplicated literals.
