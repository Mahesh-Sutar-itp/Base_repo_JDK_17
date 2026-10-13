# Domain: Identity & Access (`user`)

## Responsibility

Owns everything about *who* a caller is and *what* they may do: login accounts,
the role catalogue, the authority (permission) catalogue, the role-to-authority
grants, self-registration, guest sessions, token issuance and verification,
authority resolution, the account-profile surface, the route-discovery surface
and the protected-account guard.

This is the system's security kernel expressed as a business domain. Every other
domain's access control is enforced by authority strings that originate here.

## Bounded context summary

Access-control context. The four owned entities form a single aggregate under one
invariant: **a caller's effective authorities are derived transactionally from
account → role → role-grant → authority**. Splitting them would mean a login
could observe a half-applied permission change, so they are merged into one
context despite being four tables.

## Owned data

| Table | Entity | Source file |
|---|---|---|
| `app_users` | `User` | `jpa/entities/User.java` |
| `app_user_roles` | `UserRole` | `jpa/entities/UserRole.java` |
| `app_permissions` | `Permission` | `jpa/entities/Permission.java` |
| `app_user_role_permissions` | `UserRolePermission` | `jpa/entities/UserRolePermission.java` |

### `User`

| Field | Column | Nullable | Unique | Constraint |
|---|---|---|---|---|
| `id` | `user_id` | no | — | — |
| `name` | `user_name` | no | yes | `@Size(min=1,max=50)`, indexed |
| `password` | `user_password` | no | — | `@Size(min=1,max=100)` |
| `person` | `person_id` | **yes** | — | lazy, optional |
| `userRole` | `user_role_id` | no | — | lazy, mandatory |

### `UserRole`

`name` → `user_role_name`, not null, unique, `@Size(min=1,max=50)`, indexed.

### `Permission`

`code` → `permission_code`, not null, unique, `@Size(min=1,max=25)`;
`description` → `permission_description`, nullable, `@Size(max=100)` — **every
seeded row leaves the description null**.

### `UserRolePermission`

Pure join entity: `permission_id` and `user_role_id`, both mandatory, lazy and
`updatable=false`. Carries its own surrogate key. Note the table has **no**
uniqueness constraint on the pair, so the same authority can be granted to the
same role twice; de-duplication happens in memory at login.

## Components

### REST surface
| Component | Route | Notes |
|---|---|---|
| `DataUsersController` | `/data/users` | read, create, replace, delete (no patch), `users:*` authorities |
| `DataUserRolesController` | `/data/user_roles` | read, create, replace, delete (no patch), `user_roles:*` authorities |
| `AccountProfileController` | `/account/profile` | read + replace own profile |
| `AccessController` | `/access`, `/access/{apiRoute}` | authority/route discovery |
| `PublicRegisterController` | `/public/register` | self-registration, public |

No controller exposes `app_permissions` or `app_user_role_permissions`: the
authority catalogue and the grants are seed data only.

### Services
`UsersCrudServiceImpl`, `UserRolesCrudServiceImpl`, `RegistrationServiceImpl`,
`ProfileServiceImpl`, `UserDetailsServiceImpl`, `UserPermissionsServiceImpl`,
`AuthorizedApiServiceImpl`, `ClaimsAuthorizationHeaderParserServiceImpl`, plus
the converter/patch/predicate/sort-spec components for users and roles.

### Security filters and configuration
`GenericJwtAuthenticationFilter` (abstract issuer),
`JwtLoginAuthenticationFilter` (`/public/login`),
`JwtGuestAuthenticationFilter` (`/public/guest`),
`JwtTokenVerifierFilter`, `UserDetailsPojo`, `SecurityConfig`, `JwtKeyConfig`,
`SecurityProperties`, `AccountProtectionViolationException`,
`UserNotFoundException`, `PersonNotFoundException`.

### Repositories
`UsersRepository` (five custom finders), `UserRolesRepository`,
`PermissionsRepository` (no custom method), `UserRolePermissionsRepository`
(`deepFindPermissionsByUserRoleId`).

## Configuration

| Key | Default | Effect |
|---|---|---|
| `trebol.security.jwt-secret-key` | placeholder string | HMAC signing key material |
| `trebol.security.jwt-expiration-after-minutes` | 30 | token lifetime component |
| `trebol.security.jwt-expiration-after-hours` | 0 | token lifetime component |
| `trebol.security.jwt-expiration-after-days` | 0 | token lifetime component |
| `trebol.security.bcrypt-encoder-strength` | 10 | password hashing cost |
| `trebol.security.guest-user-enabled` | false | enables the guest account |
| `trebol.security.guest-user-name` | `guest` | guest account name **and** password |
| `trebol.security.guest-user-role-id` | 4 | role whose grants the guest receives |
| `trebol.security.account-protection-enabled` | true | enables the undeletable-account guard |
| `trebol.security.protected-account-id` | 1 | the undeletable account |

## Dependencies

- **Depends on**: `person` (an account optionally points at a person; registration
  and profile editing create, attach and patch person rows), `customer`
  (registration creates a customer row; guest authentication creates one).
- **Depended on by**: every domain, indirectly — all authority checks resolve
  against this domain's catalogue and grants. No other domain holds a compile-time
  dependency on it except `customer`, which the guest filter calls.

## Shared entity status

`Person` (owned by `person`) is used here. `User`, `UserRole`, `Permission` and
`UserRolePermission` are **not** shared — no other domain references them.
See `cross-cutting-documentation/shared-entities.md`.
